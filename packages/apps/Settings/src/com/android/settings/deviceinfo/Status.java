/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.Toast;
import android.telephony.TelephonyManager;

import com.android.internal.util.ArrayUtils;
import com.android.settings.R;
import com.android.settings.Utils;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.ISettingsMiscExt;

import java.lang.ref.WeakReference;
import android.util.Log;

/**
 * Display the following information
 * # Battery Strength  : TODO
 * # Uptime
 * # Awake Time
 * # XMPP/buzz/tickle status : TODO
 *
 */
public class Status extends PreferenceActivity {

    private static final String KEY_BATTERY_STATUS = "battery_status";
    private static final String KEY_BATTERY_LEVEL = "battery_level";
    private static final String KEY_IP_ADDRESS = "wifi_ip_address";
    private static final String KEY_WIFI_MAC_ADDRESS = "wifi_mac_address";
    private static final String KEY_BT_ADDRESS = "bt_address";
    private static final String KEY_SERIAL_NUMBER = "serial_number";
    private static final String KEY_WIMAX_MAC_ADDRESS = "wimax_mac_address";
    private static final String KEY_SIM_STATUS = "sim_status";
    private static final String KEY_IMEI_INFO = "imei_info";
    private static final String KEY_IMSI_INFO = "imsi_info";
    private static final String KEY_ICCID_NUMBER = "iccid_number";


    // Broadcasts to listen to for connectivity changes.
    private static final String[] CONNECTIVITY_INTENTS = {
            BluetoothAdapter.ACTION_STATE_CHANGED,
            ConnectivityManager.CONNECTIVITY_ACTION_IMMEDIATE,
            WifiManager.LINK_CONFIGURATION_CHANGED_ACTION,
            WifiManager.NETWORK_STATE_CHANGED_ACTION,
    };

    private static final int EVENT_UPDATE_STATS = 500;

    private static final int EVENT_UPDATE_CONNECTIVITY = 600;

    private ConnectivityManager mCM;
    private WifiManager mWifiManager;

    private Resources mRes;

    private String mUnknown;
    private String mUnavailable;
    /* HQ_daiwenqiang 2015-12-17 modified for HQ01576916 begin */
    private String simCardOne;
    private String simCardTwo;
    /* HQ_daiwenqiang 2015-12-17 modified for HQ01576916 end */
    private Preference mUptime;
    private Preference mBatteryStatus;
    private Preference mBatteryLevel;
    private Preference mBtAddress;
    private Preference mIpAddress;
    private Preference mWifiMacAddress;
    private Preference mWimaxMacAddress;
	private Preference mIccidNumber;
	
    private ISettingsMiscExt mExt;

    private Handler mHandler;

    private static class MyHandler extends Handler {
        private WeakReference<Status> mStatus;

