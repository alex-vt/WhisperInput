package ee.ioc.phon.android.speechutils;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Build;

/**
 * Pauses the audio stream by requesting the audio focus and
 * muting the music stream.
 * <p/>
 * TODO: Test this with two interleaving instances of AudioPauser, e.g.
 * TTS starts playing and calls the AudioPauser, at the same time
 * the recognizer starts listening and also calls the AudioPauser.
 */
public class AudioPauser {

    private final boolean mIsMuteStream;
    private final AudioManager mAudioManager;
    private final OnAudioFocusChangeListener mAfChangeListener;
    private int mCurrentVolume = 0;
    private boolean isPausing = false;

    private AudioPauser(Context context, boolean isMuteStream) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mIsMuteStream = isMuteStream;
        mAfChangeListener = focusChange -> Log.i("onAudioFocusChange" + focusChange);
    }

    /**
     * Creates and returns an AudioPauser.
     *
     * @param context      Context
     * @param isMuteStream if true then we additionally try to mute the audio stream.
     *                     This does not succeed if the app is not allowed to
     *                     "modify notification do not disturb policy" on Android N and higher.
     * @return AudioPauser
     */
    public static AudioPauser createAudioPauser(Context context, boolean isMuteStream) {
        if (isMuteStream && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && !nm.isNotificationPolicyAccessGranted()) {
                isMuteStream = false;
            }
        }
        return new AudioPauser(context, isMuteStream);
    }

    public boolean isMuteStream() {
        return mIsMuteStream;
    }

    /**
     * Requests audio focus with the goal of pausing any existing audio player.
     * Additionally mutes the music stream, since some audio players might
     * ignore the focus request.
     * In other words, during the pause no sound will be heard,
     * but whether the audio resumes from the same position after the pause
     * depends on the audio player.
     */
    public void pause() {
        if (!isPausing) {
            int result = mAudioManager.requestAudioFocus(mAfChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i("AUDIOFOCUS_REQUEST_GRANTED");
            }

            if (mIsMuteStream) {
                mCurrentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (mCurrentVolume > 0) {
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                }
            }
            isPausing = true;
        }
    }


    /**
     * Abandons audio focus and restores the audio volume.
     */
    public void resume() {
        if (isPausing) {
            mAudioManager.abandonAudioFocus(mAfChangeListener);
            if (mIsMuteStream && mCurrentVolume > 0) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mCurrentVolume, 0);
            }
            isPausing = false;
        }
    }

}