/*Copyright (c) 2012, Code Aurora Forum. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of Code Aurora Forum, Inc. nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.android.settings;

import com.android.internal.telephony.msim.CardSubscriptionManager;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.msim.MSimPhoneFactory;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.msim.Subscription.SubscriptionStatus;
import com.android.internal.telephony.msim.SubscriptionData;
import com.android.internal.telephony.msim.SubscriptionManager;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.multisimsettings.MultiSimConfiguration;
import com.android.settings.multisimsettings.MultiSimEnabler;

import android.os.SystemProperties;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.provider.Settings.System;
import android.provider.Settings;

public class SimStateReceiver extends BroadcastReceiver {

	ContentResolver resolver = null;
	SubscriptionManager mSubscriptionManager = SubscriptionManager.getInstance();

	@Override
	public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i("SimStateReceiver" , "onReceive " + action);
        resolver = context.getContentResolver();


        if(TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action))
        {
        	int sim_state_subId = intent.getIntExtra(MSimConstants.SUBSCRIPTION_KEY, -1);
        	String sim_state = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
        	if(sim_state_subId == 0)
        	{
        		if(sim_state.equalsIgnoreCase("ABSENT")|| sim_state.equalsIgnoreCase("UNKNOWN"))
        		{
        			Settings.System.putInt(resolver, "card1absent", 1);
        			Log.i("SimStateReceiver", "CARD_1 NO CARD");
        		}else
        		{
        			Settings.System.putInt(resolver, "card1absent", 0);
        			Log.i("SimStateReceiver", "CARD_1 HAS CARD");
        		}
        	}
        	if(sim_state_subId == 1)
        	{
        		if(sim_state.equalsIgnoreCase("ABSENT")|| sim_state.equalsIgnoreCase("UNKNOWN"))
        		{
        			Settings.System.putInt(resolver, "card2absent", 1);
        			Log.i("SimStateReceiver", "CARD_2 NO CARD");
        		}else
        		{
        			Settings.System.putInt(resolver, "card2absent", 0);
        			Log.i("SimStateReceiver", "CARD_2 HAS CARD");
        		}
        	}

        }else if(action.equals("android.intent.settings.DISABLE_GSM_CARD"))
        {
        	if(!mSubscriptionManager.mCardShouldEnable[1])
        	{
        		Log.i("SimStateReceiver", "card 2 already disabled, do nothing");
        		return;
        	}
        	int card2Absent = Settings.System.getInt(resolver, "card2absent", 1);
        	if(card2Absent == 1)
        	{
        		Log.i("SimStateReceiver", "card 2 is ABSENT or NOT_READY, do nothing");
        		return;
        	}

        	Settings.System.putInt(resolver, "card1DisableCard2", 1);

        	//this intent used to disable GSM card when SLOT_0 switched to GSM mode(for CDMA/GSM dual mode card)
        	SubscriptionData subData = new SubscriptionData(2);
            for(int i=0;i<2;i++) {
                subData.subscription[i].copyFrom(mSubscriptionManager.getCurrentSubscription(i));
            }
            subData.subscription[1].slotId = MultiSimEnabler.SUBSCRIPTION_INDEX_INVALID;
            subData.subscription[1].m3gppIndex = MultiSimEnabler.SUBSCRIPTION_INDEX_INVALID;
            subData.subscription[1].m3gpp2Index = MultiSimEnabler.SUBSCRIPTION_INDEX_INVALID;
            subData.subscription[1].subId = 1;
            subData.subscription[1].subStatus = SubscriptionStatus.SUB_DEACTIVATE;

            mSubscriptionManager.mCardShouldEnable[1] = false;

            mSubscriptionManager.setSubscription(subData);
        }else if(action.equals("android.intent.settings.ENABLE_GSM_CARD"))
        {
        	if(mSubscriptionManager.mCardShouldEnable[1])
        	{
        		Log.i("SimStateReceiver", "card 2 already enabled, do nothing");
        		return;
        	}

        	//only when card 2 is disabled by card 1,we should enable it again. at this time, we do not worry about card absence
        	int card1DisableCard2 = Settings.System.getInt(resolver, "card1DisableCard2", 0);
        	if(card1DisableCard2 != 1)
        	{
        		Log.i("SimStateReceiver", "card 2 is not disabled by card 1, so we should not enable it");
        		return;
        	}


        	//this intent used to enable GSM card when SLOT_0 switched back to CDMA mode(for CDMA/GSM dual mode card)
        	mSubscriptionManager.mCardShouldEnable[1] = true;
        	mSubscriptionManager.setRadioPowerOn(1, true);
        	final Phone phone = MSimPhoneFactory.getPhone(1);

        	new Thread(new Runnable() {

				@Override
				public void run() {

					try {
						Thread.sleep(25000);
						Log.i("SimStateReceiver", "sleep for 25 secs");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					//after 25 secs sleep, we should already know card is present or absent, if absent, return
					int card2Absent = Settings.System.getInt(resolver, "card2absent", 1);
		        	if(card2Absent == 1)
		        	{
		        		Log.i("SimStateReceiver", "card 2 is ABSENT or NOT_READY, do nothing");
		        		mSubscriptionManager.mCardShouldEnable[1] = false;
		        		phone.setRadioPower(false);
		        		//we have not enabled card2 because card 2 is absent, so we should restore the flag
//		        		Settings.System.putInt(resolver, "card1DisableCard2", 1);
		        		return;
		        	}

		        	Settings.System.putInt(resolver, "card1DisableCard2", 0);

					Log.i("SimStateReceiver", "go on process subscription activation");
					SubscriptionData subData = new SubscriptionData(2);
					for(int i=0;i<2;i++) {
						subData.subscription[i].copyFrom(mSubscriptionManager.getCurrentSubscription(i));
					}
					subData.subscription[1].slotId = 1;
					subData.subscription[1].subId = 1;
					mSubscriptionManager.setDefaultAppIndex(subData.subscription[1]);
					subData.subscription[1].subStatus = SubscriptionStatus.SUB_ACTIVATE;

					mSubscriptionManager.setSubscription(subData);
				}
			}).start();


        }else if(action.equals("android.intent.action.OPEN_RADIO_FOR_EMERGENCY"))
        {
        	//this intent used to open radio on SLOT_0, in order to dial emergency call. if CDMA card present, should also activate it
        	mSubscriptionManager.mCardShouldEnable[0] = true;
        	mSubscriptionManager.setRadioPowerOn(0, true);

        	new Thread(new Runnable() {

				@Override
				public void run() {

					try {
						Thread.sleep(25000);
						Log.i("SimStateReceiver", "sleep for 25 secs");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					//after 25 secs sleep, we should already know card is present or absent, if absent, return
					int card1Absent = Settings.System.getInt(resolver, "card1absent", 1);
		        	if(card1Absent == 1)
		        	{
		        		Log.i("SimStateReceiver", "card 1 is ABSENT or NOT_READY, stop activation");
		        		mSubscriptionManager.mCardShouldEnable[0] = false;
		        		return;
		        	}

					Log.i("SimStateReceiver", "go on process subscription activation");
					SubscriptionData subData = new SubscriptionData(2);
					for(int i=0;i<2;i++) {
						subData.subscription[i].copyFrom(mSubscriptionManager.getCurrentSubscription(i));
					}
					subData.subscription[0].slotId = 0;
					subData.subscription[0].subId = 0;
					mSubscriptionManager.setDefaultAppIndex(subData.subscription[0]);
					subData.subscription[0].subStatus = SubscriptionStatus.SUB_ACTIVATE;

					mSubscriptionManager.setSubscription(subData);
				}
			}).start();



        }

	}

}
