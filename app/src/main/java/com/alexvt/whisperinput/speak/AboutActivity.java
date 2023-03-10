/*
 * Copyright 2011-2020, Institute of Cybernetics at Tallinn University of Technology
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

package com.alexvt.whisperinput.speak;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.alexvt.whisperinput.databinding.AboutBinding;
import com.alexvt.whisperinput.R;
import com.alexvt.whisperinput.speak.utils.Utils;
import ee.ioc.phon.android.speechutils.utils.PreferenceUtils;

/**
 * <p>Simple activity that shows the info page (help page).
 * The content and style of this activity are defined entirely
 * in the resource XML files. The content is interpreted here
 * as an HTML string, i.e. one can use simple HTML-formatting
 * and linking.</p>
 *
 * @author Kaarel Kaljurand
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AboutBinding binding = AboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(R.string.labelApp);
            ab.setSubtitle("v" + Utils.getVersionName(this));
        }
        binding.tvAbout.setMovementMethod(LinkMovementMethod.getInstance());
        String about = String.format(
                getString(R.string.tvAbout),
                getString(R.string.labelApp),
                PreferenceUtils.getUniqueId(PreferenceManager.getDefaultSharedPreferences(this))
        );
        binding.tvAbout.setText(Html.fromHtml(about));
    }
}