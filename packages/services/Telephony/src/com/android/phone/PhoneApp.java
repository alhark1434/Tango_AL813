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

package com.android.phone;

import android.app.Application;
import android.content.res.Configuration;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.services.telephony.TelephonyGlobals;

/**
 * Top-level Application class for the Phone app.
 */
public class PhoneApp extends Application {
    PhoneGlobals mPhoneGlobals;
    TelephonyGlobals mTelephonyGlobals;

    public PhoneApp() {
    }

    @Override
    public void onCreate() {
    	
    	android.util.Log.d("hejk", "PHONEAPP ONCREATE" + Settings.System.getInt(this.getContentResolver(), "hejk_modify_touch", -1));
    	if(Settings.System.getInt(this.getContentResolver(), "hejk_modify_touch", -1) == 1){
    		android.util.Log.d("hejk", "Recover touch state");
    		Settings.System.putInt(this.getContentResolver(), Settings.System.TOUCH_DISABLE_MODE, 1);
    		Settings.System.putInt(this.getContentResolver(), "hejk_modify_touch", 0);
    	}
        if (UserHandle.myUserId() == 0) {
            // We are running as the primary user, so should bring up the
            // global phone state.
            mPhoneGlobals = new PhoneGlobals(this);
            mPhoneGlobals.onCreate();

            mTelephonyGlobals = new TelephonyGlobals(this);
            mTelephonyGlobals.onCreate();
        }
    }
}
