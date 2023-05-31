/*
 * Copyright 2011-2017, Institute of Cybernetics at Tallinn University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.ioc.phon.android.speechutils;

/**
 * <p>EXTRAs for RecognizerIntent and SpeechRecognizer,
 * in addition to the standard Android EXTRAs defined as part of RecognizerIntent.</p>
 *
 * @author Kaarel Kaljurand
 */
public class Extras {

    /**
     * String (must be a legal URL).
     * URL of a speech recognition server.
     */
    public static final String EXTRA_SERVER_URL = "ee.ioc.phon.android.extra.SERVER_URL";

    /**
     * String.
     * Class name of the recognizer component name, e.g.
     * ee.ioc.phon.android.speak/.service.WebSocketRecognitionService
     */
    public static final String EXTRA_SERVICE_COMPONENT = "ee.ioc.phon.android.extra.SERVICE_COMPONENT";

    /**
     * String (must be a legal URL).
     * URL of a PGF or JSGF grammar.
     */
    public static final String EXTRA_GRAMMAR_URL = "ee.ioc.phon.android.extra.GRAMMAR_URL";

    /**
     * String.
     * Identifier of the target language in the given PGF grammar (e.g. "Est", "Eng").
     */
    public static final String EXTRA_GRAMMAR_TARGET_LANG = "ee.ioc.phon.android.extra.GRAMMAR_TARGET_LANG";

    /**
     * String.
     * Desired transcription.
     * Using this extra, the user can specify to which string the enclosed audio should be transcribed.
     */
    public static final String EXTRA_PHRASE = "ee.ioc.phon.android.extra.PHRASE";

    /**
     * String.
     * Optional text prompt to read out to the user when asking them to speak in the RecognizerIntent activity.
     * See also "android.speech.extra.PROMPT".
     */
    public static final String EXTRA_VOICE_PROMPT = "ee.ioc.phon.android.extra.VOICE_PROMPT";

    /**
     * Bundle.
     * Information about the editor in which the IME is running.
     */
    public static final String EXTRA_EDITOR_INFO = "ee.ioc.phon.android.extra.EDITOR_INFO";

    /**
     * Boolean.
     * True iff the recognition service should not stop after delivering the first result.
     */
    public static final String EXTRA_UNLIMITED_DURATION = "ee.ioc.phon.android.extra.UNLIMITED_DURATION";

    /**
     * Boolean.
     * True iff the server has sent final=true, i.e. the following hypotheses
     * will not be transcriptions of the same audio anymore.
     */
    public static final String EXTRA_SEMI_FINAL = "ee.ioc.phon.android.extra.SEMI_FINAL";

    /**
     * Boolean.
     * True iff the recognizer should play audio cues to indicate start and end of
     * recording, as well as error conditions.
     */
    public static final String EXTRA_AUDIO_CUES = "ee.ioc.phon.android.extra.AUDIO_CUES";

    /**
     * Boolean.
     * True iff another app should be used to view/evaluate/execute the recognition result.
     * Used only by the app Arvutaja.
     */
    public static final String EXTRA_USE_EXTERNAL_EVALUATOR = "ee.ioc.phon.android.extra.USE_EXTERNAL_EVALUATOR";

    /**
     * Boolean.
     * Start the recognition session immediately without the user having to press a button.
     */
    public static final String EXTRA_AUTO_START = "ee.ioc.phon.android.extra.AUTO_START";

    /**
     * Boolean. (Default: true in single window mode, false in multi-window mode)
     * True iff voice search panel will be terminated after it launched the intent.
     */
    public static final String EXTRA_FINISH_AFTER_LAUNCH_INTENT = "ee.ioc.phon.android.extra.FINISH_AFTER_LAUNCH_INTENT";

    /**
     * Boolean.
     * In case of an audio/network/etc. error, finish the RecognizerIntent activity with the error code,
     * allowing the caller to handle the error.
     * Normally errors are handled by the activity so that the activity only returns with success.
     * However, in certain situations it is useful to let the caller handle the errors. If this
     * is desired then the caller can request the returning of the errors using this EXTRA.
     */
    public static final String EXTRA_RETURN_ERRORS = "ee.ioc.phon.android.extra.RETURN_ERRORS";

    /**
     * Boolean.
     * True iff caller is interested in the recorded audio data.
     */
    public static final String EXTRA_GET_AUDIO = "android.speech.extra.GET_AUDIO";

    /**
     * String.
     * Mime type of the returned audio data, if EXTRA_GET_AUDIO=true.
     */
    public static final String EXTRA_GET_AUDIO_FORMAT = "android.speech.extra.GET_AUDIO_FORMAT";

