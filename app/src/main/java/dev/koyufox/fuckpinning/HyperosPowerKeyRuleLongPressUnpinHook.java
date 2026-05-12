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

package dev.koyufox.fuckpinning;

import android.os.RemoteException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks the MIUI-specific PowerKeyRule.onMiuiLongPress(long) to exit screen pinning
 * via long-pressing the power key on HyperOS.
 * <p>
 * HyperOS has a dual PowerKeyRule architecture:
 * <ol>
 *   <li>{@code com.android.server.input.shortcut.singlekeyrule.PowerKeyRule}
 *       — MIUI's rule that handles shortcuts (XiaoAi, smart home, etc.)</li>
 *   <li>{@code com.android.server.policy.PhoneWindowManager$PowerKeyRule}
 *       — original AOSP rule, only reached when MIUI doesn't handle the key</li>
 * </ol>
 * The MIUI rule's {@code onMiuiLongPress()} calls {@code triggerLongPress()}
 * which launches XiaoAi via {@code postTriggerFunction("launch_voice_assistant")}.
 * Only when that returns false does it fall back to the original
 * {@code PhoneWindowManager$PowerKeyRule.onLongPress()}.
 * <p>
 * By hooking {@code onMiuiLongPress()} we intercept before XiaoAi launches.
 */
public final class HyperosPowerKeyRuleLongPressUnpinHook {
    private static final String TAG = "[FuckPinning]";
    private static final String MIUI_POWER_KEY_RULE_CLASS =
            "com.android.server.input.shortcut.singlekeyrule.PowerKeyRule";

    private HyperosPowerKeyRuleLongPressUnpinHook() {
    }

    public static void install(ClassLoader classLoader) {
        try {
            Class<?> miuiPowerKeyRuleClass = XposedHelpers.findClass(MIUI_POWER_KEY_RULE_CLASS, classLoader);
            XposedBridge.hookAllMethods(miuiPowerKeyRuleClass, "onMiuiLongPress", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    handleBeforeMiuiLongPress(param);
                }
            });
            XposedBridge.log(TAG + " hooked " + MIUI_POWER_KEY_RULE_CLASS + ".onMiuiLongPress");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to hook MIUI PowerKeyRule.onMiuiLongPress: " + t);
        }
    }

    private static void handleBeforeMiuiLongPress(XC_MethodHook.MethodHookParam param) {
        try {
            Object atm = getActivityTaskManagerService();
            if (atm == null) {
                return;
            }

            if (!isInLockTaskMode(atm)) {
                return;
            }

            stopSystemLockTaskMode(atm);

            // Mark power key as handled so the rest of the system doesn't
            // try to process it further.
            setPowerKeyHandled(param.thisObject);

            // onMiuiLongPress is void; consume it — prevents XiaoAi launch
            // and prevents fallback to OriginalPowerKeyRuleBridge.
            param.setResult(null);
            XposedBridge.log(TAG + " exited lock task mode via power key long press (HyperOS)");
        } catch (RemoteException e) {
            XposedBridge.log(TAG + " RemoteException when stopping lock task mode: " + e);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " MIUI PowerKeyRule.onMiuiLongPress hook failed: " + t);
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

    private static void setPowerKeyHandled(Object ruleInstance) {
        if (ruleInstance == null) {
            return;
        }

        try {
            // Access mWindowManagerPolicy field which holds the WindowManagerPolicy
            // (BaseMiuiPhoneWindowManager/PhoneWindowManager).
            Object wmPolicy = XposedHelpers.getObjectField(ruleInstance, "mWindowManagerPolicy");
            if (wmPolicy != null) {
                XposedHelpers.callMethod(wmPolicy, "setPowerKeyHandled", true);
            }
        } catch (Throwable ignored) {
            // The setPowerKeyHandled call inside the original onMiuiLongPress
            // already ran before triggerLongPress, but since we block the method,
            // we set it manually as a safety measure.
        }
    }
}
