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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class HyperosTwoKeysCombinationRuleHook {
    private static final String TAG = "[FuckPinning]";
    private static final String PHONE_WINDOW_MANAGER = "com.android.server.policy.PhoneWindowManager";
    private static final String TWO_KEYS_RULE_CLASS = "com.android.server.policy.KeyCombinationManager$TwoKeysCombinationRule";
    private static final int KEYCODE_VOLUME_UP = 24;
    private static final int KEYCODE_POWER = 26;

    private static final Set<String> HOOKED_RULE_CLASSES = new HashSet<>();

    private HyperosTwoKeysCombinationRuleHook() {
    }

    public static void install(ClassLoader classLoader) {
        try {
            Class<?> pwmClass = XposedHelpers.findClass(PHONE_WINDOW_MANAGER, classLoader);
            XposedBridge.hookAllMethods(pwmClass, "initKeyCombinationRules", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    tryInstallCombinationRuleHook(param.thisObject);
                }
            });
            XposedBridge.log(TAG + " hooked PhoneWindowManager.initKeyCombinationRules");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to hook initKeyCombinationRules: " + t);
        }
    }

    private static void tryInstallCombinationRuleHook(Object phoneWindowManager) {
        try {
            if (phoneWindowManager == null) {
                return;
            }

            Object keyCombinationManager;
            try {
                keyCombinationManager = XposedHelpers.getObjectField(phoneWindowManager, "mKeyCombinationManager");
            } catch (Throwable t) {
                XposedBridge.log(TAG + " mKeyCombinationManager unavailable: " + t);
                return;
            }

            if (keyCombinationManager == null) {
                return;
            }

            ClassLoader classLoader = phoneWindowManager.getClass().getClassLoader();
            Class<?> twoKeysRuleClass = XposedHelpers.findClass(TWO_KEYS_RULE_CLASS, classLoader);

            int scanned = 0;
            int matched = 0;
            for (Object rule : collectRules(keyCombinationManager, twoKeysRuleClass)) {
                scanned++;
                if (!isPowerVolumeUpRule(rule)) {
                    continue;
                }

                matched++;
                hookRuleExecute(rule.getClass());
            }

            XposedBridge.log(TAG + " two-keys rules scanned=" + scanned + ", matched power+volup=" + matched);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to install two-keys execute hook: " + t);
        }
    }

    private static List<Object> collectRules(Object keyCombinationManager, Class<?> twoKeysRuleClass) {
        List<Object> out = new ArrayList<>();

        Object directRules = null;
        try {
            directRules = XposedHelpers.getObjectField(keyCombinationManager, "mRules");
        } catch (Throwable ignored) {
            // Some ROMs rename this field.
        }
        appendRules(out, directRules, twoKeysRuleClass);
        if (!out.isEmpty()) {
            return out;
        }

        for (Field field : getAllFields(keyCombinationManager.getClass())) {
            if (!Collection.class.isAssignableFrom(field.getType())) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(keyCombinationManager);
                appendRules(out, value, twoKeysRuleClass);
            } catch (Throwable ignored) {
                // Keep scanning other fields.
            }
        }

        return out;
    }

    private static void appendRules(List<Object> out, Object value, Class<?> twoKeysRuleClass) {
        if (!(value instanceof Collection<?>)) {
            return;
        }

        for (Object item : (Collection<?>) value) {
            if (item != null && twoKeysRuleClass.isInstance(item)) {
                out.add(item);
            }
        }
    }

    private static boolean isPowerVolumeUpRule(Object rule) {
        if (rule == null) {
            return false;
        }

        Integer key1 = getIntField(rule, "mKeyCode1");
        Integer key2 = getIntField(rule, "mKeyCode2");
        if (key1 != null && key2 != null) {
            return isKeyCodePair(key1, key2, KEYCODE_VOLUME_UP, KEYCODE_POWER);
        }

        // Fallback: search int fields for both keycodes.
        boolean hasVolUp = false;
        boolean hasPower = false;
        for (Field field : getAllFields(rule.getClass())) {
            if (field.getType() != int.class) {
                continue;
            }

            try {
                field.setAccessible(true);
                int value = field.getInt(rule);
                if (value == KEYCODE_VOLUME_UP) {
                    hasVolUp = true;
                } else if (value == KEYCODE_POWER) {
                    hasPower = true;
                }
            } catch (Throwable ignored) {
                // Ignore inaccessible or synthetic fields.
            }
        }
        return hasVolUp && hasPower;
    }

    private static Integer getIntField(Object target, String fieldName) {
        try {
            Object value = XposedHelpers.getObjectField(target, fieldName);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
            // Try by scanning hierarchy below.
        }

        for (Field field : getAllFields(target.getClass())) {
            if (!fieldName.equals(field.getName()) || field.getType() != int.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                return field.getInt(target);
            } catch (Throwable ignored) {
                // Not accessible on this ROM.
            }
        }

        return null;
    }

    private static boolean isKeyCodePair(int a, int b, int x, int y) {
        return (a == x && b == y) || (a == y && b == x);
    }

    private static void hookRuleExecute(Class<?> ruleClass) {
        if (ruleClass == null) {
            return;
        }

        final String className = ruleClass.getName();
        synchronized (HOOKED_RULE_CLASSES) {
            if (!HOOKED_RULE_CLASSES.add(className)) {
                return;
            }
        }

        try {
            Method executeMethod = ruleClass.getDeclaredMethod("execute");
            executeMethod.setAccessible(true);
            XposedBridge.hookMethod(executeMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    handleBeforeRuleExecute(param);
                }
            });
            XposedBridge.log(TAG + " hooked power+volup execute on " + className);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to hook execute on " + className + ": " + t);
        }
    }

    private static void handleBeforeRuleExecute(XC_MethodHook.MethodHookParam param) {
        try {
            Object atm = getActivityTaskManagerService();
            if (atm == null) {
                return;
            }

            if (!isInLockTaskMode(atm)) {
                return;
            }

            stopSystemLockTaskMode(atm);
            setPowerKeyHandledFromRule(param.thisObject);

            // execute() is void; consume stock combo action while pinned.
            param.setResult(null);
            XposedBridge.log(TAG + " exited lock task mode via power+volume up combo");
        } catch (RemoteException e) {
            XposedBridge.log(TAG + " RemoteException when stopping lock task mode: " + e);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " two-keys execute hook failed, fallback to stock behavior: " + t);
        }
    }

    private static void setPowerKeyHandledFromRule(Object ruleInstance) {
        if (ruleInstance == null) {
            return;
        }

        try {
            Object phoneWindowManager = XposedHelpers.getObjectField(ruleInstance, "this$0");
            if (phoneWindowManager != null) {
                XposedHelpers.setBooleanField(phoneWindowManager, "mPowerKeyHandled", true);
            }
        } catch (Throwable ignored) {
            // Optional on ROM variants.
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

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
        }
        return fields;
    }
}