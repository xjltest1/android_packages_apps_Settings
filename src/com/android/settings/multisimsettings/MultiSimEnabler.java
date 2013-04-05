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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.app.Activity;
import android.content.res.Resources;

import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.os.Message;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.AsyncResult;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.telephony.TelephonyManager;
import android.telephony.MSimTelephonyManager;
import com.android.internal.telephony.msim.SubscriptionManager;
import com.android.internal.telephony.msim.SubscriptionData;
import com.android.internal.telephony.msim.Subscription;
import com.android.internal.telephony.msim.Subscription.SubscriptionStatus;
import com.android.internal.telephony.msim.CardSubscriptionManager;

import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;


/**
 * SimEnabler is a helper to manage the slot on/off checkbox
 * preference. It is turns on/off slot and ensures the summary of the
 * preference reflects the current state.
 */
public class MultiSimEnabler extends CheckBoxPreference implements Preference.OnPreferenceChangeListener{
    private final Context mContext;

    private String LOG_TAG = "MultiSimEnabler";
    private final String INTENT_SIM_DISABLED = "com.android.sim.INTENT_SIM_DISABLED";
    private static final boolean DBG = true; //(PhoneApp.DBG_LEVEL >= 2);
    public static final int SUBSCRIPTION_INDEX_INVALID = 99999;

    private static final int EVENT_SIM_STATE_CHANGED = 1;
    private static final int EVENT_SET_SUBSCRIPTION_DONE = 2;
    private static final int EVENT_SIM_DEACTIVATE_DONE = 3;
    private static final int EVENT_SIM_ACTIVATE_DONE = 4;
    private static final int EVENT_ENABLE_RADIO_WAIT_FINISHED = 5;

    private static long lastChangeState_sub0 = 0;
    private static long lastChangeState_sub1 = 0;

    private final int MAX_SUBSCRIPTIONS = 2;

    private SubscriptionManager mSubscriptionManager;
    private CardSubscriptionManager mCardSubscriptionManager;

    private SubscriptionData[] mCardSubscrInfo;
    private int mSubscriptionId;
    private String mSummary;
    private boolean mState;

    private boolean mRequest;
    private Subscription mSubscription = new Subscription();

    private Activity mForegroundActivity;

    private AlertDialog mErrorDialog = null;
    private AlertDialog mAlertDialog = null;
    private ProgressDialog mProgressDialog = null;
    private ProgressDialog mEnableRadioProgressDialog = null;
    //flag whether it is activating sub
    private boolean mActivateSub;
    private String mDialogString = null;
    private boolean progressDialogIsOn = false;
    private boolean summaryWaitingToUpdate = false;
    private Handler mMultiSimConfigurationHandler;
    private boolean oldCardShouldEnable = false;
    private ContentResolver resolver = null;
	private boolean hasFocus = true;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_SIM_STATE_CHANGED:
                    logd("receive EVENT_SIM_STATE_CHANGED");
                    handleSimStateChanged();
                    break;
                case EVENT_SIM_DEACTIVATE_DONE:
                    logd("receive EVENT_SIM_DEACTIVATE_DONE");
                    mSubscriptionManager.unregisterForSubscriptionDeactivated(mSubscriptionId, this);
                    setEnabled(true);
                    displayAlertDialog();
                    break;
                case EVENT_SIM_ACTIVATE_DONE:
                    logd("receive EVENT_SIM_ACTIVATE_DONE");
                    mSubscriptionManager.unregisterForSubscriptionActivated(mSubscriptionId, this);
                    setEnabled(true);
                    //when activate sub,after completed,it first send EVENT_SET_SUBSCRIPTION_DONE,
                    //and then EVENT_SIM_ACTIVATE_DONE,so we dismiss progressbar and show alert
                    //dialog here.
                    displayAlertDialog();
                    mActivateSub = false;
                    break;
                case EVENT_SET_SUBSCRIPTION_DONE:
                    logd("receive EVENT_SET_SUBSCRIPTION_DONE");
                    String result[] = (String[])((AsyncResult) msg.obj).result;
                    if (result != null) {
                        mDialogString = result[mSubscriptionId];
                    }
                    handleSetSubscriptionDone();
                    // To notify CarrierLabel
                    if (!MultiSimEnabler.this.isChecked() && mForegroundActivity!=null) {
                        logd("Broadcast INTENT_SIM_DISABLED");
                        Intent intent = new Intent(INTENT_SIM_DISABLED);
                        intent.putExtra("Subscription", mSubscriptionId);
                        mForegroundActivity.sendBroadcast(intent);
                    }
                    break;

