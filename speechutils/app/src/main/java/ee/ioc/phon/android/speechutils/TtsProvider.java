package ee.ioc.phon.android.speechutils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * TODO: add the capability to aggregate different TTS engines with support different languages
 * getEngines() on API level 14
 */
public class TtsProvider {

    public interface Listener {
        void onDone();
    }

    private static final String UTT_COMPLETED_FEEDBACK = "UTT_COMPLETED_FEEDBACK";

    private final TextToSpeech mTts;

    public TtsProvider(Context context, TextToSpeech.OnInitListener listener) {
        // TODO: use the 3-arg constructor (API 14) that supports passing the engine.
        // Choose the engine that supports the selected language, if there are several
        // then let the user choose.
        mTts = new TextToSpeech(context, listener);
        Log.i("Default TTS engine:" + mTts.getDefaultEngine());
    }

    public int say(String text) {
        return say(text, null);
    }

    public int say(String text, final Listener listener) {
        mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override
            public void onDone(String utteranceId) {
                if (listener != null) listener.onDone();
            }

            @Override
            public void onError(String utteranceId) {
                if (listener != null) listener.onDone();
            }

            @Override
            public void onStart(String utteranceId) {
            }
        });

        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTT_COMPLETED_FEEDBACK);
        return mTts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
    }


    /**
     * Interrupts the current utterance and discards other utterances in the queue.
     *
     * @return {@ERROR} or {@SUCCESS}
     */
    public int stop() {
        // TODO: not sure which callbacks get called as a result of stop()
        return mTts.stop();
    }


    /**
     * TODO: is the language available on any engine (not just the default)
     */
    public boolean isLanguageAvailable(String localeAsStr) {
        return mTts.isLanguageAvailable(new Locale(localeAsStr)) >= 0;
    }


    /**
     * TODO: set the language, changing the engine if the default engine
     * does not support this language
     */
    public void setLanguage(Locale locale) {
        mTts.setLanguage(locale);
    }


    /**
     * TODO: add this logic to setLanguage and deprecate this method
     */
    public Locale chooseLanguage(String localeAsStr) {
        Locale locale = new Locale(localeAsStr);
        Log.i("Choosing TTS for: " + localeAsStr + " -> " + locale);
        // TODO: this can throw NPE in String.isEmpty in java.util.Locale.getISO3Language
        // if garbage is given as input
        if (mTts.isLanguageAvailable(locale) >= 0) {
            Log.i("Chose: " + locale);
            return locale;
        }
        List<Locale> similarLocales = TtsLocaleMapper.getSimilarLocales(locale);
        if (similarLocales != null) {
            for (Locale l : similarLocales) {
                if (mTts.isLanguageAvailable(l) >= 0) {
                    Log.i("Chose: " + l + " from " + similarLocales);
                    return l;
                }
            }
        }
        Log.i("Chose: " + "NULL from " + similarLocales);
        return null;
    }


    /**
     * Shuts down the TTS instance, resuming the audio if needed.
     */
    public void shutdown() {
        mTts.shutdown();
    }

}