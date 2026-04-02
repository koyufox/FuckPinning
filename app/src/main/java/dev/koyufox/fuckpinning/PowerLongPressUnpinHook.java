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

public final class PowerLongPressUnpinHook {
    private static final String TAG = "[FuckPinning]";
    private static final String PHONE_WINDOW_MANAGER = "com.android.server.policy.PhoneWindowManager";

    private PowerLongPressUnpinHook() {
    }

    public static void install(ClassLoader classLoader) {
        try {
            Class<?> pwmClass = XposedHelpers.findClass(PHONE_WINDOW_MANAGER, classLoader);
            XposedBridge.hookAllMethods(pwmClass, "powerLongPress", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    handleBeforePowerLongPress(param);
                }
            });
            XposedBridge.log(TAG + " hooked PhoneWindowManager.powerLongPress");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to hook powerLongPress: " + t);
        }
    }

    private static void handleBeforePowerLongPress(XC_MethodHook.MethodHookParam param) {
        try {
            Object atm = getActivityTaskManagerService();
            if (atm == null) {
                return;
            }

            if (!isInLockTaskMode(atm)) {
                return;
            }

            stopSystemLockTaskMode(atm);
            setPowerKeyHandledIfPresent(param.thisObject);

            // powerLongPress is void, consume the original logic when we already exited pinning.
            param.setResult(null);
            XposedBridge.log(TAG + " exited lock task mode via power long press");
        } catch (RemoteException e) {
            XposedBridge.log(TAG + " RemoteException when stopping lock task mode: " + e);
        } catch (Throwable t) {
            // Never break power key behavior on failures.
            XposedBridge.log(TAG + " unexpected failure, fallback to stock behavior: " + t);
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

    private static void setPowerKeyHandledIfPresent(Object phoneWindowManager) {
        try {
            XposedHelpers.setBooleanField(phoneWindowManager, "mPowerKeyHandled", true);
        } catch (Throwable ignored) {
            // Optional field on some ROMs.
        }
    }
}
