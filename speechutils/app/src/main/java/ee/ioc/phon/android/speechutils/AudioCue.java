package ee.ioc.phon.android.speechutils;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.SystemClock;

// TODO: add a method that calls back when audio is finished
public class AudioCue {

    private static final int DELAY_AFTER_START_BEEP = 200;

    private final Context mContext;
    private final int mStartSound;
    private final int mStopSound;
    private final int mErrorSound;

    public AudioCue(Context context) {
        mContext = context;
        mStartSound = R.raw.explore_begin;
        mStopSound = R.raw.explore_end;
        mErrorSound = R.raw.error;
    }

    public AudioCue(Context context, int startSound, int stopSound, int errorSound) {
        mContext = context;
        mStartSound = startSound;
        mStopSound = stopSound;
        mErrorSound = errorSound;
    }

    public void playStartSoundAndSleep() {
        if (playSound(mStartSound)) {
            SystemClock.sleep(DELAY_AFTER_START_BEEP);
        }
    }


    public void playStopSound() {
        playSound(mStopSound);
    }


    public void playErrorSound() {
        playSound(mErrorSound);
    }


    private boolean playSound(int sound) {
        MediaPlayer mp = MediaPlayer.create(mContext, sound);
        // create can return null, e.g. on Android Wear
        if (mp == null) {
            return false;
        }
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mp.setOnCompletionListener(MediaPlayer::release);
        mp.start();
        return true;
    }

}