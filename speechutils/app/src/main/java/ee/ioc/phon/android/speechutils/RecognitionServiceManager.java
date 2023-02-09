package ee.ioc.phon.android.speechutils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Pair;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RecognitionServiceManager {
    private static String SEPARATOR = ";";
    private Set<String> mInitiallySelectedCombos = new HashSet<>();
    private Set<String> mCombosExcluded = new HashSet<>();

    public interface Listener {
        void onComplete(List<String> combos, Set<String> selectedCombos);
    }

    /**
     * @return true iff a RecognitionService with the given component name is installed
     */
    public static boolean isRecognitionServiceInstalled(PackageManager pm, ComponentName componentName) {
        List<ResolveInfo> services = pm.queryIntentServices(
                new Intent(RecognitionService.SERVICE_INTERFACE), 0);
        for (ResolveInfo ri : services) {
            ServiceInfo si = ri.serviceInfo;
            if (si == null) {
                Log.i("serviceInfo == null");
                continue;
            }
            if (componentName.equals(new ComponentName(si.packageName, si.name))) {
                return true;
            }
        }
        return false;
    }

    /**
     * On LOLLIPOP we use a builtin to parse the locale string, and return
     * the name of the locale in the language of the current locale. In pre-LOLLIPOP we just return
     * the formal name (e.g. "et-ee"), because the Locale-constructor is not able to parse it.
     *
     * @param localeAsStr Formal name of the locale, e.g. "et-ee"
     * @return The name of the locale in the language of the current locale
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static String makeLangLabel(String localeAsStr) {
        // Just to make sure we do not get a NPE from Locale.forLanguageTag
        if (localeAsStr == null || localeAsStr.isEmpty()) {
            return "?";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Locale.forLanguageTag(localeAsStr).getDisplayName();
        }
        return localeAsStr;
    }

    public static String[] getServiceAndLang(String str) {
        return TextUtils.split(str, SEPARATOR);
    }

    public static String createComboString(String service, String locale) {
        return service + SEPARATOR + locale;
    }

    public static Pair<ComponentName, String> unflattenFromString(String comboId) {
        String serviceAsStr = "";
        String localeAsStr = "";
        String[] splits = getServiceAndLang(comboId);
        if (splits.length > 0) {
            serviceAsStr = splits[0];
            if (splits.length > 1) {
                localeAsStr = splits[1];
            }
        }
        return new Pair<>(ComponentName.unflattenFromString(serviceAsStr), localeAsStr);
    }

    /**
     * @param str string like {@code com.alexvt.whisperinput/.speak.OfflineRecognitionService}
     * @return ComponentName in the input string
     */
    public static ComponentName getComponentName(String str) {
        String[] splits = getServiceAndLang(str);
        return ComponentName.unflattenFromString(splits[0]);
    }

    public static String getServiceLabel(Context context, String service) {
        ComponentName recognizerComponentName = ComponentName.unflattenFromString(service);
        return getServiceLabel(context, recognizerComponentName);
    }

    public static String getServiceLabel(Context context, ComponentName recognizerComponentName) {
        try {
            PackageManager pm = context.getPackageManager();
            ServiceInfo si = pm.getServiceInfo(recognizerComponentName, 0);
            return si.loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            // ignored
        }
        return "[?]";
    }

    public static ServiceInfo getServiceInfo(Context context, ComponentName recognizerComponentName) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getServiceInfo(recognizerComponentName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            // ignored
        }
        return null;
    }

    public static String getSettingsActivity(Context context, ServiceInfo si)
            throws XmlPullParserException, IOException {
        PackageManager pm = context.getPackageManager();
        XmlResourceParser parser = null;
        try {
            parser = si.loadXmlMetaData(pm, RecognitionService.SERVICE_META_DATA);
            if (parser == null) {
                throw new XmlPullParserException("No " + RecognitionService.SERVICE_META_DATA + " meta-data");
            }

            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
            }

            String nodeName = parser.getName();
            if (!"recognition-service".equals(nodeName)) {
                throw new XmlPullParserException(
                        "Meta-data does not start with recognition-service tag");
            }

            return parser.getAttributeValue("http://schemas.android.com/apk/res/android",
                    "settingsActivity");
        } finally {
            if (parser != null) parser.close();
        }
    }

    public static Drawable getServiceIcon(Context context, ComponentName recognizerComponentName) {
        try {
            PackageManager pm = context.getPackageManager();
            ServiceInfo si = pm.getServiceInfo(recognizerComponentName, 0);
            return si.loadIcon(pm);
        } catch (PackageManager.NameNotFoundException e) {
            // ignored
        }
        return null;
    }

    public void setCombosExcluded(Set<String> set) {
        mCombosExcluded = set;
    }

    public void setInitiallySelectedCombos(Set<String> set) {
        if (set == null) {
            mInitiallySelectedCombos = new HashSet<>();

        } else {
            mInitiallySelectedCombos = set;
        }
    }

    /**
     * @return list of currently installed RecognitionService component names flattened to short strings
     */
    public List<String> getServices(PackageManager pm) {
        List<String> services = new ArrayList<>();
        int flags = 0;
        //int flags = PackageManager.GET_META_DATA;
        List<ResolveInfo> infos = pm.queryIntentServices(
                new Intent(RecognitionService.SERVICE_INTERFACE), flags);

        for (ResolveInfo ri : infos) {
            ServiceInfo si = ri.serviceInfo;
            if (si == null) {
                Log.i("serviceInfo == null");
                continue;
            }
            String pkg = si.packageName;
            String cls = si.name;
            // TODO: process si.metaData
            String component = (new ComponentName(pkg, cls)).flattenToShortString();
            if (!mCombosExcluded.contains(component)) {
                services.add(component);
            }
        }
        return services;
    }

    /**
     * Collect together the languages supported by the given services and call back once done.
     */
    public void populateCombos(Context activity, final Listener listener) {
        List<String> services = getServices(activity.getPackageManager());
        populateCombos(activity, services, listener);
    }

    public void populateCombos(Context activity, List<String> services, final Listener listener) {
        populateCombos(activity, services, 0, listener, new ArrayList<>(), new HashSet<>());
    }

    public void populateCombos(Context activity, String service, final Listener listener) {
        final List<String> services = new ArrayList<>();
        services.add(service);
        populateCombos(activity, services, listener);
    }

    private void populateCombos(final Context activity, final List<String> services, final int counter, final Listener listener,
                                final List<String> combos, final Set<String> selectedCombos) {

        if (services.size() == counter) {
            listener.onComplete(combos, selectedCombos);
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS);
        // TODO: this seems to be only for activities that implement ACTION_WEB_SEARCH
        //Intent intent = RecognizerIntent.getVoiceDetailsIntent(this);

        final String service = services.get(counter);
        ComponentName serviceComponent = ComponentName.unflattenFromString(service);
        if (serviceComponent != null) {
            intent.setPackage(serviceComponent.getPackageName());
            // TODO: ideally we would like to query the component, because the package might
            // contain services (= components) with different capabilities.
            //intent.setComponent(serviceComponent);
        }

        // This is needed to include newly installed apps or stopped apps
        // as receivers of the broadcast.
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        activity.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                ArrayList<CharSequence> langs = new ArrayList<>();

                if (getResultCode() == Activity.RESULT_OK) {
                    Bundle results = getResultExtras(true);

                    // Supported languages
                    String prefLang = results.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE);
                    ArrayList<CharSequence> allLangs = results.getCharSequenceArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES);

                    Log.i("Supported langs: " + prefLang + ": " + allLangs);
                    if (allLangs != null) {
                        langs.addAll(allLangs);
                    }
                    // We add the preferred language to the list of supported languages, if not already there.
                    if (prefLang != null && !langs.contains(prefLang)) {
                        langs.add(prefLang);
                    }
                }

                // Make sure that the list of languages contains at least one member,
                // "und", to be interpreted
                // as "some unspecified languages" or "all languages" (but not "no languages").
                // We use the code "und" here, see also:
                // - https://en.wikipedia.org/wiki/ISO_639-3
                // - https://android.googlesource.com/platform/libcore/+/refs/heads/master/ojluni/src/main/java/java/util/Locale.java
                //   Android-added: (internal only): ISO 639-3 generic code for undetermined languages.
                //   private static final String UNDETERMINED_LANGUAGE = "und";
                langs.add("und");

                for (CharSequence lang : langs) {
                    String combo = service + SEPARATOR + lang;
                    if (!mCombosExcluded.contains(combo)) {
                        Log.i(combos.size() + ") " + combo);
                        combos.add(combo);
                        if (mInitiallySelectedCombos.contains(combo)) {
                            selectedCombos.add(combo);
                        }
                    }
                }

                populateCombos(activity, services, counter + 1, listener, combos, selectedCombos);
            }
        }, null, Activity.RESULT_OK, null, null);
    }
}