    /**
     * Boolean.
     * True iff continuous recognition should be used.
     * Same as EXTRA_UNLIMITED_DURATION.
     * Used on Chrome to talk to Google's recognizer?
     * (http://src.chromium.org/svn/trunk/src/content/public/android/java/src/org/chromium/content/browser/SpeechRecognition.java)
     */
    public static final String EXTRA_DICTATION_MODE = "android.speech.extra.DICTATION_MODE";

    /**
     * <p>Key used to retrieve an {@code ArrayList<String>} from the {@link android.os.Bundle} passed to the
     * {@link android.speech.RecognitionListener#onResults(android.os.Bundle)} and
     * {@link android.speech.RecognitionListener#onPartialResults(android.os.Bundle)} methods.
     * This list represents structured data:</p>
     * <ul>
     * <li>raw utterance of hypothesis 1
     * <li>linearization 1.1
     * <li>language code of linearization 1.1
     * <li>linearization 1.2
     * <li>language code of linearization 1.2
     * <li>...
     * <li>raw utterance of hypothesis 2
     * <li>...
     * </ul>
     * <p/>
     * <p>The number of linearizations for each hypothesis is given by an ArrayList<Integer> from a bundle
     * item accessible via the key RESULTS_RECOGNITION_LINEARIZATION_COUNTS.
     * Both of these bundle items have to be present for the client to be able to use the results.</p>
     */
    public static final String RESULTS_RECOGNITION_LINEARIZATIONS = "ee.ioc.phon.android.extra.RESULTS_RECOGNITION_LINEARIZATIONS";
    public static final String RESULTS_RECOGNITION_LINEARIZATION_COUNTS = "ee.ioc.phon.android.extra.RESULTS_RECOGNITION_LINEARIZATION_COUNTS";

    /**
     * Byte array. Currently not used.
     */
    public static final String RESULTS_AUDIO_ENCODED = "ee.ioc.phon.android.extra.RESULTS_AUDIO_ENCODED";

    /**
     * String (must be a Java regular expression).
     * Regular expression applied to the transcription result(s).
     */
    public static final String EXTRA_RESULT_UTTERANCE = "ee.ioc.phon.android.extra.RESULT_UTTERANCE";

    /**
     * String (must be a Java regular expression replacement).
     * Replacement applied to the transcription result(s) if EXTRA_RESULT_UTTERANCE matches.
     */
    public static final String EXTRA_RESULT_REPLACEMENT = "ee.ioc.phon.android.extra.RESULT_REPLACEMENT";

    /**
     * String.
     * Name of a command.
     */
    public static final String EXTRA_RESULT_COMMAND = "ee.ioc.phon.android.extra.RESULT_COMMAND";

    /**
     * String.
     * Content of the 1st argument of the command.
     */
    public static final String EXTRA_RESULT_ARG1 = "ee.ioc.phon.android.extra.RESULT_ARG1";

    /**
     * String.
     * Content of the 2nd argument of the command.
     */
    public static final String EXTRA_RESULT_ARG2 = "ee.ioc.phon.android.extra.RESULT_ARG2";

    /**
     * Boolean.
     * If @code{true} then the following EXTRAs are set in the following way:
     * EXTRA_RESULT_UTTERANCE = "(.+)"
     * EXTRA_RESULT_COMMAND = "activity"
     * EXTRA_RESULT_ARG1 = "$1"
     */
    public static final String EXTRA_RESULT_LAUNCH_AS_ACTIVITY = "ee.ioc.phon.android.extra.RESULT_LAUNCH_AS_ACTIVITY";

    /**
     * String[].
     * List of transcription results.
     */
    public static final String EXTRA_RESULT_RESULTS = "ee.ioc.phon.android.extra.RESULT_RESULTS";

    /**
     * String[] (String can also be used to denote a single element list)
     * List of names of rewrite tables that should apply to the transcription results.
     */
    public static final String EXTRA_RESULT_REWRITES = "ee.ioc.phon.android.extra.RESULT_REWRITES";

    /**
     * String.
     * Rewrite table (in TSV-format and with a header) that should apply to the transcription results.
     */
    public static final String EXTRA_RESULT_REWRITES_AS_STR = "ee.ioc.phon.android.extra.RESULT_REWRITES_AS_STR";

    /**
     * Used only by the app Arvutaja.
     *
     * @deprecated instead use EXTRA_AUTO_START
     */
    public static final String EXTRA_LAUNCH_RECOGNIZER = "ee.ioc.phon.android.extra.LAUNCH_RECOGNIZER";

    /**
     * android/speech/RecognizerIntent.html#ACTION_VOICE_SEARCH_HANDS_FREE (API 16)
     */
    public static final String ACTION_VOICE_SEARCH_HANDS_FREE = "android.speech.action.VOICE_SEARCH_HANDS_FREE";

    /**
     * A non-standard (undocumented) android.speech.extra
     */
    public static final String EXTRA_ADDITIONAL_LANGUAGES = "android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES";
}