package com.alexvt.whisperinput.speak.fragment;

/*
 * Copyright 2011-2016, Institute of Cybernetics at Tallinn University of Technology
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

import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.ListFragment;

import com.alexvt.whisperinput.R;

/**
 * <p>Some methods that various list fragments can share by extending
 * this class rather than extending the ListFragment-class directly.</p>
 *
 * @author Kaarel Kaljurand
 */
public abstract class K6neleListFragment extends ListFragment {

    protected void toast(String message) {
        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
    }

    protected void setEmptyView(String text) {
        ListView lv = getListView();
        TextView emptyView = (TextView) getActivity().getLayoutInflater().inflate(R.layout.empty_list, null);
        emptyView.setText(text);
        emptyView.setVisibility(View.GONE);
        ((ViewGroup) lv.getParent()).addView(emptyView);
        lv.setEmptyView(emptyView);
    }
}