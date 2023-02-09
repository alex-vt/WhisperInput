package ee.ioc.phon.android.speechutils;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;

import androidx.annotation.RequiresPermission;

import static android.Manifest.permission.RECORD_AUDIO;

/**
 * The following takes effect only on Jelly Bean and higher.
 *
 * @author Kaarel Kaljurand
 * @deprecated Use SpeechAudioRecord
 */
public class SpeechRecord extends AudioRecord {

    @RequiresPermission(RECORD_AUDIO)
    public SpeechRecord(int sampleRateInHz, int bufferSizeInBytes)
            throws IllegalArgumentException {

        this(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRateInHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeInBytes,
                false,
                false,
                false
        );
    }

    @RequiresPermission(RECORD_AUDIO)
    public SpeechRecord(int sampleRateInHz, int bufferSizeInBytes, boolean noise, boolean gain, boolean echo)
            throws IllegalArgumentException {

        this(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRateInHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeInBytes,
                noise,
                gain,
                echo
        );
    }


    // This is a copy of the AudioRecord constructor

    @RequiresPermission(RECORD_AUDIO)
    public SpeechRecord(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes)
            throws IllegalArgumentException {

        this(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, false, false, false);
    }

    @RequiresPermission(RECORD_AUDIO)
    public SpeechRecord(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes,
                        boolean noise, boolean gain, boolean echo)
            throws IllegalArgumentException {

        super(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Log.i("Trying to enhance audio because running on SDK " + Build.VERSION.SDK_INT);

            int audioSessionId = getAudioSessionId();

            if (noise) {
                if (NoiseSuppressor.create(audioSessionId) == null) {
                    Log.i("NoiseSuppressor: failed");
                } else {
                    Log.i("NoiseSuppressor: ON");
                }
            } else {
                Log.i("NoiseSuppressor: OFF");
            }

            if (gain) {
                if (AutomaticGainControl.create(audioSessionId) == null) {
                    Log.i("AutomaticGainControl: failed");
                } else {
                    Log.i("AutomaticGainControl: ON");
                }
            } else {
                Log.i("AutomaticGainControl: OFF");
            }

            if (echo) {
                if (AcousticEchoCanceler.create(audioSessionId) == null) {
                    Log.i("AcousticEchoCanceler: failed");
                } else {
                    Log.i("AcousticEchoCanceler: ON");
                }
            } else {
                Log.i("AcousticEchoCanceler: OFF");
            }
        }
    }


    public static boolean isNoiseSuppressorAvailable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return NoiseSuppressor.isAvailable();
        }
        return false;
    }
}