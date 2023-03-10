package com.alexvt.whisperinput.speak;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

// Copied from http://stackoverflow.com/a/19506010/12547
public class OnSwipeTouchListener implements View.OnTouchListener {

    private final SpringForce SPRING = new SpringForce(0)
            .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
            .setStiffness(SpringForce.STIFFNESS_LOW);

    private final GestureDetector mGestureDetector;
    private final View mView;

    public OnSwipeTouchListener(Context context) {
        this(context, null);
    }

    public OnSwipeTouchListener(Context context, View view) {
        mGestureDetector = new GestureDetector(context, new GestureListener());
        mView = view;
    }

    public void onSwipeLeft() {
        // intentionally empty
    }

    public void onSwipeRight() {
        // intentionally empty
    }

    public void onSwipeUp() {
        // intentionally empty
    }

    public void onSwipeDown() {
        // intentionally empty
    }

    public void onSingleTapMotion() {
        // intentionally empty
    }

    public void onDoubleTapMotion() {
        // intentionally empty
    }

    public void onLongPressMotion() {
        // intentionally empty
    }

    public boolean onTouch(View v, MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private static final int SWIPE_DISTANCE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float changeX = e2.getX() - e1.getX();
            float changeY = e2.getY() - e1.getY();
            float distanceX = Math.abs(changeX);
            float distanceY = Math.abs(changeY);
            if (distanceX > distanceY && distanceX > SWIPE_DISTANCE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (changeX > 0) {
                    onSwipeRight();
                } else {
                    onSwipeLeft();
                }
                if (mView != null) {
                    final SpringAnimation anim = new SpringAnimation(mView, DynamicAnimation.TRANSLATION_X)
                            .setSpring(SPRING).setStartValue(changeX);
                    anim.start();
                }
                return true;
            } else if (distanceY > distanceX && distanceY > SWIPE_DISTANCE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (changeY > 0) {
                    onSwipeDown();
                } else {
                    onSwipeUp();
                }
                if (mView != null) {
                    final SpringAnimation anim = new SpringAnimation(mView, DynamicAnimation.TRANSLATION_Y)
                            .setSpring(SPRING).setStartValue(changeY);
                    anim.start();
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            onSingleTapMotion();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            onDoubleTapMotion();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            onLongPressMotion();
        }
    }
}