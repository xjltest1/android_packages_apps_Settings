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

import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import com.android.settings.R;

//import com.qrd.plugin.feature_query.FeatureQuery;


public class MultiSimSettings extends PreferenceActivity {
    private static final String TAG = "MultiSimSettings";

    private static final String[] KEY_PREFERRED_SUBSCRIPTION_LIST = {"voice_list", "data_list", "sms_list"};

    private static final String KEY_COUNTDOWN_TIMER = "multi_sim_countdown";
    private static final String KEY_CALLBACK_TOGGLE = "callback_enable_key";
    private static final String KEY_CONFIG_SUB = "config_sub";
    private static final String KEY_SHOW_DURATION = "duration_enable_key";

    private PreferredSubscriptionListPreference[] mPreferredSubLists;
    private CountDownPreference mCountDown;
    private CallbackEnabler mCallbackToggle;
    private CheckBoxPreference showDurationCheckBox;

    private PreferenceScreen mConfigSub;


    private void initPreferences() {
        mPreferredSubLists = new PreferredSubscriptionListPreference[KEY_PREFERRED_SUBSCRIPTION_LIST.length];

        for (int i = 0; i < KEY_PREFERRED_SUBSCRIPTION_LIST.length; i++) {
             mPreferredSubLists[i] = (PreferredSubscriptionListPreference)findPreference(KEY_PREFERRED_SUBSCRIPTION_LIST[i]);
             mPreferredSubLists[i].setType(MultiSimSettingsConstants.PREFERRED_SUBSCRIPTION_LISTS[i]);
        }

        PreferenceCategory prefCat = (PreferenceCategory) findPreference("preferred_subscription_settings");

        //if (!FeatureQuery.FEATURE_SETTING_DATA_CHANNEL_SWITCH_OPTION_JAVA) {
		if(false){
        	prefCat.removePreference(mPreferredSubLists[1]);
        }

        mCountDown = (CountDownPreference)findPreference(KEY_COUNTDOWN_TIMER);
        mCountDown.updateSummary();

        mCallbackToggle = (CallbackEnabler)findPreference(KEY_CALLBACK_TOGGLE);

        mConfigSub = (PreferenceScreen) findPreference(KEY_CONFIG_SUB);
        //mConfigSub.getIntent().putExtra(CONFIG_SUB, true);
        if (mConfigSub != null) {
            Intent intent = mConfigSub.getIntent();
            intent.putExtra(MultiSimSettingsConstants.TARGET_PACKAGE, MultiSimSettingsConstants.CONFIG_PACKAGE);
            intent.putExtra(MultiSimSettingsConstants.TARGET_CLASS, MultiSimSettingsConstants.CONFIG_CLASS);
        }

        showDurationCheckBox = (CheckBoxPreference) findPreference(KEY_SHOW_DURATION);
        showDurationCheckBox.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.SHOW_DURATION, 0) == 1);
        showDurationCheckBox
                .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        int flag = (Boolean) newValue == true ? 1 : 0;
                        if (Settings.System.putInt(getContentResolver(),
                                Settings.System.SHOW_DURATION, flag)) {
                            return true;
                        }
                        return false;
                    }
                });
        //if (!FeatureQuery.FEATURE_SHOW_DURATION_AFTER_CALL) {
		if(false){
            getPreferenceScreen().removePreference(findPreference(KEY_SHOW_DURATION));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.multi_sim_settings_qi);
        LocalMultiSimSettingsManager.getInstance(getApplicationContext()).setForegroundActivity(this);
        initPreferences();
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (PreferredSubscriptionListPreference subPref : mPreferredSubLists) {
             subPref.resume();
        }
        mCallbackToggle.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (PreferredSubscriptionListPreference subPref : mPreferredSubLists) {
             subPref.pause();
        }
        mCallbackToggle.pause();
    }
}
