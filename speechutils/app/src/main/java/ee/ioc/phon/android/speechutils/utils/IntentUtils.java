package ee.ioc.phon.android.speechutils.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.SparseIntArray;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.json.JSONException;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import ee.ioc.phon.android.speechutils.Extras;
import ee.ioc.phon.android.speechutils.Log;
import ee.ioc.phon.android.speechutils.R;
import ee.ioc.phon.android.speechutils.editor.Command;
import ee.ioc.phon.android.speechutils.editor.UtteranceRewriter;

public final class IntentUtils {

    private IntentUtils() {
    }

    /**
     * @return table that maps SpeechRecognizer error codes to RecognizerIntent error codes
     */
    public static SparseIntArray createErrorCodesServiceToIntent() {
        SparseIntArray errorCodes = new SparseIntArray();
        errorCodes.put(SpeechRecognizer.ERROR_AUDIO, RecognizerIntent.RESULT_AUDIO_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_CLIENT, RecognizerIntent.RESULT_CLIENT_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, RecognizerIntent.RESULT_CLIENT_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_NETWORK, RecognizerIntent.RESULT_NETWORK_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_NETWORK_TIMEOUT, RecognizerIntent.RESULT_NETWORK_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_NO_MATCH, RecognizerIntent.RESULT_NO_MATCH);
        errorCodes.put(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, RecognizerIntent.RESULT_SERVER_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_SERVER, RecognizerIntent.RESULT_SERVER_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, RecognizerIntent.RESULT_NO_MATCH);
        return errorCodes;
    }

    public static PendingIntent getPendingIntent(Bundle extras) {
        Parcelable extraResultsPendingIntentAsParceable = extras.getParcelable(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT);
        if (extraResultsPendingIntentAsParceable != null) {
            //PendingIntent.readPendingIntentOrNullFromParcel(mExtraResultsPendingIntent);
            if (extraResultsPendingIntentAsParceable instanceof PendingIntent) {
                return (PendingIntent) extraResultsPendingIntentAsParceable;
            }
        }
        return null;
    }

    public static Intent getAppIntent(Context c, String packageName) {
        PackageManager pm = c.getPackageManager();
        return pm.getLaunchIntentForPackage(packageName);
    }

    /**
     * Constructs a list of search intents.
     * The first one that can be handled by the device is launched.
     * In split-screen mode, launch the activity into the other screen. Test this by:
     * 1. Launch Kõnele, 2. Start split-screen, 3. Press Kõnele mic button and speak,
     * 4. The results should be loaded into the other window.
     *
     * @param activity activity
     * @param query    search query
     */
    public static void startActivitySearch(Activity activity, CharSequence query) {
        // TODO: how to pass the search query to ACTION_ASSIST
        // TODO: maybe use SearchManager instead
        //Intent intent0 = new Intent(Intent.ACTION_ASSIST);
        //intent0.putExtra(Intent.EXTRA_ASSIST_CONTEXT, new Bundle());
        //intent0.putExtra(SearchManager.QUERY, query);
        //intent0.putExtra(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, false);
        //intent0.putExtra(Intent.EXTRA_ASSIST_PACKAGE, context.getPackageName());
        startActivityIfAvailable(activity,
                getGoogleSearchIntent(query),
                getSearchIntent(Intent.ACTION_WEB_SEARCH, query),
                getSearchIntent(Intent.ACTION_SEARCH, query));
    }

