package com.alexvt.whisperinput.speak.activity;

import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;

import com.alexvt.whisperinput.speak.QuickSettingsManager;
import com.alexvt.whisperinput.R;

public class QuickSettingsActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quick_settings);

        final QuickSettingsManager mngr = new QuickSettingsManager(
                PreferenceManager.getDefaultSharedPreferences(QuickSettingsActivity.this),
                getResources());

        findViewById(R.id.buttonApplyDeveloperDefaults)
                .setOnClickListener(view -> mngr.setDefaultsDevel());
    }
}