        public MyHandler(Status activity) {
            mStatus = new WeakReference<Status>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            Status status = mStatus.get();
            if (status == null) {
                return;
            }

            switch (msg.what) {
                case EVENT_UPDATE_STATS:
                    status.updateTimes();
                    sendEmptyMessageDelayed(EVENT_UPDATE_STATS, 1000);
                    break;

                case EVENT_UPDATE_CONNECTIVITY:
                    status.updateConnectivity();
                    break;
            }
        }
    }

    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel.setSummary(Utils.getBatteryPercentage(intent));
                mBatteryStatus.setSummary(Utils.getBatteryStatus(getResources(), intent));
            }
        }
    };

    private IntentFilter mConnectivityIntentFilter;
    private final BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ArrayUtils.contains(CONNECTIVITY_INTENTS, action)) {
                mHandler.sendEmptyMessage(EVENT_UPDATE_CONNECTIVITY);
            }
        }
    };

    private boolean hasBluetooth() {
        return BluetoothAdapter.getDefaultAdapter() != null;
    }

    private boolean hasWimax() {
        return  mCM.getNetworkInfo(ConnectivityManager.TYPE_WIMAX) != null;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mExt = UtilsExt.getMiscPlugin(this);

        mHandler = new MyHandler(this);

        mCM = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        addPreferencesFromResource(R.xml.device_info_status);
        mBatteryLevel = findPreference(KEY_BATTERY_LEVEL);
        mBatteryStatus = findPreference(KEY_BATTERY_STATUS);
        mBtAddress = findPreference(KEY_BT_ADDRESS);
        mWifiMacAddress = findPreference(KEY_WIFI_MAC_ADDRESS);
        mWimaxMacAddress = findPreference(KEY_WIMAX_MAC_ADDRESS);
        mIpAddress = findPreference(KEY_IP_ADDRESS);
		mIccidNumber = findPreference(KEY_ICCID_NUMBER);
		
		String sim1IccId_1 = SystemProperties.get("ril.iccid.sim1");
		String sim1IccId_2 = SystemProperties.get("ril.iccid.sim2");
	        //add by wangwenjia for HQ01571126
                String doubleSimCards = SystemProperties.get("ro.mtk_gemini_support");

		/* HQ_daiwenqiang 2015-12-17 modified for HQ01576916 begin */
		mRes = getResources();
		simCardOne = mRes.getString(R.string.iccid_number_card_one);
		simCardTwo = mRes.getString(R.string.iccid_number_card_two);
                
                //modify by wangwenjia for HQ01571126
		if ((sim1IccId_1 != null && (!sim1IccId_1.equals("N/A")))||(sim1IccId_2 != null && (!sim1IccId_2.equals("N/A")))) {
			if(!sim1IccId_1.equals("N/A") && !sim1IccId_2.equals("N/A") && doubleSimCards.equals("1")){
				mIccidNumber.setSummary(simCardOne + sim1IccId_1 + simCardTwo + sim1IccId_2);
			}else if(!sim1IccId_1.equals("N/A")){
				mIccidNumber.setSummary(sim1IccId_1);
			}else if(!sim1IccId_2.equals("N/A")){
				mIccidNumber.setSummary(sim1IccId_2);
			}
        }
        /* HQ_daiwenqiang 2015-12-17 modified for HQ01576916 end */
        ///M: feature replace sim to uim
        /* HQ_zhangpeng5 2015-10-13 modified for no use MTK replace HQ01389202 begin */
        //changeSimTitle();
        /* HQ_zhangpeng5 2015-10-13 modified for no use MTK replace HQ01389202 end */

        //mRes = getResources();
        mUnknown = mRes.getString(R.string.device_info_default);
        mUnavailable = mRes.getString(R.string.status_unavailable);

        // Note - missing in zaku build, be careful later...
        mUptime = findPreference("up_time");

        if (!SystemProperties.get("ro.hq.display.iccid").equals("1")) {
            getPreferenceScreen().removePreference(mIccidNumber);
            mIccidNumber = null;			
        }

        if (!hasBluetooth()) {
            getPreferenceScreen().removePreference(mBtAddress);
            mBtAddress = null;
        }

        if (!hasWimax()) {
            getPreferenceScreen().removePreference(mWimaxMacAddress);
            mWimaxMacAddress = null;
        }

        mConnectivityIntentFilter = new IntentFilter();
        for (String intent: CONNECTIVITY_INTENTS) {
             mConnectivityIntentFilter.addAction(intent);
        }

        updateConnectivity();

        // hanchao add HQ01526607 begin
        if (SystemProperties.get("ro.imsi.enable").equals("1")){
            TelephonyManager mTelephonyMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String imsi = mTelephonyMgr.getSubscriberId();
            setSummaryText(KEY_IMSI_INFO, imsi);
        } else {
            getPreferenceScreen().removePreference(findPreference(KEY_IMSI_INFO));
        }
        // hanchao add HQ01526607 end
        //HQ_wuhuihui_20150706 modified for serial number start
        String snstr = SystemProperties.get("gsm.serial");
        Log.d("nnnnnnnnnn", "snstr = " + snstr);
        String serial = "";
        try {
            if (snstr.length() >= 16) {//HQ_liugang modify for the shortest length of SN is 16
                serial = snstr.substring(0, 16);   //HQ01270787 hanchao change  2015.9.23
            }
         } catch (Exception e) {
            Log.d("nnnnnnnnnn", "exception");
         }
         Log.d("nnnnnnnnnn", "sn:" + serial);
        //HQ_wuhuihui_20150706 modified for serial number end
        if (serial != null && !serial.trim().equals("")) {
            setSummaryText(KEY_SERIAL_NUMBER, serial);
        } else {
            //removePreferenceFromScreen(KEY_SERIAL_NUMBER);
        	setSummaryText(KEY_SERIAL_NUMBER, Build.SERIAL);   //HQ01270787 hanchao change  2015.9.23
        }

        //Remove SimStatus and Imei for Secondary user as it access Phone b/19165700
        if (Utils.isWifiOnly(this) || UserHandle.myUserId() != UserHandle.USER_OWNER) {
            removePreferenceFromScreen(KEY_SIM_STATUS);
            removePreferenceFromScreen(KEY_IMEI_INFO);
        }

        // Make every pref on this screen copy its data to the clipboard on longpress.
        // Super convenient for capturing the IMEI, MAC addr, serial, etc.
        getListView().setOnItemLongClickListener(
            new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view,
                        int position, long id) {
                    ListAdapter listAdapter = (ListAdapter) parent.getAdapter();
                    Preference pref = (Preference) listAdapter.getItem(position);

                    ClipboardManager cm = (ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setText(pref.getSummary());
                    Toast.makeText(
                        Status.this,
                        com.android.internal.R.string.text_copied,
                        Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
    }

    /**
     * for replace SIM to UIM.
     */
    private void changeSimTitle() {
        findPreference(KEY_SIM_STATUS).setTitle(
                mExt.customizeSimDisplayString(
                        findPreference(KEY_SIM_STATUS).getTitle().toString(),
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID));
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mConnectivityReceiver, mConnectivityIntentFilter,
                         android.Manifest.permission.CHANGE_NETWORK_STATE, null);
        registerReceiver(mBatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        mHandler.sendEmptyMessage(EVENT_UPDATE_STATS);
    }

    @Override
    public void onPause() {
        super.onPause();

        unregisterReceiver(mBatteryInfoReceiver);
        unregisterReceiver(mConnectivityReceiver);
        mHandler.removeMessages(EVENT_UPDATE_STATS);
    }

    /**
     * Removes the specified preference, if it exists.
     * @param key the key for the Preference item
     */
    private void removePreferenceFromScreen(String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            getPreferenceScreen().removePreference(pref);
        }
    }

    /**
     * @param preference The key for the Preference item
     * @param property The system property to fetch
     * @param alt The default value, if the property doesn't exist
     */
    private void setSummary(String preference, String property, String alt) {
        try {
            findPreference(preference).setSummary(
                    SystemProperties.get(property, alt));
        } catch (RuntimeException e) {

        }
    }

    private void setSummaryText(String preference, String text) {
            if (TextUtils.isEmpty(text)) {
               text = mUnknown;
             }
             // some preferences may be missing
             if (findPreference(preference) != null) {
                 findPreference(preference).setSummary(text);
             }
    }

    private void setWimaxStatus() {
        if (mWimaxMacAddress != null) {
            String macAddress = SystemProperties.get("net.wimax.mac.address", mUnavailable);
            mWimaxMacAddress.setSummary(macAddress);
        }
    }

    private void setWifiStatus() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        mWifiMacAddress.setSummary(!TextUtils.isEmpty(macAddress) ? macAddress.toUpperCase() : mUnavailable);
    }

    private void setIpAddressStatus() {
        String ipAddress = Utils.getDefaultIpAddresses(this.mCM);
        if (ipAddress != null) {
            mIpAddress.setSummary(ipAddress);
        } else {
            mIpAddress.setSummary(mUnavailable);
        }
    }

    private void setBtStatus() {
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth != null && mBtAddress != null) {
            String address = bluetooth.isEnabled() ? bluetooth.getAddress() : null;
            if (!TextUtils.isEmpty(address)) {
               // Convert the address to lowercase for consistency with the wifi MAC address.
            	
                mBtAddress.setSummary(address.toUpperCase());
            } else {
                mBtAddress.setSummary(mUnavailable);
            }
        }
    }

    void updateConnectivity() {
        setWimaxStatus();
        setWifiStatus();
        setBtStatus();
        setIpAddressStatus();
    }

    void updateTimes() {
        long at = SystemClock.uptimeMillis() / 1000;
        long ut = SystemClock.elapsedRealtime() / 1000;

        if (ut == 0) {
            ut = 1;
        }

        mUptime.setSummary(convert(ut));
    }

    private String pad(int n) {
        if (n >= 10) {
            return String.valueOf(n);
        } else {
            return "0" + String.valueOf(n);
        }
    }

    private String convert(long t) {
        int s = (int)(t % 60);
        int m = (int)((t / 60) % 60);
        int h = (int)((t / 3600));

        return h + ":" + pad(m) + ":" + pad(s);
    }
}
