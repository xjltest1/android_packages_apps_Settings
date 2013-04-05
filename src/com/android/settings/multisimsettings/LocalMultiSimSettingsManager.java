/*
 *
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RegistrantList;
import android.os.Registrant;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.PreferenceActivity;

import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.telephony.MSimTelephonyManager;
import android.widget.Toast;
import android.util.Config;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.msim.SubscriptionManager;
import com.android.internal.telephony.msim.MSimPhoneFactory;
import com.android.internal.telephony.msim.SubscriptionData;
import com.android.internal.telephony.msim.Subscription;
import com.android.internal.telephony.msim.Subscription.SubscriptionStatus;
import com.android.internal.telephony.msim.MSimPhoneFactory;

import com.android.settings.R;


public class LocalMultiSimSettingsManager implements DialogInterface.OnClickListener {
    private static final String TAG = "LocalMultiSimSettingsManager";
    private static final boolean DBG = true;

    private final String SHARED_PREFERENCES_NAME = "dual_sim_settings";

    private static LocalMultiSimSettingsManager INSTANCE;
    /** Used when obtaining a reference to the singleton instance. */
    private static Object INSTANCE_LOCK = new Object();
    private boolean mInitialized;

    private Context mContext;

    private Activity mForegroundActivity;

    private ProgressDialog mProgressDialog = null;

    /** Used for sim names */
    private RegistrantList mMultiSimNamesRegistrants = new RegistrantList();
    private String[] mSimNames;

    /** Used for preferred data subscription settings */
    private static Message mPendingSetDdsMessage = null;

    private static final int DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS = 100;

    static final int EVENT_SET_DATA_SUBSCRIPTION_DONE = 1;

    public static LocalMultiSimSettingsManager getInstance(Context context) {
        synchronized (INSTANCE_LOCK) {
            if (INSTANCE == null) {
                INSTANCE = new LocalMultiSimSettingsManager();
            }

            if (!INSTANCE.init(context)) {
                return null;
            }

            return INSTANCE;
        }
    }

    private boolean init(Context context) {
        if (mInitialized) return true;
        mInitialized = true;

        // This will be around as long as this process is
        mContext = context.getApplicationContext();

        int count = MSimTelephonyManager.getDefault().getPhoneCount();
        mSimNames = new String[count];

        for (int i = 0; i < count; i++) {
            mSimNames[i] = getMultiSimName(i);
            if( (i == 0) && (mSimNames[i] == null)) {
                setMultiSimName("SLOT1", 0);
                mSimNames[i] = "SLOT1";
            } else if( (i == 1) && (mSimNames[i] == null)){
                setMultiSimName("SLOT2", 1);
                mSimNames[i] = "SLOT2";
            }
        }
        return true;
    }

    public Context getContext() {
        return mContext;
    }

    public Activity getForegroundActivity() {
        return mForegroundActivity;
    }

    public void setForegroundActivity(Activity activity) {
        mForegroundActivity = activity;
    }

    public SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    /*** For channel name process  **/
    public void registerForSimNameChange(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (INSTANCE_LOCK) {
            mMultiSimNamesRegistrants.add(r);
        }
    }
    public void unregisterForSimNameChange(Handler h) {
        synchronized (INSTANCE_LOCK) {
            mMultiSimNamesRegistrants.remove(h);
        }
    }

    public String getMultiSimName(int subscription) {
	return "card"+subscription;
    //return Settings.System.getString(mContext.getContentResolver(),
    //    Settings.System.MULTI_SIM_NAME[subscription]);
    }

    public void setMultiSimName(String simName, int subscription) {
        /*if (!mSimNames[subscription].equals(simName))*/ {
            mSimNames[subscription] = simName;
            Settings.System.putString(mContext.getContentResolver(),
		 "card"+subscription, simName);
         //Settings.System.MULTI_SIM_NAME[subscription], simName);
        mMultiSimNamesRegistrants.notifyRegistrants(new AsyncResult(null, subscription, null));
        }
    }

    public String[] getMultiSimNames() {
        return mSimNames;
    }

    /*** For preferred subscription process on Voice, Sms, Data ***/
    public int getPreferredSubscription(int listType) {
         int preferredSubscription = 0;
         switch (listType) {
             case MultiSimSettingsConstants.VOICE_SUBSCRIPTION_LIST:
                preferredSubscription = MSimPhoneFactory.getVoiceSubscription();
                break;
             case MultiSimSettingsConstants.SMS_SUBSCRIPTION_LIST:
                preferredSubscription = MSimPhoneFactory.getSMSSubscription();
                break;
             case MultiSimSettingsConstants.DATA_SUBSCRIPTION_LIST:
                preferredSubscription = MSimPhoneFactory.getDataSubscription();
                break;
         }
         return preferredSubscription;
    }

    public void setPreferredSubscription(Message msg) {
        switch (msg.arg1) {
            case MultiSimSettingsConstants.VOICE_SUBSCRIPTION_LIST:
                MSimPhoneFactory.setVoiceSubscription(msg.arg2);
                msg.sendToTarget();
                break;
            case MultiSimSettingsConstants.SMS_SUBSCRIPTION_LIST:
                MSimPhoneFactory.setSMSSubscription(msg.arg2);
                msg.sendToTarget();
                break;
            case MultiSimSettingsConstants.DATA_SUBSCRIPTION_LIST:
                mPendingSetDdsMessage = msg;
                showDialog(DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS);
                SubscriptionManager subManager = SubscriptionManager.getInstance();
                Message setDdsMsg = Message.obtain(mHandler, EVENT_SET_DATA_SUBSCRIPTION_DONE, msg.arg2,0);
                subManager.setDataSubscription(msg.arg2, setDdsMsg);
                break;
            default:
                Log.e(TAG, "Not avaliable list type can be processed");
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch(msg.what) {
                case EVENT_SET_DATA_SUBSCRIPTION_DONE:
                    Log.d(TAG, "EVENT_SET_DATA_SUBSCRIPTION_DONE");
                    dismissDialog(DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS);
                    ((PreferenceActivity)getForegroundActivity()).getPreferenceScreen().setEnabled(true);

                    if (mPendingSetDdsMessage != null) {
                        mPendingSetDdsMessage.sendToTarget();
                        mPendingSetDdsMessage = null;
                    }

                    ar = (AsyncResult) msg.obj;

                    String status;

                    if (ar.exception != null) {
                        // This should never happens.  But display an alert message in case.
                        status = mContext.getResources().getString(R.string.set_dds_failed);
                        displayAlertDialog(status);
                        break;
                    }

                    //final ProxyManager.SetDdsResult result = (ProxyManager.SetDdsResult)ar.result;
                    boolean result = (Boolean)ar.result;

                    Log.d(TAG, "SET_DATA_SUBSCRIPTION_DONE: result = " + result);

                    if (result == true/*ProxyManager.SetDdsResult.SUCCESS*/) {
                        MSimPhoneFactory.setDataSubscription(msg.arg1);
                        status = mContext.getResources().getString(R.string.set_dds_success);
                        Toast toast = Toast.makeText(getForegroundActivity().getApplicationContext(), status, Toast.LENGTH_LONG);
                        toast.show();
                    } else {
                        status = mContext.getResources().getString(R.string.set_dds_failed);
                        displayAlertDialog(status);
                    }

                    break;
            }
        }
    };

    private void showDialog(int id) {
        if (id == DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS) {
			if (mProgressDialog != null){
	            mProgressDialog.hide();
	            mProgressDialog = null;
	        }
            mProgressDialog = new ProgressDialog(mForegroundActivity);

            mProgressDialog.setMessage(mContext.getResources().getString(R.string.set_data_subscription_progress));
            mProgressDialog.setCancelable(false);
            mProgressDialog.setIndeterminate(true);

            ((PreferenceActivity)getForegroundActivity()).getPreferenceScreen().setEnabled(false);

            mProgressDialog.show();
        }
    }

    private void dismissDialog(int id) {
        if (mProgressDialog != null){
            mProgressDialog.hide();
            mProgressDialog = null;
        }
    }

    protected void dismissDialogForPause() {
        if (mProgressDialog != null){
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    void displayAlertDialog(String msg) {
        Log.d(TAG, "displayErrorDialog!" + msg);
        new AlertDialog.Builder(mForegroundActivity).setMessage(msg)
               .setTitle(android.R.string.dialog_alert_title)
               .setIcon(android.R.drawable.ic_dialog_alert)
               .setPositiveButton(android.R.string.yes, this)
               .show();
    }

    // This is a method implemented for DialogInterface.OnClickListener.
    public void onClick(DialogInterface dialog, int which) {
        Log.d(TAG, "onClick!");
    }

    public boolean getSubscriptionStatus(int subscription) {
        Log.d(TAG, "getSubStatus on sub" + subscription);

        return SubscriptionManager.getInstance().isSubActive(subscription);
    }

}

