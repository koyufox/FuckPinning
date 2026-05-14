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

import java.lang.reflect.Method;

import dev.koyufox.fuckpinning.utils.ActivityTaskManagerUtils;
import dev.koyufox.fuckpinning.utils.ModuleLog;
import io.github.libxposed.api.XposedInterface;

public final class PowerKeyRuleLongPressUnpinHook {
    private static final String[] POWER_KEY_RULE_CLASS_CANDIDATES = {
            "com.android.server.policy.PhoneWindowManager$PowerKeyRule",
            "com.android.server.policy.PowerKeyRule"
    };

    private final XposedInterface ctx;
    private final ClassLoader classLoader;

    private PowerKeyRuleLongPressUnpinHook(XposedInterface ctx, ClassLoader classLoader) {
        this.ctx = ctx;
        this.classLoader = classLoader;
    }

    public static void install(XposedInterface ctx, ClassLoader classLoader) {
        new PowerKeyRuleLongPressUnpinHook(ctx, classLoader).install();
    }

    private void install() {
        Class<?> powerKeyRuleClass = findPowerKeyRuleClass();
        if (powerKeyRuleClass == null) {
            ModuleLog.w("failed to hook PowerKeyRule.onLongPress: class not found");
            return;
        }

        try {
            for (Method method : powerKeyRuleClass.getDeclaredMethods()) {
                if ("onLongPress".equals(method.getName())) {
                    ctx.hook(method).intercept(chain -> {
                        handleOnLongPress(chain);
                        return null;
                    });
                }
            }
            ModuleLog.i("hooked " + powerKeyRuleClass.getName() + ".onLongPress");
        } catch (Throwable t) {
            ModuleLog.e("failed to hook " + powerKeyRuleClass.getName() + ".onLongPress", t);
        }
    }

    private Class<?> findPowerKeyRuleClass() {
        for (String className : POWER_KEY_RULE_CLASS_CANDIDATES) {
            try {
                return Class.forName(className, false, classLoader);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void handleOnLongPress(XposedInterface.Chain chain) throws Throwable {
        try {
            if (!chain.getArgs().isEmpty()) {
                Object arg0 = chain.getArg(0);
                if (arg0 != null) {
                    try {
                        Method getAction = arg0.getClass().getMethod("getAction");
                        int action = ((Number) getAction.invoke(arg0)).intValue();
                        if (action != 1) {
                            chain.proceed();
                            return;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }

            Object atm = ActivityTaskManagerUtils.getActivityTaskManagerService();
            if (atm == null) {
                chain.proceed();
                return;
            }

            if (!ActivityTaskManagerUtils.isInLockTaskMode(atm)) {
                chain.proceed();
                return;
            }

            ActivityTaskManagerUtils.stopSystemLockTaskMode(atm);
            setPowerKeyHandledFromRuleIfPresent(chain.getThisObject());

            ModuleLog.i("exited lock task mode via PowerKeyRule.onLongPress");
        } catch (RemoteException e) {
            ModuleLog.e("RemoteException when stopping lock task mode", e);
            chain.proceed();
        } catch (Throwable t) {
            ModuleLog.e("PowerKeyRule.onLongPress hook failed, fallback to stock behavior", t);
            chain.proceed();
        }
    }

    private static void setPowerKeyHandledFromRuleIfPresent(Object ruleInstance) {
        if (ruleInstance == null) {
            return;
        }

        Object phoneWindowManager = null;
        try {
            java.lang.reflect.Field f = ruleInstance.getClass().getDeclaredField("this$0");
            f.setAccessible(true);
            phoneWindowManager = f.get(ruleInstance);
        } catch (Throwable ignored) {
        }

        if (phoneWindowManager == null) {
            phoneWindowManager = ruleInstance;
        }

        try {
            java.lang.reflect.Field f = phoneWindowManager.getClass().getDeclaredField("mPowerKeyHandled");
            f.setAccessible(true);
            f.setBoolean(phoneWindowManager, true);
        } catch (Throwable ignored) {
        }
    }
}
