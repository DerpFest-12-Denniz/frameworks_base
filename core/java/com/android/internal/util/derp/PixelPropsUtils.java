/*
 * Copyright (C) 2020 The Pixel Experience Project
 *               2021-2022 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.derp;

import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PixelPropsUtils {
    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final String DEVICE = "ro.product.device";
    private static final boolean DEBUG = false;
    private static boolean isPixelDevice = false;
    private static final Map<String, Object> propsToChange;
    private static final Map<String, Object> propsToChangePixel5;
    private static final Map<String, Object> propsToChangePixel6;
    private static final Map<String, Object> propsToChangePixelXL;

    private static final String[] packagesToChangePixel6 = {
            "com.google.android", "com.samsung.accessory", "com.samsung.android", "com.android.vending"};

    private static final String[] extraPackagesToChange = {
            "com.android.chrome", "com.breel.wallpapers20", "com.netflix.mediaclient"};

    private static final String[] packagesToKeep = {"com.google.android.GoogleCamera",
            "com.google.android.MTCL83", "com.google.android.UltraCVM", "com.google.android.apps.cameralite",
            "com.google.android.apps.wearables.maestro.companion", "com.google.android.dialer",
            "com.google.ar.core", "com.google.android.apps.recorder", "com.google.android.youtube",
            "com.google.android.apps.youtube"};

    // Codenames for currently supported Pixels by Google
    private static final String[] pixelCodenames = {"oriole", "raven", "redfin", "barbet",
            "bramble", "sunfish", "coral", "flame"};

    private static volatile boolean sIsGms = false;

    static {
        propsToChange = new HashMap<>();
        propsToChangePixel6 = new HashMap<>();
        propsToChangePixel6.put("BRAND", "google");
        propsToChangePixel6.put("MANUFACTURER", "Google");
        propsToChangePixel6.put("DEVICE", "raven");
        propsToChangePixel6.put("PRODUCT", "raven");
        propsToChangePixel6.put("MODEL", "Pixel 6 Pro");
        propsToChangePixel6.put(
                "FINGERPRINT", "google/raven/raven:12/SQ3A.220705.004/8836240:user/release-keys");
        propsToChangePixel5 = new HashMap<>();
        propsToChangePixel5.put("BRAND", "google");
        propsToChangePixel5.put("MANUFACTURER", "Google");
        propsToChangePixel5.put("DEVICE", "redfin");
        propsToChangePixel5.put("PRODUCT", "redfin");
        propsToChangePixel5.put("MODEL", "Pixel 5");
        propsToChangePixel5.put(
                "FINGERPRINT", "google/redfin/redfin:12/SQ3A.220705.003.A1/8672226:user/release-keys");
        propsToChangePixelXL = new HashMap<>();
        propsToChangePixelXL.put("BRAND", "google");
        propsToChangePixelXL.put("MANUFACTURER", "Google");
        propsToChangePixelXL.put("DEVICE", "marlin");
        propsToChangePixelXL.put("PRODUCT", "marlin");
        propsToChangePixelXL.put("MODEL", "Pixel XL");
        propsToChangePixelXL.put("FINGERPRINT",
                "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
        isPixelDevice = Arrays.asList(pixelCodenames).contains(SystemProperties.get(DEVICE));
    }

    private static boolean contains(String value, String[] array) {
        for (String i : array) {
            if (value.contains(i))
                return true;
        }
        return false;
    }

    public static void setProps(String packageName) {
        if (packageName == null || contains(packageName, packagesToKeep) || isPixelDevice) {
            return;
        }
        if (packageName.equals("com.netflix.mediaclient")
                && !SystemProperties.getBoolean("persist.pixelpropsutils.spoof_netflix", true)) {
            if (DEBUG)
                Log.d(TAG, "Netflix spoofing disabled by system prop");
            return;
        }
        if (packageName.equals("com.google.android.apps.photos") 
            && SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", true)){
            propsToChange.putAll(propsToChangePixelXL);
        } else if (contains(packageName, packagesToChangePixel6)) {
            propsToChange.putAll(propsToChangePixel6);
        } else if (contains(packageName, extraPackagesToChange)) {
            propsToChange.putAll(propsToChangePixel5);
        }
        if (packageName.equals("com.google.android.gms")) {
            sIsGms = true;
        }
        if (DEBUG)
            Log.d(TAG, "Defining props for: " + packageName);
        for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
            String key = prop.getKey();
            Object value = prop.getValue();
            if (DEBUG)
                Log.d(TAG, "Defining " + key + " prop for: " + packageName);
            setPropValue(key, value);
        }
        // Set MODEL to "Pixel 5a"
        if (sIsGms) {
            setPropValue("MODEL", "Pixel 5a");
        }
        // Set proper indexing fingerprint
        if (packageName.equals("com.google.android.settings.intelligence")) {
            setPropValue("FINGERPRINT", Build.VERSION.INCREMENTAL);
        }
    }

    private static void setPropValue(String key, Object value) {
        try {
            if (DEBUG)
                Log.d(TAG, "Defining prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static boolean isCallerSafetyNet() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet
        if (sIsGms && isCallerSafetyNet()) {
            throw new UnsupportedOperationException();
        }
    }
}
