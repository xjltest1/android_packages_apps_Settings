/*
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
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
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.provider.Settings;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.System;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.msim.CardSubscriptionManager;
import com.android.internal.telephony.msim.Subscription.SubscriptionStatus;
import com.android.internal.telephony.msim.SubscriptionManager;
import com.android.internal.telephony.TelephonyIntents;
import android.os.SystemProperties;
import com.android.settings.R;
import com.android.settings.SimStateReceiver;
//import com.qrd.plugin.feature_query.FeatureQuery;

import android.os.SystemProperties;
import com.android.internal.telephony.TelephonyProperties;


public class MultiSimConfiguration extends PreferenceActivity {
    private static final String LOG_TAG = "MultiSimConfiguration";

    private static final String KEY_SIM_NAME = "sim_name_key";
    private static final String KEY_SIM_ENABLER = "sim_enabler_key";
    private static final String KEY_NETWORK_SETTING = "mobile_network_key";
    private static final String KEY_CALL_SETTING = "call_setting_key";
    private static final String KEY_NETWORK_MODE_SETTING = "network_choice_key";
    private static final String KEY_GSM_OPERATORS_SETTING = "button_GSM_carrier_sel_key";
    private static final String KEY_CDMA_OPERATORS_SETTING = "button_CDMA_carrier_sel_key";
    private static final String KEY_STATUS_INFO = "status_info_key";
    private static final String KEY_DATA_ROAMING = "enable_data_roaming_key";
    private static final String KEY_ROAMING_AUTO_RECEIVE_MMS = "roaming_auto_receive_mms_key";

    private static final int EVENT_SET_SUBSCRIPTION_DONE = 2;
    public static final int REMOVE_DIALOG_NETWORK_SELECTION = 3;

    private static final int DIALOG_NETWORK_SELECTION = 100;

    private PreferenceScreen mPrefScreen;
    private SubscriptionManager mSubscriptionManager;
    private PreferenceScreen mNetworkSetting;
    private PreferenceScreen mCallSetting;
    private PreferenceScreen mGsmOperatorSetting;
    private PreferenceScreen mCdmaOperatorSetting;
    private PreferenceScreen mStatusInfo;
    private NetworkModeSelectListPreference mNetworkModeSetting;
    private CheckBoxPreference mDataRoamingEnabler;
    private CheckBoxPreference mRoamingAutoReceiveMmsEnabler;

    private int mSubscription;
    private MultiSimNamePreference mNamePreference;
    private MultiSimEnabler mEnablerPreference;

    private ContentResolver resolver = null;
    boolean OkClicked = false;

    private IntentFilter mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logd("onReceive " + action);
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action) ||
                Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                setScreenState();
                mEnablerPreference.updateSimEnablerPreference();
            }
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SET_SUBSCRIPTION_DONE:
                    setScreenState();
                    break;

                case REMOVE_DIALOG_NETWORK_SELECTION:
                	removeDialog(DIALOG_NETWORK_SELECTION);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.multi_sim_configuration);

        resolver = getContentResolver();

        mPrefScreen = getPreferenceScreen();

        Intent intent = getIntent();
        mSubscription = intent.getIntExtra(SUBSCRIPTION_KEY, 0);

        mSubscriptionManager = SubscriptionManager.getInstance();

        /*mNamePreference = (MultiSimNamePreference)findPreference(KEY_SIM_NAME);
        mNamePreference.setSubscription(mSubscription);*/

        mEnablerPreference = (MultiSimEnabler)findPreference(KEY_SIM_ENABLER);
        mEnablerPreference.setSubscription(this, mSubscription, mHandler);
        mNetworkSetting = (PreferenceScreen)findPreference(KEY_NETWORK_SETTING);
        mNetworkSetting.getIntent().putExtra(MultiSimSettingsConstants.TARGET_PACKAGE,
                               MultiSimSettingsConstants.NETWORK_PACKAGE)
                                    .putExtra(MultiSimSettingsConstants.TARGET_CLASS,
                               MultiSimSettingsConstants.NETWORK_CLASS)
                                    .putExtra(SUBSCRIPTION_KEY, mSubscription);

        mCallSetting = (PreferenceScreen)findPreference(KEY_CALL_SETTING);
        mCallSetting.getIntent().putExtra(MultiSimSettingsConstants.TARGET_PACKAGE,
                               MultiSimSettingsConstants.CALL_PACKAGE)
                                    .putExtra(MultiSimSettingsConstants.TARGET_CLASS,
                               MultiSimSettingsConstants.CALL_CLASS)
                                    .putExtra(SUBSCRIPTION_KEY, mSubscription);

        mDataRoamingEnabler = (CheckBoxPreference)findPreference(KEY_DATA_ROAMING);

        final String dataRoamingKeyClone = checkDataRoamingState();
        mDataRoamingEnabler.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean dataRoamingEnabledBoolean = ((Boolean)newValue).booleanValue();
				final boolean dataRoamingEnabledBooleanClone = dataRoamingEnabledBoolean;
				OkClicked = false;

				if(dataRoamingEnabledBooleanClone)
				{
					new AlertDialog.Builder(MultiSimConfiguration.this).setMessage(
	                        getResources().getString(R.string.roaming_warning))
	                        .setTitle(android.R.string.dialog_alert_title)
	                        .setIcon(android.R.drawable.ic_dialog_alert)
	                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {
									Settings.System.putInt(resolver, Settings.System.DATA_ROAMING_SUB1, dataRoamingEnabledBooleanClone ? 1 : 0);
					                Settings.System.putInt(resolver, Settings.System.DATA_ROAMING_SUB2, dataRoamingEnabledBooleanClone ? 1 : 0);
									OkClicked = true;
								}
							})
	                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {
									mDataRoamingEnabler.setChecked(false);
									OkClicked = false;
								}
							})
	                        .show()
	                        .setOnDismissListener(new DialogInterface.OnDismissListener() {

								@Override
								public void onDismiss(DialogInterface dialog) {
									if(!OkClicked)
									{
										mDataRoamingEnabler.setChecked(false);

									}
									logd("dialog onDismiss");
								}
							});
				}else
				{
					Settings.System.putInt(resolver, Settings.System.DATA_ROAMING_SUB1, dataRoamingEnabledBooleanClone ? 1 : 0);
					Settings.System.putInt(resolver, Settings.System.DATA_ROAMING_SUB2, dataRoamingEnabledBooleanClone ? 1 : 0);
				}

				logd("DataRoamingEnabler checked status == " + String.valueOf(dataRoamingEnabledBoolean));

				return true;
			}
		});


        mRoamingAutoReceiveMmsEnabler = (CheckBoxPreference)findPreference(KEY_ROAMING_AUTO_RECEIVE_MMS);

        final String roamingMmsKeyClone = checkRoamingMMSAutoReceivedState();
        mRoamingAutoReceiveMmsEnabler.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean roamingMmsEnabledBoolean = ((Boolean)newValue).booleanValue();

				logd("mRoamingAutoReceiveMmsEnabler checked status == " + String.valueOf(roamingMmsEnabledBoolean));
	            Settings.System.putInt(resolver, roamingMmsKeyClone, roamingMmsEnabledBoolean ? 1 : 0);

				return true;
			}
		});


        mStatusInfo = (PreferenceScreen)findPreference(KEY_STATUS_INFO);

        Intent mGsmOperatorSettingIntent = new Intent();
        mGsmOperatorSettingIntent.setClassName("com.android.phone", "com.android.phone.NetworkSetting");
        mGsmOperatorSettingIntent.putExtra(SUBSCRIPTION_KEY, mSubscription);


        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);

        //if(!FeatureQuery.FEATURE_SETTING_INTERNATIONAL_ROAMING_OPTIONS_JAVA)
    }

    protected void onResume() {
        super.onResume();

        registerReceiver(mReceiver, mIntentFilter);
        mEnablerPreference.resume();
        setScreenState();
        checkDataRoamingState();
        checkRoamingMMSAutoReceivedState();
        mSubscriptionManager.registerForSetSubscriptionCompleted(mHandler, EVENT_SET_SUBSCRIPTION_DONE, null);
    }

    protected void onPause() {
        super.onPause();

        unregisterReceiver(mReceiver);
        //mProxyManager.unRegisterForSetSubscriptionCompleted(mHandler);
        mEnablerPreference.pause();
    }

    private boolean isSubActivated() {
        return mSubscriptionManager.isSubActive(mSubscription);
    }

    private boolean isAirplaneModeOn() {
        return (System.getInt(getContentResolver(), System.AIRPLANE_MODE_ON, 0) != 0);
    }

    // check whether has SIM card
    private boolean hasCard() {
//        CardSubscriptionManager cardSubMgr = CardSubscriptionManager.getInstance();
//        if (cardSubMgr != null && cardSubMgr.getCardSubscriptions(mSubscription) != null) {
//            return true;
//        }
//    	return false;


    	logd("mSubscription :  " + mSubscription );
    	if(mSubscription == 0)
    	{
    		int cardPresent = Settings.System.getInt(resolver, "card1present", 0);
    		logd("card1Present :  " + cardPresent );
    		return cardPresent == 1? true:false;
    	}else if(mSubscription == 1)
    	{
    		int cardPresent = Settings.System.getInt(resolver, "card2present", 0);
    		logd("card2Present :  " + cardPresent );
    		return cardPresent == 1? true:false;

    	}
    	return false;

    }

    private void setScreenState() {
        if (isAirplaneModeOn()) {
            mNetworkSetting.setEnabled(false);
            mCallSetting.setEnabled(false);
            mEnablerPreference.setEnabled(false);
            mStatusInfo.setEnabled(false);
            mDataRoamingEnabler.setEnabled(false);
            mRoamingAutoReceiveMmsEnabler.setEnabled(false);
        } else {
            mNetworkSetting.setEnabled(isSubActivated());
            mCallSetting.setEnabled(isSubActivated());

            logd("hasCard():  " + hasCard() );
//            mEnablerPreference.setEnabled(hasCard());
            mEnablerPreference.setEnabled(true);
			mNetworkModeSetting.setEnabled(isSubActivated());
	    mStatusInfo.setEnabled(isSubActivated());

            mDataRoamingEnabler.setEnabled(isSubActivated());
            mRoamingAutoReceiveMmsEnabler.setEnabled(isSubActivated());

        }
    }

    private void logd(String msg) {
        Log.d(LOG_TAG, "[" + LOG_TAG + "(" + mSubscription + ")] " + msg);
    }

    @Override
    protected Dialog onCreateDialog(int id) {

        if (id == DIALOG_NETWORK_SELECTION) {
            ProgressDialog dialog = new ProgressDialog(this);
            switch (id) {
                case DIALOG_NETWORK_SELECTION:
                    String text = getString(R.string.switch_network_mode);
                    dialog.setMessage(text);
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    break;

                default:
                    break;
            }
            return dialog;
        }
        return null;
    }

    private String checkDataRoamingState()
    {
    	String dataRoamingKey = "";
    	if(mSubscription == 0)
        {
        	dataRoamingKey = Settings.System.DATA_ROAMING_SUB1;
        	//dataRoamingKey = "data_roaming_sub1";
        }else if(mSubscription == 1)
        {
        	dataRoamingKey = Settings.System.DATA_ROAMING_SUB2;
        	//dataRoamingKey = "data_roaming_sub2";

        }

    	int dataRoamingEnabled = Settings.System.getInt(resolver, dataRoamingKey, 0);
        logd("dataRoamingEnabled(0 is false, as default) == " + dataRoamingEnabled);
        if(dataRoamingEnabled == 0)
        {
        	mDataRoamingEnabler.setChecked(false);
        }else if(dataRoamingEnabled == 1)
        {
        	mDataRoamingEnabler.setChecked(true);
        }

        return dataRoamingKey;

    }
    private String checkRoamingMMSAutoReceivedState()
    {
    	String roamingMmsKey = "";

        if(mSubscription == 0)
        {
        	roamingMmsKey = Settings.System.DATA_ROAMING_MMS_SUB1;
        	//roamingMmsKey = "data_roaming_mms_sub1";
        }else if(mSubscription == 1)
        {
        	roamingMmsKey = Settings.System.DATA_ROAMING_MMS_SUB2;
        	//roamingMmsKey = "data_roaming_mms_sub2";

        }

    	int roamingMmsEnabled = Settings.System.getInt(resolver, roamingMmsKey, 0);
        logd("roamingMmsEnabled(0 is false, as default) == " + roamingMmsEnabled);
        if(roamingMmsEnabled == 0)
        {
        	mRoamingAutoReceiveMmsEnabler.setChecked(false);
        }else if(roamingMmsEnabled == 1)
        {
        	mRoamingAutoReceiveMmsEnabler.setChecked(true);
        }

        return roamingMmsKey;

    }

}
