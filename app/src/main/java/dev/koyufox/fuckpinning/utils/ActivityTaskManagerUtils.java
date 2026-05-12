/*
 * This file is part of FuckPinning.
 *
 * FuckPinning is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2026 koyufox
 */

package dev.koyufox.fuckpinning.utils;

import android.os.RemoteException;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class ActivityTaskManagerUtils {
    private static final String TAG = "[FuckPinning]";

    private ActivityTaskManagerUtils() {
    }

    public static Object getActivityTaskManagerService() {
        try {
            Class<?> atmClass = XposedHelpers.findClass("android.app.ActivityTaskManager", null);
            return XposedHelpers.callStaticMethod(atmClass, "getService");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " ActivityTaskManager.getService unavailable: " + t);
            return null;
        }
    }

    public static boolean isInLockTaskMode(Object atm) throws RemoteException {
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

    public static void stopSystemLockTaskMode(Object atm) throws RemoteException {
        try {
            XposedHelpers.callMethod(atm, "stopSystemLockTaskMode");
            return;
        } catch (Throwable ignored) {
            // Fall back for ROM variants.
        }

        XposedHelpers.callMethod(atm, "stopLockTaskMode");
    }
}
