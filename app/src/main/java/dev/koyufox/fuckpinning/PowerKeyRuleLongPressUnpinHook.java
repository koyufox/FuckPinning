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
            Object atm = getActivityTaskManagerService();
            if (atm == null) {
                return;
            }

            if (!isInLockTaskMode(atm)) {
                return;
            }

            stopSystemLockTaskMode(atm);
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

    private static Object getActivityTaskManagerService() {
        try {
            Class<?> atmClass = XposedHelpers.findClass("android.app.ActivityTaskManager", null);
            return XposedHelpers.callStaticMethod(atmClass, "getService");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " ActivityTaskManager.getService unavailable: " + t);
            return null;
        }
    }

    private static boolean isInLockTaskMode(Object atm) throws RemoteException {
        try {
            Object result = XposedHelpers.callMethod(atm, "isInLockTaskMode");
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Throwable ignored) {
            // Keep compatibility with ROMs where binder method name changed.
        }

        try {
            Object stateObj = XposedHelpers.callMethod(atm, "getLockTaskModeState");
            if (stateObj instanceof Integer) {
                return ((Integer) stateObj) != 0;
            }
        } catch (Throwable ignored) {
            // Fall through to false.
        }

        return false;
    }

    private static void stopSystemLockTaskMode(Object atm) throws RemoteException {
        try {
            XposedHelpers.callMethod(atm, "stopSystemLockTaskMode");
            return;
        } catch (Throwable ignored) {
            // Fall back for ROM variants.
        }

        XposedHelpers.callMethod(atm, "stopLockTaskMode");
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
