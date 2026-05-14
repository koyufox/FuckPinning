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

import java.lang.reflect.Method;

public final class ActivityTaskManagerUtils {

    private ActivityTaskManagerUtils() {
    }

    public static Object getActivityTaskManagerService() {
        try {
            Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
            Method getService = atmClass.getDeclaredMethod("getService");
            return getService.invoke(null);
        } catch (Throwable t) {
            ModuleLog.w("ActivityTaskManager.getService unavailable: " + t);
            return null;
        }
    }

    public static boolean isInLockTaskMode(Object atm) throws RemoteException {
        try {
            Method isInLockTaskMode = atm.getClass().getMethod("isInLockTaskMode");
            Object result = isInLockTaskMode.invoke(atm);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method getLockTaskModeState = atm.getClass().getMethod("getLockTaskModeState");
            Object stateObj = getLockTaskModeState.invoke(atm);
            if (stateObj instanceof Integer) {
                return ((Integer) stateObj) != 0;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    public static void stopSystemLockTaskMode(Object atm) throws RemoteException {
        try {
            Method stopSystemLockTaskMode = atm.getClass().getMethod("stopSystemLockTaskMode");
            stopSystemLockTaskMode.invoke(atm);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method stopLockTaskMode = atm.getClass().getMethod("stopLockTaskMode");
            stopLockTaskMode.invoke(atm);
        } catch (Throwable ignored) {
        }
    }
}
