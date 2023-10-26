package ee.ioc.phon.android.speechutils;

import static android.Manifest.permission.RECORD_AUDIO;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

import androidx.annotation.RequiresPermission;

public class SpeechAudioRecord {

    @RequiresPermission(RECORD_AUDIO)
    public static AudioRecord create(int sampleRateInHz, int bufferSizeInBytes)
            throws IllegalArgumentException {

        return create(
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
    public static AudioRecord create(int sampleRateInHz, int bufferSizeInBytes, boolean noise, boolean gain, boolean echo)
            throws IllegalArgumentException {

        return create(
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

    @RequiresPermission(RECORD_AUDIO)
    public static AudioRecord create(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes)
            throws IllegalArgumentException {

        return create(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, false, false, false);
    }

    @RequiresPermission(RECORD_AUDIO)
    public static AudioRecord create(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes,
                                     boolean noise, boolean gain, boolean echo)
            throws IllegalArgumentException, SecurityException {

        // TODO: setPrivacySensitive(true) if API 30+
        AudioRecord audioRecord = new AudioRecord.Builder()
                .setAudioSource(audioSource)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRateInHz)
                        .setChannelMask(channelConfig)
                        .build())
                .setBufferSizeInBytes(bufferSizeInBytes)
                .build();


        int audioSessionId = audioRecord.getAudioSessionId();

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

        return audioRecord;
    }


    public static boolean isNoiseSuppressorAvailable() {
        return NoiseSuppressor.isAvailable();
    }
}