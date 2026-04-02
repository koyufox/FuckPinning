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

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class HyperosLauncherGestureBlockHook {
    private static final String TAG = "[FuckPinning]";
    private static final String HELPER_CLASS = "com.miui.home.recents.ScreenPinnedHelper";

    private HyperosLauncherGestureBlockHook() {
    }

    public static void install(ClassLoader classLoader) {
        try {
            Class<?> helperClass = XposedHelpers.findClass(HELPER_CLASS, classLoader);
            
            // 直接替换掉 stopScreenPinning 方法，让它什么都不做
            XposedBridge.hookAllMethods(helperClass, "stopScreenPinning", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log(TAG + " blocked MIUI/HyperOS gesture from stopping screen pinning");
                    return null; // 对于 void 方法，返回 null 即可
                }
            });
            XposedBridge.log(TAG + " hooked ScreenPinnedHelper.stopScreenPinning");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to hook MIUI ScreenPinnedHelper.stopScreenPinning: " + t);
        }
    }
}
