package com.alexvt.whisperinput.speak;

import android.content.SharedPreferences;
import android.content.res.Resources;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.alexvt.whisperinput.R;

public class QuickSettingsManager {

    private static final Set<String> COMBOS_MULTILINGUAL;

    static {
        Set<String> set = new HashSet<>();
        set.add("com.alexvt.whisperinput/.speak.service.OfflineRecognitionService");
        COMBOS_MULTILINGUAL = Collections.unmodifiableSet(set);
    }

    private SharedPreferences mPrefs;
    private Resources mRes;

    public QuickSettingsManager(SharedPreferences prefs, Resources res) {
        mPrefs = prefs;
        mRes = res;
    }

    public void setDefaultsDevel() {
        SharedPreferences.Editor editor = mPrefs.edit();

        // Speech keyboard
        editor.putStringSet(mRes.getString(R.string.keyImeCombo), COMBOS_MULTILINGUAL);
        editor.putBoolean(mRes.getString(R.string.keyImeHelpText), false);
        editor.putBoolean(mRes.getString(R.string.keyImeAutoStart), false);

        // Search panel
        editor.putStringSet(mRes.getString(R.string.keyCombo), COMBOS_MULTILINGUAL);
        editor.putBoolean(mRes.getString(R.string.keyHelpText), false);
        editor.putBoolean(mRes.getString(R.string.keyAutoStart), false);
        editor.putInt(mRes.getString(R.string.keyMaxHypotheses), 4);

        // HTTP service
        editor.putString(mRes.getString(R.string.keyAudioFormat), "audio/x-flac");
        editor.putBoolean(mRes.getString(R.string.keyAudioCues), false);
        editor.putString(mRes.getString(R.string.keyAutoStopAfterTime), "10");

        // WebSocket service
        editor.putString(mRes.getString(R.string.keyImeAudioFormat), "audio/x-flac");
        editor.putBoolean(mRes.getString(R.string.keyImeAudioCues), false);

        editor.apply();
    }
}