    public static boolean startActivityIfAvailable(@NonNull Context context, Intent... intents) {
        PackageManager mgr = context.getPackageManager();
        try {
            for (Intent intent : intents) {
                if (isActivityAvailable(mgr, intent)) {
                    // TODO: is it sensible to always start activity for result,
                    // even if the activity is not designed to return a result
                    if (context instanceof Activity) {
                        context.startActivity(intent);
                    } else {
                        // Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | intent.getFlags());
                        context.startActivity(intent);
                    }
                    //activity.startActivityForResult(intent, 2);
                    return true;
                } else {
                    Log.i("startActivityIfAvailable: not available: " + intent);
                }
            }
            showMessage(context, R.string.errorFailedLaunchIntent);
        } catch (SecurityException e) {
            // This happens if the user constructs an intent for which we do not have a
            // permission, e.g. the CALL intent.
            Log.i("startActivityIfAvailable: " + e.getMessage());
            showMessage(context, e.getLocalizedMessage());
        }
        return false;
    }

    /**
     * Starts an activity from the given context and catches a possible security exception.
     */
    public static void startActivityWithCatch(@NonNull Context context, Intent intent) {
        try {
            if (context instanceof Activity) {
                context.startActivity(intent);
            } else {
                // Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | intent.getFlags());
                context.startActivity(intent);
            }
        } catch (SecurityException e) {
            // This happens if the user constructs an intent for which we do not have a
            // permission, e.g. the CALL intent,
            // or according to Google Play, com.vlingo.midas/.settings.SettingsScreen
            showMessage(context, e.getLocalizedMessage());
        }
    }

    public static String rewriteResultWithExtras(Context context, Bundle extras, String result) {
        String defaultResultUtterance = null;
        String defaultResultCommand = null;
        String defaultResultArg1 = null;
        if (extras.getBoolean(Extras.EXTRA_RESULT_LAUNCH_AS_ACTIVITY)) {
            defaultResultUtterance = "(.+)";
            defaultResultCommand = "activity";
            defaultResultArg1 = "$1";
        }
        String resultUtterance = extras.getString(Extras.EXTRA_RESULT_UTTERANCE, defaultResultUtterance);
        if (resultUtterance != null) {
            String resultReplacement = extras.getString(Extras.EXTRA_RESULT_REPLACEMENT, null);
            String resultCommand = extras.getString(Extras.EXTRA_RESULT_COMMAND, defaultResultCommand);
            String resultArg1 = extras.getString(Extras.EXTRA_RESULT_ARG1, defaultResultArg1);
            String resultArg2 = extras.getString(Extras.EXTRA_RESULT_ARG2, null);

            String[] resultArgs;

            if (resultArg1 == null) {
                resultArgs = null;
            } else if (resultArg2 == null) {
                resultArgs = new String[]{resultArg1};
            } else {
                resultArgs = new String[]{resultArg1, resultArg2};
            }

            List<Command> commands = new ArrayList<>();
            commands.add(new Command(resultUtterance, resultReplacement, resultCommand, resultArgs));
            result = launchIfIntent(context, new UtteranceRewriter(commands), result);
        }
        if (result != null) {
            String rewritesAsStr = extras.getString(Extras.EXTRA_RESULT_REWRITES_AS_STR, null);
            if (rewritesAsStr != null) {
                result = launchIfIntent(context, new UtteranceRewriter(rewritesAsStr), result);
            }
        }
        return result;
    }

    /**
     * Rewrites the text. If the result is a command then executes it (only "activity" is currently
     * supported). Otherwise returns the rewritten string.
     * Errors that occur during the execution of "activity" are communicated via toasts.
     * The possible errors are: syntax error in JSON, nobody responded to the intent, no permission to launch
     * the intent.
     */
    public static String launchIfIntent(Context context, Iterable<UtteranceRewriter> urs, String text) {
        String newText = text;
        for (UtteranceRewriter ur : urs) {
            // Skip null, i.e. a case where a rewrites name did not resolve to a table.
            if (ur == null) {
                continue;
            }
            UtteranceRewriter.Rewrite rewrite = ur.getRewrite(newText);
            if (rewrite.isCommand() && rewrite.mArgs != null && rewrite.mArgs.length > 0) {
                // Commands that interpret their 1st arg as an intent in JSON.
                // There can be other commands in the future.
                try {
                    Intent intent = JsonUtils.createIntent(rewrite.mArgs[0]);
                    switch (rewrite.mId) {
                        case "activity":
                            startActivityIfAvailable(context, intent);
                            break;
                        case "service":
                            // TODO
                            break;
                        case "broadcast":
                            // TODO
                            break;
                        default:
                            break;
                    }
                } catch (JSONException e) {
                    Log.i("launchIfIntent: JSON: " + e.getMessage());
                    showMessage(context, e.getLocalizedMessage());
                }
                return null;
            }
            newText = rewrite.mStr;
        }
        return newText;
    }

