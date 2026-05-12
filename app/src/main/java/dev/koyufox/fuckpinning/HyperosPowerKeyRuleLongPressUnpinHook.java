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
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dev.koyufox.fuckpinning.utils.ActivityTaskManagerUtils;
import io.github.libxposed.api.XposedInterface;

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
    private static final String TAG = "FuckPinning";
    private static final String MIUI_POWER_KEY_RULE_CLASS =
            "com.android.server.input.shortcut.singlekeyrule.PowerKeyRule";

    private final XposedInterface ctx;
    private final ClassLoader classLoader;

    private HyperosPowerKeyRuleLongPressUnpinHook(XposedInterface ctx, ClassLoader classLoader) {
        this.ctx = ctx;
        this.classLoader = classLoader;
    }

    public static void install(XposedInterface ctx, ClassLoader classLoader) {
        new HyperosPowerKeyRuleLongPressUnpinHook(ctx, classLoader).install();
    }

    private void install() {
        try {
            Class<?> miuiPowerKeyRuleClass = Class.forName(MIUI_POWER_KEY_RULE_CLASS, false, classLoader);
            for (Method method : miuiPowerKeyRuleClass.getDeclaredMethods()) {
                if ("onMiuiLongPress".equals(method.getName())) {
                    ctx.hook(method).intercept(chain -> {
                        handleBeforeMiuiLongPress(chain);
                        return null;
                    });
                }
            }
            log(Log.INFO, TAG, "hooked " + MIUI_POWER_KEY_RULE_CLASS + ".onMiuiLongPress");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "failed to hook MIUI PowerKeyRule.onMiuiLongPress", t);
        }
    }

    private void handleBeforeMiuiLongPress(XposedInterface.Chain chain) throws Throwable {
        try {
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

            setPowerKeyHandled(chain.getThisObject());

            log(Log.INFO, TAG, "exited lock task mode via power key long press (HyperOS)");
        } catch (RemoteException e) {
            log(Log.ERROR, TAG, "RemoteException when stopping lock task mode", e);
            chain.proceed();
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "MIUI PowerKeyRule.onMiuiLongPress hook failed", t);
            chain.proceed();
        }
    }

    private static void setPowerKeyHandled(Object ruleInstance) {
        if (ruleInstance == null) {
            return;
        }

        try {
            Field wmPolicyField = ruleInstance.getClass().getDeclaredField("mWindowManagerPolicy");
            wmPolicyField.setAccessible(true);
            Object wmPolicy = wmPolicyField.get(ruleInstance);
            if (wmPolicy != null) {
                Method setPowerKeyHandled = wmPolicy.getClass().getMethod("setPowerKeyHandled", boolean.class);
                setPowerKeyHandled.invoke(wmPolicy, true);
            }
        } catch (Throwable ignored) {
        }
    }

    private void log(int priority, String tag, String msg) {
        ctx.log(priority, tag, msg);
    }

    private void log(int priority, String tag, String msg, Throwable t) {
        ctx.log(priority, tag, msg, t);
    }
}