                case EVENT_ENABLE_RADIO_WAIT_FINISHED:

                	logd("receive EVENT_ENABLE_RADIO_WAIT_FINISHED");

                	if (mEnableRadioProgressDialog != null){
                		mEnableRadioProgressDialog.dismiss();
                		mEnableRadioProgressDialog = null;
                    }

                	//after 20 secs, we should already know the exact card state. here we assume no card inserted
                	int cardAbsent = 1;
                	if(mSubscriptionId == 0)
					{
						cardAbsent = Settings.System.getInt(resolver, "card1absent", 1);
					}else
					{
						cardAbsent = Settings.System.getInt(resolver, "card2absent", 1);
					}

                	if(cardAbsent == 1)
					{
                		logd("card is ABSENT or NOT_READY, do nothing : Sub == " + mSubscriptionId);
						if(progressDialogIsOn)
						{
							if (mProgressDialog != null) {
					            mProgressDialog.dismiss();
					            mProgressDialog = null;
					            progressDialogIsOn = false;
					        }

					        if (mEnableRadioProgressDialog != null) {
					        	mEnableRadioProgressDialog.dismiss();
					        	mEnableRadioProgressDialog = null;
					        	progressDialogIsOn = false;
					        }

					        displayAlertDialog(mContext.getString(R.string.no_card));
					        updateSummary();
					        setEnabled(true);

					        mSubscriptionManager.setRadioPowerOn(mSubscriptionId, false);

						}

						return;
					}
                	//if no card, confirm dialog's ondismiss listener will revert the CardShouldEnable state
                	mSubscriptionManager.mCardShouldEnable[mSubscriptionId] = true;

                	sendCommand(true);


