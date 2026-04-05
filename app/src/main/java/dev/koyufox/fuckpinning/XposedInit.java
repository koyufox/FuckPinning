/*
 * This file is part of FuckPinning.

 * FuckPinning is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2026 koyufox
 */

package dev.koyufox.fuckpinning;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "[FuckPinning]";
    private static final String MIUI_VERSION_CODE_PROP = "ro.miui.ui.version.code";
    private static final String TARGET_HYPEROS_CODE = "816";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("android".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + " loaded in android process");
            if (isHyperOsCode816()) {
                XposedBridge.log(TAG + " detected HyperOS code " + TARGET_HYPEROS_CODE + ", using two-keys combo hook");
                HyperosTwoKeysCombinationRuleHook.install(lpparam.classLoader);
            } else {
                PowerLongPressUnpinHook.install(lpparam.classLoader);
            }
            return;
        }

        if ("com.zui.launcher".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + " loaded in com.zui.launcher process");
            DefaultScreenPinnedInputConsumerHook.install(lpparam.classLoader);
            return;
        }

        if ("com.android.launcher3".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + " loaded in com.android.launcher3 process");
            DefaultScreenPinnedInputConsumerHook.install(lpparam.classLoader);
            return;
        }

        if ("com.miui.home".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + " loaded in com.miui.home process");
            HyperosLauncherGestureBlockHook.install(lpparam.classLoader);
        }
    }

    private static boolean isHyperOsCode816() {
        try {
            Class<?> systemPropertiesClass = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object value = XposedHelpers.callStaticMethod(systemPropertiesClass, "get", MIUI_VERSION_CODE_PROP, "");
            return TARGET_HYPEROS_CODE.equals(String.valueOf(value));
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to read " + MIUI_VERSION_CODE_PROP + ": " + t);
            return false;
        }
    }
}
