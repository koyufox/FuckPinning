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

import android.os.RemoteException;

import dev.koyufox.fuckpinning.utils.ActivityTaskManagerUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class PowerKeyRuleLongPressUnpinHook {
    private static final String TAG = "[FuckPinning]";
    private static final String[] POWER_KEY_RULE_CLASS_CANDIDATES = {
            "com.android.server.policy.PhoneWindowManager$PowerKeyRule",
            "com.android.server.policy.PowerKeyRule"
    };

    private PowerKeyRuleLongPressUnpinHook() {
    }

    public static void install(ClassLoader classLoader) {
        Class<?> powerKeyRuleClass = findPowerKeyRuleClass(classLoader);
        if (powerKeyRuleClass == null) {
            XposedBridge.log(TAG + " failed to hook PowerKeyRule.onLongPress: class not found");
            return;
        }

        try {
            XposedBridge.hookAllMethods(powerKeyRuleClass, "onLongPress", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    handleBeforePowerKeyRuleLongPress(param);
                }
            });
            XposedBridge.log(TAG + " hooked " + powerKeyRuleClass.getName() + ".onLongPress");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to hook " + powerKeyRuleClass.getName() + ".onLongPress: " + t);
        }
    }

    private static Class<?> findPowerKeyRuleClass(ClassLoader classLoader) {
        for (String className : POWER_KEY_RULE_CLASS_CANDIDATES) {
            try {
                return XposedHelpers.findClass(className, classLoader);
            } catch (Throwable ignored) {
                // Try next candidate class name.
            }
        }
        return null;
    }

    private static void handleBeforePowerKeyRuleLongPress(XC_MethodHook.MethodHookParam param) {
        try {
            // LineageOS fires onLongPress(SingleKeyGestureEvent) three times per gesture
            // (action=0 on press, action=1 on confirmed long-press, action=2 on cancel).
            // Only exit pinning on the confirmed long-press (action=1).
            // ZUI's onLongPress(long) has no getAction() — the reflection call fails
            // and we proceed normally (it only fires once anyway).
            if (param.args.length > 0 && param.args[0] != null) {
                try {
                    java.lang.reflect.Method getAction = param.args[0].getClass().getMethod("getAction");
                    int action = ((Number) getAction.invoke(param.args[0])).intValue();
                    if (action != 1) {
                        return;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Not a SingleKeyGestureEvent — proceed (ZUI path).
                }
            }

            Object atm = ActivityTaskManagerUtils.getActivityTaskManagerService();
            if (atm == null) {
                return;
            }

            if (!ActivityTaskManagerUtils.isInLockTaskMode(atm)) {
                return;
            }

            ActivityTaskManagerUtils.stopSystemLockTaskMode(atm);
            setPowerKeyHandledFromRuleIfPresent(param.thisObject);

            // onLongPress is void, consume the original logic when we already exited pinning.
            param.setResult(null);
            XposedBridge.log(TAG + " exited lock task mode via PowerKeyRule.onLongPress");
        } catch (RemoteException e) {
            XposedBridge.log(TAG + " RemoteException when stopping lock task mode: " + e);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " PowerKeyRule.onLongPress hook failed, fallback to stock behavior: " + t);
        }
    }

    private static void setPowerKeyHandledFromRuleIfPresent(Object ruleInstance) {
        if (ruleInstance == null) {
            return;
        }

        Object phoneWindowManager = null;
        try {
            phoneWindowManager = XposedHelpers.getObjectField(ruleInstance, "this$0");
        } catch (Throwable ignored) {
            // Some ROMs may not expose this$0.
        }

        if (phoneWindowManager == null) {
            phoneWindowManager = ruleInstance;
        }

        try {
            XposedHelpers.setBooleanField(phoneWindowManager, "mPowerKeyHandled", true);
        } catch (Throwable ignored) {
            // Optional field on some ROMs.
        }
    }
}
