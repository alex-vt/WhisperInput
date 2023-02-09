package ee.ioc.phon.android.speechutils.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.List;

import ee.ioc.phon.android.speechutils.Log;
import ee.ioc.phon.android.speechutils.R;

// TODO: rather use com.google.android.material.floatingactionbutton.FloatingActionButton
public class MicButton extends androidx.appcompat.widget.AppCompatImageButton {

    public enum State {
        // Initial state
        INIT,
        // An attempt was made to start the recording/transcription, but unclear if it succeeded
        WAITING,
        // We are listening (if there is human speech)
        LISTENING,
        // We are recording and sending the (speech) audio for transcription
        RECORDING,
        // Recording has stopped but not all the recorded audio has been processed yet
        TRANSCRIBING,
        // An error has occurred
        ERROR
    }

    // TODO: rename to COLOR_RECORDING
    public static final int COLOR_LISTENING = Color.argb(255, 198, 40, 40);
    public static final int COLOR_TRANSCRIBING = Color.argb(255, 153, 51, 204);

    // Must equal to the last index of mVolumeLevels
    private static final int MAX_LEVEL = 3;

    private float mMinRmsDb;
    private float mMaxRmsDb;

    private Drawable mDrawableMic;
    private Drawable mDrawableMicWaiting;
    private Drawable mDrawableMicListening;
    private Drawable mDrawableMicTranscribing;

    private List<Drawable> mVolumeLevels;

    private Animation mAnimFadeInOutInf;

    private int mVolumeLevel = 0;

    public MicButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (!isInEditMode()) {
            init(context);
        }
    }

    public MicButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            init(context);
        }
    }

    public MicButton(Context context) {
        super(context);
        if (!isInEditMode()) {
            init(context);
        }
    }

    public void setState(State state) {
        Log.i("WHISPER MicButton state " + state.name());
        switch (state) {
            case INIT:
            case ERROR:
                mMinRmsDb = mMaxRmsDb = 0f;
                setEnabled(true);
                clearAnimation();
                setBackgroundDrawable(mDrawableMic);
                break;
            case WAITING:
                setEnabled(false);
                setBackgroundDrawable(mDrawableMicWaiting);
                break;
            case RECORDING:
                setEnabled(true);
                setBackgroundDrawable(mVolumeLevels.get(0));
                break;
            case LISTENING:
                setEnabled(true);
                setBackgroundDrawable(mDrawableMicListening);
                break;
            case TRANSCRIBING:
                setEnabled(true);
                setBackgroundDrawable(mDrawableMicTranscribing);
                startAnimation(mAnimFadeInOutInf);
                break;
            default:
                break;
        }
    }


    public void setVolumeLevel(float rmsdB) {
        if (mMinRmsDb == mMaxRmsDb) {
            mMinRmsDb = rmsdB;
            mMaxRmsDb = mMinRmsDb + 1;
        } else if (rmsdB < mMinRmsDb) {
            mMinRmsDb = rmsdB;
        } else if (rmsdB > mMaxRmsDb) {
            mMaxRmsDb = rmsdB;
        }
        int index = (int) (MAX_LEVEL * ((rmsdB - mMinRmsDb) / (mMaxRmsDb - mMinRmsDb)));
        int level = Math.min(Math.max(0, index), MAX_LEVEL);
        if (level != mVolumeLevel) {
            mVolumeLevel = level;
            setBackgroundDrawable(mVolumeLevels.get(level));
        }
    }

    private void initAnimations(Context context) {
        Resources res = getResources();
        mDrawableMic = ResourcesCompat.getDrawable(res, R.drawable.button_mic, null);
        mDrawableMicWaiting = ResourcesCompat.getDrawable(res, R.drawable.button_mic_waiting, null);
        mDrawableMicListening = ResourcesCompat.getDrawable(res, R.drawable.button_mic_listening, null);
        mDrawableMicTranscribing = ResourcesCompat.getDrawable(res, R.drawable.button_mic_transcribing, null);

        mVolumeLevels = new ArrayList<>();
        mVolumeLevels.add(ResourcesCompat.getDrawable(res, R.drawable.button_mic_recording_0, null));
        mVolumeLevels.add(ResourcesCompat.getDrawable(res, R.drawable.button_mic_recording_1, null));
        mVolumeLevels.add(ResourcesCompat.getDrawable(res, R.drawable.button_mic_recording_2, null));
        mVolumeLevels.add(ResourcesCompat.getDrawable(res, R.drawable.button_mic_recording_3, null));

        mAnimFadeInOutInf = AnimationUtils.loadAnimation(context, R.anim.fade_inout_inf);
    }

    private void init(Context context) {
        initAnimations(context);

        // Vibrate when the microphone key is pressed down
        setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // TODO: what is the diff between KEYBOARD_TAP and the other constants?
                // TODO: does not seem to work on Android 7.1
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            return false;
        });
    }
}