                default:
                    break;
            }
        }
    };

    private void handleSimStateChanged() {
        logd("EVENT_SIM_STATE_CHANGED");
        mSubscription = new Subscription();
        SubscriptionData[] cardSubsInfo = new SubscriptionData[MAX_SUBSCRIPTIONS];
        for(SubscriptionData cardSub : cardSubsInfo) {
            if (cardSub != null) {
                for (int i = 0; i < cardSub.getLength(); i++) {
                    Subscription sub = cardSub.subscription[i];
                    if (sub.subId == mSubscriptionId) {
                        mSubscription.copyFrom(sub);
                        break;
                    }
                }
            }
        }
        if (mSubscription.subStatus == SubscriptionStatus.SUB_ACTIVATED
            || mSubscription.subStatus == SubscriptionStatus.SUB_DEACTIVATED) {
            setEnabled(true);
        }
    }

    private void handleSetSubscriptionDone() {
        //set subscription is done, can set check state and summary at here
        updateSummary();

        mSubscription.copyFrom(mSubscriptionManager.getCurrentSubscription(mSubscriptionId));
        //if it is activating sub,we can dismiss progressbar and show alert dialog.Otherwise
        //do this when EVENT_SIM_ACTIVATE_DONE received.
        if (!mActivateSub) {
            displayAlertDialog();
        }
		else{
             if (mDialogString != null) mDialogString = null;
		}
    }

    private void displayAlertDialog() {
        logd("displayAlertDialog");
        String alertMsg = null;
        if(mDialogString != null)
        {
        	alertMsg = resultToMsg(mDialogString);
        	if(alertMsg == null)
        	{
        		return;
        	}

        }

        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
            progressDialogIsOn = false;
        }

        if (mEnableRadioProgressDialog != null) {
        	mEnableRadioProgressDialog.dismiss();
        	mEnableRadioProgressDialog = null;
        	progressDialogIsOn = false;
        }

        updateSummary();

        if (mDialogString != null) {
        	mMultiSimConfigurationHandler.sendEmptyMessage(MultiSimConfiguration.REMOVE_DIALOG_NETWORK_SELECTION);

            displayAlertDialog(alertMsg);
			//Modify for displaying this alert dialog only once when disable or active the card--start
			mDialogString = null;
			//Modify for displaying this alert dialog only once when disable or active the card--end
        }
    }

    private String resultToMsg(String result){
        if(result.equals(SubscriptionManager.SUB_ACTIVATE_SUCCESS)){
            return mContext.getString(R.string.sub_activate_success);
        }
        if (result.equals(SubscriptionManager.SUB_ACTIVATE_FAILED)){
            return mContext.getString(R.string.sub_activate_failed);
        }
        if (result.equals(SubscriptionManager.SUB_DEACTIVATE_SUCCESS)){
            return mContext.getString(R.string.sub_deactivate_success);
        }
        if (result.equals(SubscriptionManager.SUB_DEACTIVATE_FAILED)){
            return mContext.getString(R.string.sub_deactivate_failed);
        }
        if (result.equals(SubscriptionManager.SUB_DEACTIVATE_NOT_SUPPORTED)){
            return mContext.getString(R.string.sub_deactivate_not_supported);
        }
        if (result.equals(SubscriptionManager.SUB_ACTIVATE_NOT_SUPPORTED)){
            return mContext.getString(R.string.sub_activate_not_supported);
        }
        if (result.equals(SubscriptionManager.SUB_NOT_CHANGED)){
//            return mContext.getString(R.string.sub_not_changed);
            return null;
        }
//        return mContext.getString(R.string.sub_not_changed);
        return null;
    }

    public MultiSimEnabler(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mSubscriptionManager = SubscriptionManager.getInstance();
        mCardSubscriptionManager = CardSubscriptionManager.getInstance();
        resolver = mContext.getContentResolver();

    }

    public MultiSimEnabler(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.checkBoxPreferenceStyle);
    }

    public MultiSimEnabler(Context context) {
        this(context, null);
    }

    public void setSubscription(Activity activity, int subscription, Handler mHandler) {
        mSubscriptionId = subscription;
        mMultiSimConfigurationHandler = mHandler;

        String alpha = ((MSimTelephonyManager) mContext.getSystemService(Context.MSIM_TELEPHONY_SERVICE))
                .getSimOperatorName(subscription);
        if (alpha != null && !"".equals(alpha))
            setTitle(alpha);

        mForegroundActivity = activity;
        if (mForegroundActivity == null) logd("error! mForegroundActivity is null!");

        if (getCardSubscriptions() == null){
            logd("card info is not available.");
            setEnabled(false);
        }else{
            mSubscription.copyFrom(mSubscriptionManager.getCurrentSubscription(mSubscriptionId));
            logd("sub status " + mSubscription.subStatus);
            if (mSubscription.subStatus == SubscriptionStatus.SUB_ACTIVATED
                || mSubscription.subStatus == SubscriptionStatus.SUB_DEACTIVATED) {
                setEnabled(true);
            } else {
                setEnabled(false);
            }
        }

    }

    public void resume() {
		hasFocus = true;
        setOnPreferenceChangeListener(this);

        updateSummary();

        /*mSubscriptionManager.registerForSimStateChanged(mHandler, EVENT_SIM_STATE_CHANGED, null);*/
        mSubscriptionManager.registerForSetSubscriptionCompleted(mHandler, EVENT_SET_SUBSCRIPTION_DONE, null);
    }

    public void pause() {
		hasFocus = false;

        setOnPreferenceChangeListener(null);

        //dismiss all dialogs: alert and progress dialogs
        if (mAlertDialog != null) {
            logd("pause: dismiss alert dialog");
            mAlertDialog.dismiss();
            mAlertDialog = null;
            mDialogString = null;
        }

        if (mErrorDialog != null) {
            logd("pause: dismiss error dialog");
            mErrorDialog.dismiss();
            mErrorDialog = null;
        }

        if (mProgressDialog != null){
            logd("pause: dismiss progress dialog");
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        if (mEnableRadioProgressDialog != null){
        	logd("pause: dismiss enable radio progress dialog");
    		mEnableRadioProgressDialog.dismiss();
    		mEnableRadioProgressDialog = null;
        }



        progressDialogIsOn = false;

        mSubscriptionManager.unRegisterForSetSubscriptionCompleted(mHandler);
        /*mSubscriptionManager.unRegisterForSimStateChanged(mHandler);*/
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        mRequest = ((Boolean)value).booleanValue();

        logd("PreferenceChange on [mSubscriptionId]: " + mRequest);
        oldCardShouldEnable = mSubscriptionManager.mCardShouldEnable[mSubscriptionId];
        mSubscriptionManager.mCardShouldEnable[mSubscriptionId] = mRequest;

        displayConfirmDialog();

        // Don't update UI to opposite state until we're sure
        return false;
    }

    public void updateSimEnablerPreference() {
        //need to update card sub info
        for(int i=0; i<MAX_SUBSCRIPTIONS; i++) {
            mCardSubscrInfo[i] = mCardSubscriptionManager.getCardSubscriptions(i);
        }

        updateSummary();
    }

    private void displayConfirmDialog() {
        if (mForegroundActivity == null){
            logd("can not display alert dialog,no foreground activity");
            return;
        }
        String message = mContext.getString(mRequest?R.string.sim_enabler_need_enable_sim:R.string.sim_enabler_need_disable_sim);
        // Need an activity context to show a dialog
        mAlertDialog = new AlertDialog.Builder(mForegroundActivity)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, mDialogClickListener)
                .setNegativeButton(android.R.string.no, mDialogClickListener)
                .setOnCancelListener(new OnCancelListener() {

					@Override
					public void onCancel(DialogInterface dialog) {
						 if (DBG) logd("confirm dialog Cancel, revert CardShouldEnable state to oldCardShouldEnable(" + String.valueOf(oldCardShouldEnable) +")");
			                mSubscriptionManager.mCardShouldEnable[mSubscriptionId] = oldCardShouldEnable;
					}
				})
                .show();

        mAlertDialog.setOnDismissListener(new OnDismissListener() {

			@Override
			public void onDismiss(DialogInterface dialog) {
				if (DBG) logd("confirm dialog Dismiss, revert CardShouldEnable state to oldCardShouldEnable(" + String.valueOf(oldCardShouldEnable) +")");
                mSubscriptionManager.mCardShouldEnable[mSubscriptionId] = oldCardShouldEnable;
                //when click positive button, dismiss callback also be invoked. so we set CardShouldEnable again in DialogClickListener
			}
		});

    }


    private DialogInterface.OnClickListener mDialogClickListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                logd("onClick: " + mRequest);

                if(mSubscriptionId == 0)
                {
                	if(lastChangeState_sub0 != 0)
                	{
                		if((System.currentTimeMillis()) - lastChangeState_sub0 < 60000)
                		{
                			if (DBG) logd("Time interval between card operations is less than 60 seconds");
                			displayAlertDialog(mContext.getString(R.string.operation_on_card_toofast));
                			return;
                		}

                	}
                	lastChangeState_sub0 = System.currentTimeMillis();

                }else if(mSubscriptionId == 1)
                {
                	if(lastChangeState_sub1 != 0)
                	{
                		if((System.currentTimeMillis()) - lastChangeState_sub1 < 60000)
                		{
                			if (DBG) logd("Time interval between card operations is less than 60 seconds");
                			displayAlertDialog(mContext.getString(R.string.operation_on_card_toofast));
                			return;
                		}

                	}
                	lastChangeState_sub1 = System.currentTimeMillis();

                }



                int currentNetworkMode = SystemProperties.getInt("persist.radio.networkmode", 0);
                int gsmNetworkModeSwitch = SystemProperties.getInt("gsm.networkmode.switch", 0);
                //if CDMA card is under GSM mode, gsm.networkmode.switch must be 1. otherwise it will be 0 or unset
                //to check gsm.networkmode.switch will avoid following condition:
                //CDMA card under GSM mode, shutdown, remove CDMA card, boot, GSM card can't be enabled because flag indicats that CDMA card under GSM mode
                if(currentNetworkMode == 1 && gsmNetworkModeSwitch == 1)
                {
                	if (DBG) logd("card_0 is under CDMA mode, operation on card_1 is not allowed");
                    displayAlertDialog(mContext.getString(R.string.operation_not_allowed));
                    return;
                }

                if (Settings.System.getInt(mContext.getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0) {
                    // do nothing but warning
                    logd("airplane is on, show error!");
                    displayAlertDialog(mContext.getString(R.string.sim_enabler_airplane_on));
                    return;
                }

                for (int i=0; i<MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                    if (MSimTelephonyManager.getDefault().getCallState(i) != TelephonyManager.CALL_STATE_IDLE) {
                        // do nothing but warning
                        if (DBG) logd("call state " + i + " is not idle, show error!");
                        displayAlertDialog(mContext.getString(R.string.sim_enabler_in_call));
                        return;
                    }
                }

                if (!mRequest){
					//can disable two sim card
                    if (mSubscriptionManager.getActiveSubscriptionsCount() >= 1){
                    	oldCardShouldEnable = false; //sometimes confirm dialog's ondismiss listener is late, so we should ensure correct state
                        if(DBG) logd("disable, both are active,can do");
                        setEnabled(false);

                        mSubscriptionManager.mCardShouldEnable[mSubscriptionId] = false;
                        sendCommand(mRequest);
                    }
					/*else{
                        if (DBG) logd("only one is active,can not do");
                        displayAlertDialog(mContext.getString(R.string.sim_enabler_both_inactive));
                        return;
                    }*/
                }else{
                    if (DBG) logd("enable, do it");

                    //we assume card inserted, then we can check card state. and for second time, we can return immediately without check
                    int cardAbsent = 0;
                	if(mSubscriptionId == 0)
					{
						cardAbsent = Settings.System.getInt(resolver, "card1absent", 0);
					}else
					{
						cardAbsent = Settings.System.getInt(resolver, "card2absent", 0);
					}
                	if(cardAbsent == 1)
                	{
                		displayAlertDialog(mContext.getString(R.string.no_card));
                		return;
                	}

                    setEnabled(false);
                    mActivateSub = true;
                    mSubscriptionManager.setRadioPowerOn(mSubscriptionId, true);
                    displayEnableRadioProgressDialog();
                    mHandler.sendEmptyMessageDelayed(EVENT_ENABLE_RADIO_WAIT_FINISHED, 25000);
                    //command below will be done in EVENT_ENABLE_RADIO_WAIT_FINISHED
                    //sendCommand(mRequest);
                }
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                if (DBG) logd("onClick Cancel, revert checkbox status");
                if (DBG) logd("onClick Cancel, revert CardShouldEnable state to oldCardShouldEnable(" + String.valueOf(oldCardShouldEnable) +")");
                mSubscriptionManager.mCardShouldEnable[mSubscriptionId] = oldCardShouldEnable;
            }
        }
    };

    private void displayEnableRadioProgressDialog(){
        String title = "card"+mSubscriptionId;//Settings.System.getString(mContext.getContentResolver(),Settings.System.MULTI_SIM_NAME[mSubscriptionId]);
        mEnableRadioProgressDialog = new ProgressDialog(mForegroundActivity);
        mEnableRadioProgressDialog.setIndeterminate(true);
        mEnableRadioProgressDialog.setTitle(title);
        mEnableRadioProgressDialog.setMessage(mContext.getString(R.string.enable_radio_power));
        mEnableRadioProgressDialog.setCancelable(false);
        mEnableRadioProgressDialog.setOnDismissListener(new OnDismissListener() {

			@Override
			public void onDismiss(DialogInterface dialog) {
				progressDialogIsOn = false;

			}
		});
        mEnableRadioProgressDialog.show();

        progressDialogIsOn = true;
    }



    private void sendCommand(boolean enabled){
        SubscriptionData subData = new SubscriptionData(MAX_SUBSCRIPTIONS);
        for(int i=0;i<MAX_SUBSCRIPTIONS;i++) {
            subData.subscription[i].copyFrom(mSubscriptionManager.getCurrentSubscription(i));
        }
        if (enabled){
            subData.subscription[mSubscriptionId].slotId = mSubscriptionId;
            subData.subscription[mSubscriptionId].subId = mSubscriptionId;
            mSubscriptionManager.setDefaultAppIndex(subData.subscription[mSubscriptionId]);
            subData.subscription[mSubscriptionId].subStatus = SubscriptionStatus.SUB_ACTIVATE;
            mSubscriptionManager.registerForSubscriptionActivated(
                mSubscriptionId, mHandler, EVENT_SIM_ACTIVATE_DONE, null);
        }else{
            subData.subscription[mSubscriptionId].slotId = SUBSCRIPTION_INDEX_INVALID;
            subData.subscription[mSubscriptionId].m3gppIndex = SUBSCRIPTION_INDEX_INVALID;
            subData.subscription[mSubscriptionId].m3gpp2Index = SUBSCRIPTION_INDEX_INVALID;
            subData.subscription[mSubscriptionId].subId = mSubscriptionId;
            subData.subscription[mSubscriptionId].subStatus = SubscriptionStatus.SUB_DEACTIVATE;
            mSubscriptionManager.registerForSubscriptionDeactivated(
                mSubscriptionId, mHandler, EVENT_SIM_DEACTIVATE_DONE, null);
        }
		if(hasFocus)
		{
			displayProgressDialog(enabled);
		}

        mSubscriptionManager.setSubscription(subData);
    }

    private void displayProgressDialog(boolean enabled){
        String title = "card"+mSubscriptionId;//Settings.System.getString(mContext.getContentResolver(),Settings.System.MULTI_SIM_NAME[mSubscriptionId]);
        String msg = mContext.getString(enabled?R.string.sim_enabler_enabling:R.string.sim_enabler_disabling);
        mProgressDialog = new ProgressDialog(mForegroundActivity);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setTitle(title);
        mProgressDialog.setMessage(msg);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setOnDismissListener(new OnDismissListener() {

			@Override
			public void onDismiss(DialogInterface dialog) {
				progressDialogIsOn = false;

			}
		});
        mProgressDialog.show();

        progressDialogIsOn = true;
    }

    private void displayAlertDialog(String msg) {
        mErrorDialog = new AlertDialog.Builder(mForegroundActivity)
             .setTitle(android.R.string.dialog_alert_title)
             .setMessage(msg)
             .setCancelable(false)
             .setNeutralButton(R.string.close_dialog, null)
             .show();
    }

    private void updateSummary() {
        Resources res = mContext.getResources();
        boolean isActivated = mSubscriptionManager.isSubActive(mSubscriptionId);
        if (isActivated) {
            mState = true;
            mSummary = String.format(res.getString(R.string.sim_enabler_summary), res.getString(R.string.sim_enabled));
        } else {
            mState = false;
//            mSummary = String.format(res.getString(R.string.sim_enabler_summary), res.getString(mCardSubscrInfo[mSubscriptionId] != null  ?
//                R.string.sim_disabled :R.string.sim_missing));

            mSummary = String.format(res.getString(R.string.sim_enabler_summary), res.getString(R.string.sim_disabled));
        }

        logd("progressDialogIsOn: " + progressDialogIsOn);
        if(!progressDialogIsOn)
        {
        	setSummary(mSummary);
        	setChecked(mState);
        }

        logd("updateSummary on [mSubscriptionId]: " + mState);
        //mSubscriptionManager.mCardEnabled[mSubscriptionId] = mState;
    }

    private void logd(String msg) {
        Log.d(LOG_TAG + "(" + mSubscriptionId + ")", msg);
    }

    private SubscriptionData[] getCardSubscriptions() {
        mCardSubscrInfo = new SubscriptionData[MAX_SUBSCRIPTIONS];
        for(int i=0; i<MAX_SUBSCRIPTIONS; i++) {
            mCardSubscrInfo[i] = mCardSubscriptionManager.getCardSubscriptions(i);
        }
        return mCardSubscrInfo;
    }

}
