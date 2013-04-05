/*
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *    Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *    Neither the name of Code Aurora Forum, Inc. nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.settings.multisimsettings;

import static com.android.internal.telephony.MSimConstants.SUBSCRIPTION_KEY;
import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.widget.TabHost;
import android.widget.TextView;

import com.android.settings.R;

public class MultiSimSettingTab extends TabActivity {

    private static final String LOG_TAG = "MultiSimSettingWidget";

    private static final boolean DBG = true;

    private int[] tabIcons = {
            R.drawable.ic_tab_sim1, R.drawable.ic_tab_sim2
    };

    private String[] tabSpecTags = {
            "sub1", "sub2"
    };

    private LocalMultiSimSettingsManager mLocalManager;

    private Intent mIntent;

    @Override
    public void onPause() {
        super.onPause();
    }

    /*
     * Activity class methods
     */

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (DBG)
            log("Creating activity");
        mLocalManager = LocalMultiSimSettingsManager.getInstance(this);

        mIntent = getIntent();

        setContentView(R.layout.multi_sim_setting_tab);

        Resources res = getResources(); // Resource object to get Drawables
        TabHost tabHost = getTabHost(); // The activity TabHost
        TabHost.TabSpec spec; // Resusable TabSpec for each tab
        Intent intent; // Reusable Intent for each tab

        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            String packageName = mIntent.getStringExtra(MultiSimSettingsConstants.TARGET_PACKAGE);
            String className = mIntent.getStringExtra(MultiSimSettingsConstants.TARGET_CLASS);

            // come in from shortcut packagename and classname is null
            if (packageName == null)
                packageName = MultiSimSettingsConstants.CONFIG_PACKAGE;
            if (className == null)
                className = MultiSimSettingsConstants.CONFIG_CLASS;
            // Create an Intent to launch an Activity for the tab (to be reused)
            intent = new Intent().setClassName(packageName, className)
                    .setAction(mIntent.getAction()).putExtra(SUBSCRIPTION_KEY, i);
            // Initialize a TabSpec for each tab and add it to the TabHost
            spec = tabHost.newTabSpec(tabSpecTags[i])
                    .setIndicator(getMultiSimName(i), res.getDrawable(tabIcons[i]))
                    .setContent(intent);
            // Add new spec to Tab
            tabHost.addTab(spec);
        }
        tabHost.setCurrentTab(mIntent.getIntExtra(SUBSCRIPTION_KEY, 0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLocalManager.registerForSimNameChange(mHandler,
                MultiSimSettingsConstants.EVENT_MULTI_SIM_NAME_CHANGED, null);
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private String getMultiSimName(int subscription) {
	    return "card"+subscription;
        //return Settings.System.getString(this.getContentResolver(),
        //        Settings.System.MULTI_SIM_NAME[subscription]);
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            switch (msg.what) {
                case MultiSimSettingsConstants.EVENT_MULTI_SIM_NAME_CHANGED:
                    handleSimNameChanged(ar);
                    break;
            }
        }
    };

    private void handleSimNameChanged(AsyncResult ar) {
        int subscription = ((Integer) ar.result).intValue();
        if (DBG)
            Log.d(LOG_TAG, "sim name changed on sub" + subscription);
        TextView simName = (TextView) getTabHost().getTabWidget().getChildAt(subscription)
                .findViewById(com.android.internal.R.id.title);
        simName.setText(mLocalManager.getMultiSimName(subscription));
    }
}