    public static String launchIfIntent(Context context, UtteranceRewriter ur, String text) {
        UtteranceRewriter.Rewrite rewrite = ur.getRewrite(text);
        if (rewrite.isCommand() && rewrite.mArgs != null && rewrite.mArgs.length > 0) {
            // Commands that interpret their 1st arg as an intent in JSON.
            // There can be other commands in the future.
            try {
                Intent intent = JsonUtils.createIntent(rewrite.mArgs[0]);
                switch (rewrite.mId) {
                    case "activity":
                        startActivityIfAvailable(context, intent);
                        break;
                    case "service":
                        // TODO
                        break;
                    case "broadcast":
                        // TODO
                        break;
                    default:
                        break;
                }
            } catch (JSONException e) {
                Log.i("launchIfIntent: JSON: " + e.getMessage());
                showMessage(context, e.getLocalizedMessage());
            }
            return null;
        }
        return rewrite.mStr;
    }

    /**
     * Checks whether a speech recognition service is available on the system. If this method
     * returns {@code false}, {@link SpeechRecognizer#createSpeechRecognizer(Context, ComponentName)}
     * will fail.
     * Similar to {@link SpeechRecognizer#isRecognitionAvailable(Context)} but supports
     * restricting the intent query by component name.
     * <p/>
     * TODO: propose to add this to SpeechRecognizer
     * TODO: clarify what does "will fail" mean
     *
     * @param context       with which {@code SpeechRecognizer} will be created
     * @param componentName of the recognition service
     * @return {@code true} if recognition is available, {@code false} otherwise
     */
    public static boolean isRecognitionAvailable(final Context context, ComponentName componentName) {
        Intent intent = new Intent(RecognitionService.SERVICE_INTERFACE);
        intent.setComponent(componentName);
        final List<ResolveInfo> list = context.getPackageManager().queryIntentServices(intent, 0);
        return list.size() != 0;
    }

    /**
     * Checks if there are any activities that can service this intent.
     * Note that we search for activities using the MATCH_DEFAULT_ONLY flag, but this
     * can also return a non-exported activity (for some reason).
     * This can only (?) happen if the intent references the activity by its class name and the
     * activity belongs to the app that calls this method.
     * We assume that activities are not exported for a reason, and thus will declare the
     * intent unserviceable if a non-exported activity matches the intent.
     *
     * @param mgr    PackageManager
     * @param intent Intent
     * @return true iff an exported activity exists to service this intent
     */
    private static boolean isActivityAvailable(PackageManager mgr, Intent intent) {
        List<ResolveInfo> list = mgr.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : list) {
            if (ri.activityInfo != null) {
                if (!ri.activityInfo.exported) {
                    Log.i("Query returned non-exported activity, declaring it unavailable.");
                    return false;
                }
            }
        }
        return list.size() > 0;
    }

    private static Intent getSearchIntent(String action, CharSequence query) {
        Intent intent = new Intent(action);
        intent.putExtra(SearchManager.QUERY, query);
        return intent;
    }

    private static Intent getGoogleSearchIntent(CharSequence query) {
        String escapedQuery;
        try {
            escapedQuery = URLEncoder.encode(query.toString(), "UTF-8");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        Uri uri = Uri.parse("https://www.google.com/search?q=" + escapedQuery);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        return intent;
    }

    private static void showMessage(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private static void showMessage(Context context, int message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}