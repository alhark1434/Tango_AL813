/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.settings.wifi;

import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.os.SystemProperties;
import android.service.persistentdata.PersistentDataBlockManager;
import android.view.View;

import com.android.settings.ButtonBarHandler;
import com.android.settings.R;
import com.android.settings.SetupWizardUtils;
import com.android.settings.wifi.SetupWizardNavBar;
import com.android.settings.wifi.SetupWizardNavBar.NavigationBarListener;

public class WifiSetupActivity extends WifiPickerActivity
        implements ButtonBarHandler, NavigationBarListener {
    private static final String TAG = "WifiSetupActivity";

    // this boolean extra specifies whether to auto finish when connection is established
    private static final String EXTRA_AUTO_FINISH_ON_CONNECT = "wifi_auto_finish_on_connect";

    // This boolean extra specifies whether network is required
    private static final String EXTRA_IS_NETWORK_REQUIRED = "is_network_required";

    // This boolean extra specifies whether wifi is required
    private static final String EXTRA_IS_WIFI_REQUIRED = "is_wifi_required";

    // Whether auto finish is suspended until user connects to an access point
    private static final String EXTRA_REQUIRE_USER_NETWORK_SELECTION =
            "wifi_require_user_network_selection";

    // Key for whether the user selected network in saved instance state bundle
    private static final String PARAM_USER_SELECTED_NETWORK = "userSelectedNetwork";

    // Activity result when pressing the Skip button
    private static final int RESULT_SKIP = Activity.RESULT_FIRST_USER;

    // Whether to auto finish when the user selected a network and successfully connected
    private boolean mAutoFinishOnConnection;
    // Whether network is required to proceed. This is decided in SUW and passed in as an extra.
    private boolean mIsNetworkRequired;
    // Whether wifi is required to proceed. This is decided in SUW and passed in as an extra.
    private boolean mIsWifiRequired;
    // Whether the user connected to a network. This excludes the auto-connecting by the system.
    private boolean mUserSelectedNetwork;
    // Whether the device is connected to WiFi
    private boolean mWifiConnected;

    private SetupWizardNavBar mNavigationBar;

    private IntentFilter mFilter = new IntentFilter();
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Refresh the connection state with the latest connection info. Use the connection info
            // from ConnectivityManager instead of the one attached in the intent to make sure
            // we have the most up-to-date connection state. b/17511772
            /// M: timing issue , CR: ALPS01837025 , don't auto finish
            Log.d(TAG, "onReceive, action = " + intent.getAction());
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                final NetworkInfo info = (NetworkInfo) bundle
                        .get(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI) {
                    refreshConnectionState();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        mAutoFinishOnConnection = intent.getBooleanExtra(EXTRA_AUTO_FINISH_ON_CONNECT, false);
        mIsNetworkRequired = intent.getBooleanExtra(EXTRA_IS_NETWORK_REQUIRED, false);
        mIsWifiRequired = intent.getBooleanExtra(EXTRA_IS_WIFI_REQUIRED, false);
        // Behave like the user already selected a network if we do not require selection
        mUserSelectedNetwork = !intent.getBooleanExtra(EXTRA_REQUIRE_USER_NETWORK_SELECTION, false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PARAM_USER_SELECTED_NETWORK, mUserSelectedNetwork);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mUserSelectedNetwork = savedInstanceState.getBoolean(PARAM_USER_SELECTED_NETWORK, true);
    }

    private boolean isWifiConnected() {
        final ConnectivityManager connectivity = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean wifiConnected = connectivity != null &&
                connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
        mWifiConnected = wifiConnected;
        return wifiConnected;
    }

    private void refreshConnectionState() {
        if (isWifiConnected()) {
            if (mAutoFinishOnConnection && mUserSelectedNetwork) {
                Log.d(TAG, "Auto-finishing with connection");
                finishOrNext(Activity.RESULT_OK);
                // Require a user selection before auto-finishing next time we are here. The user
                // can either connect to a different network or press "next" to proceed.
                mUserSelectedNetwork = false;
            }
            setNextButtonText(R.string.setup_wizard_next_button_label);
            setNextButtonEnabled(true);
        } else if (mIsWifiRequired || (mIsNetworkRequired && !isNetworkConnected())) {
            // We do not want the user to skip wifi setting if
            // - wifi is required, but wifi connection hasn't been established yet;
            // - or network is required, but no valid connection has been established.
            setNextButtonText(R.string.skip_label);
            setNextButtonEnabled(false);
        } else {
            // In other cases, user can choose to skip. Specifically these cases are
            // - wifi is not required;
            // - and network is not required;
            // -     or network is required and a valid connection has been established.
            setNextButtonText(R.string.skip_label);
            setNextButtonEnabled(true);
        }
    }

    private void setNextButtonEnabled(boolean enabled) {
        /*HQ_xupeixin at 2015-11-11 modified about nextbutton  begin */
        enabled = false;
        //Because of FRP feature , we should control the nextbutton enable status.
        PersistentDataBlockManager manager = (PersistentDataBlockManager)this.getSystemService(Context.PERSISTENT_DATA_BLOCK_SERVICE);
        if (manager != null) {
            byte[] frp = manager.read();
            if (frp != null) {
                int length = frp.length;
                Log.d(TAG, "frp.length:" + frp.length);
                if (length > 0 && !isNetworkConnected()) {
                    //have one google account in device,disable the nextbutton.
                    enabled = false;
                } else {
                    //no google account in device,so enable the nextbutton.
                    enabled = true;
                }
            }
        } else {
            //default is enable
            enabled = true;
        }
        /*HQ_xupeixin at 2015-11-11 modified end*/
        if (mNavigationBar != null) {
            mNavigationBar.getNextButton().setEnabled(enabled);
        }
    }

    private void setNextButtonText(int resId) {
        if (mNavigationBar != null) {
            mNavigationBar.getNextButton().setText(resId);
        }
    }

    /* package */ void networkSelected() {
        Log.d(TAG, "Network selected by user");
        mUserSelectedNetwork = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        //HQ_hushunli 2015-10-31 add for HQ01473267 begin
        /*HQ_liugang delete for HQ01631824
        String locale = Locale.getDefault().toString();
        boolean is24Hour = DateFormat.is24HourFormat(this);
        Log.d(TAG, "locale is " + locale + ", is24Hour is " + is24Hour);
        if (SystemProperties.get("ro.hq.africa.time.format").equals("1")) {
            if (locale.equals("en_US") || locale.equals("ar_IL") || locale.equals("fa_IR")) {
                if (is24Hour) {
                    Settings.System.putString(getContentResolver(), Settings.System.TIME_12_24, "12");
                }
            } else {
                if (!is24Hour) {
                    Settings.System.putString(getContentResolver(), Settings.System.TIME_12_24, "24");
                }
            }
        }
        */
        //HQ_hushunli 2015-10-31 add for HQ01473267 end
	String locale = Locale.getDefault().toString();
        boolean is24Hour = DateFormat.is24HourFormat(this);
		if (SystemProperties.get("ro.hq.mena.time.format").equals("1")) {
            if (locale.equals("en_US") || locale.equals("ar_IL") || locale.equals("fa_IR")) {
                if (is24Hour) {
                    Settings.System.putString(getContentResolver(), Settings.System.TIME_12_24, "12");
                }
            } else {
                if (!is24Hour) {
                    Settings.System.putString(getContentResolver(), Settings.System.TIME_12_24, "24");
                }
            }
        }
        registerReceiver(mReceiver, mFilter);
        refreshConnectionState();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        unregisterReceiver(mReceiver);
        super.onPause();
    }

    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        resid = SetupWizardUtils.getTheme(getIntent(), resid);
        super.onApplyThemeResource(theme, resid, first);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return WifiSettingsForSetupWizard.class.getName().equals(fragmentName);
    }

    @Override
    /* package */ Class<? extends PreferenceFragment> getWifiSettingsClass() {
        return WifiSettingsForSetupWizard.class;
    }

    /**
     * Complete this activity and return the results to the caller. If using WizardManager, this
     * will invoke the next scripted action; otherwise, we simply finish.
     */
    public void finishOrNext(int resultCode) {
        setSystemUi();
        if (SetupWizardUtils.isUsingWizardManager(this)) {
            SetupWizardUtils.sendResultsToSetupWizard(this, resultCode);
        } else {
            setResult(resultCode);
            finish();
        }
    }

    public void setSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
               View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.NAVIGATION_BAR_TRANSLUCENT
                );
    }

    @Override
    public void onNavigationBarCreated(final SetupWizardNavBar bar) {
        mNavigationBar = bar;
        SetupWizardUtils.setImmersiveMode(this, bar);
    }

    @Override
    public void onNavigateBack() {
        onBackPressed();
    }

    @Override
    public void onNavigateNext() {
        if (mWifiConnected) {
            finishOrNext(RESULT_OK);
        } else {
            // Warn of possible data charges if there is a network connection, or lack of updates
            // if there is none.
            final int message = isNetworkConnected() ? R.string.wifi_skipped_message :
                    R.string.wifi_and_mobile_skipped_message;
            /*HQ_xupeixin at 2015-11-10 modified for hide the wifi skip dialog and finish this activity on click nextbutton in latin_claro product begin*/
            if (SystemProperties.get("ro.hq.claro.wifisetup.control").equals("1")) {
                finishOrNext(RESULT_OK);
                return;
            }
            WifiSkipDialog.newInstance(message).show(getFragmentManager(), "dialog");
            /*HQ_xupeixin at 2015-11-11 modified end*/
        }
    }

    /**
     * @return True if there is a valid network connection, whether it is via WiFi, mobile data or
     *         other means.
     */
    private boolean isNetworkConnected() {
        final ConnectivityManager connectivity = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return false;
        }
        final NetworkInfo info = connectivity.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    public static class WifiSkipDialog extends DialogFragment {
        public static WifiSkipDialog newInstance(int messageRes) {
            final Bundle args = new Bundle();
            args.putInt("messageRes", messageRes);
            final WifiSkipDialog dialog = new WifiSkipDialog();
            dialog.setArguments(args);
            return dialog;
        }

        public WifiSkipDialog() {
            // no-arg constructor for fragment
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            if (getActivity() != null && getActivity() instanceof WifiSetupActivity) {
                WifiSetupActivity activity = (WifiSetupActivity) getActivity();
                activity.setSystemUi();
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int messageRes = getArguments().getInt("messageRes");
            return new AlertDialog.Builder(getActivity())
                    .setMessage(messageRes)
                    .setCancelable(false)
                    .setPositiveButton(R.string.wifi_skip_anyway,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            WifiSetupActivity activity = (WifiSetupActivity) getActivity();
                            activity.finishOrNext(RESULT_SKIP);
                        }
                    })
                    .setNegativeButton(R.string.wifi_dont_skip,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    })
                    .create();
        }
    }
}
