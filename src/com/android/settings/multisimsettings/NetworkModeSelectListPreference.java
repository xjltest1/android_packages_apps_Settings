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

package com.android.settings.multisimsettings;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.provider.Settings.Secure;
import android.util.AttributeSet;
import android.util.Log;
import android.app.AlertDialog;
import android.app.Dialog;
import com.android.settings.R;




import com.android.internal.telephony.Phone;
import com.android.internal.telephony.msim.SubscriptionManager;
import com.android.internal.telephony.TelephonyProperties;

public class NetworkModeSelectListPreference extends ListPreference {

    private static final String LOG_TAG = "NetworkModeListPreference";
    private static final boolean DBG = true;

    private int mSubscription = 0;

    public NetworkModeSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

    }

    public NetworkModeSelectListPreference(Context context) {
        this(context, null);
    }

	public void setSubscription(int subscription) {
        mSubscription = subscription;

        String cardType = SystemProperties.get("gsm.sim0.cardmode", "");
        Log.d(LOG_TAG, "cardType : " + cardType);
        boolean doubleModeCard = false;

        if(cardType.equalsIgnoreCase("2") || cardType.equalsIgnoreCase("4") )
        {
        	doubleModeCard = true;
        	Log.d(LOG_TAG, "doubleModeCard : " + String.valueOf(doubleModeCard));
        }

        if(mSubscription == 0)
        {
        	if(doubleModeCard)
        	{
        		setEntries(new String[]{"CDMA", "GSM"});
        		setEntryValues(new String[]{"0", "1"});
        		//we should read previous choice on network mode
        		setValue(Integer.toString(SystemProperties.getInt("persist.radio.networkmode", 0)));

        	}else
        	{
        		setEntries(new String[]{"CDMA"});
        		setEntryValues(new String[]{"0"});
        		setValue(Integer.toString(0));

        	}

        }else
        {
        	setEntries(new String[]{"GSM"});
        	setEntryValues(new String[]{"1"});
        	//setValue(Integer.toString(SystemProperties.getInt("persist.ril.slot1.networkmode", 1)));
            setValue(Integer.toString(1));

        }
    }

    @Override
    protected void showDialog(Bundle state) {
        if (Boolean.parseBoolean(SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
            // In ECM mode do not show selection options
        	Log.d(LOG_TAG, "In ECM mode do not show selection options");
        } else {
            super.showDialog(state);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if(mSubscription != 0)
        {
        	//only SLOT_1 can switch between CDMA and GSM
        	return;
        }

		if (positiveResult && (getValue() != null))
		{
			int requestNetworkMode = Integer.valueOf(getValue()).intValue();
			//retrieve current network mode. 0 for CDMA as default

			//int currentNetworkMode = SystemProperties.getInt("persist.ril.slot1.networkmode", 0);
			int currentNetworkMode = SystemProperties.getInt("persist.radio.networkmode", 0);

			Log.d(LOG_TAG, "requestNetworkMode == " + requestNetworkMode);
			Log.d(LOG_TAG, "currentNetworkMode == " + currentNetworkMode);


			if (requestNetworkMode != currentNetworkMode)
			{
				//save preferred network mode for SLOT_1
				if(requestNetworkMode == 0)
				{

					SystemProperties.set("persist.radio.networkmode", "0");
					//gsm.networkmode.switch used to notify RIL layer to switch
					SystemProperties.set("gsm.networkmode.switch", "0");
				}
				else if(requestNetworkMode == 1)
				{
                    int gsm_flag = SystemProperties.getInt("persist.gsm.enable.switch1", 0);
                    if(gsm_flag == 0)
                    {
//    					String mnc_mcc_0 = android.os.SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC);
//    					String mnc_mcc_1 = android.os.SystemProperties.get(TelephonyProperties.PROPERTY_ICC2_OPERATOR_NUMERIC);
    					String mnc_mcc_0 = "0000";//android.os.SystemProperties.get(TelephonyProperties.PROPERTY_SLOT_OPERATOR_NUMERIC);
    					String mnc_mcc_1 = "0000";//android.os.SystemProperties.get(TelephonyProperties.PROPERTY_SLOT2_OPERATOR_NUMERIC);

    					Log.d(LOG_TAG, "mnc_mcc_0: " + mnc_mcc_0 + " mnc_mcc_1: " + mnc_mcc_1);
    					if(mnc_mcc_0.startsWith("460") || mnc_mcc_0.startsWith("455") || mnc_mcc_1.startsWith("460") || mnc_mcc_1.startsWith("455"))
    					{
    					    setValue(Integer.toString(0));
    						new AlertDialog.Builder(this.getContext()).setTitle(R.string.gsm_setting_forbidden_title)
    							.setIcon(null)
    							.setMessage(R.string.gsm_setting_forbidden_message).setPositiveButton("OK", null)
    							.show();
    						return;
    					}
    					else
    					{
    						SystemProperties.set("persist.radio.networkmode", "1");
    						//gsm.networkmode.switch used to notify RIL layer to switch
    						SystemProperties.set("gsm.networkmode.switch", "1");
    					}
                    }
                    else
                        {
    						SystemProperties.set("persist.radio.networkmode", "1");
    						//gsm.networkmode.switch used to notify RIL layer to switch
    						SystemProperties.set("gsm.networkmode.switch", "1");
                        }
				}
				Log.d(LOG_TAG, "write networkmode");

				SubscriptionManager.networkModeChangedBySettings = true;

			}
		}
		else
		{
			Log.d(LOG_TAG, String.format("onDialogClosed: positiveResult=%b value=%s -- do nothing",
						positiveResult, getValue()));
		}
	}


}
