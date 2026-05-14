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

import java.lang.reflect.Method;

import dev.koyufox.fuckpinning.utils.ModuleLog;
import io.github.libxposed.api.XposedInterface;

public final class HyperosLauncherGestureBlockHook {
    private static final String HELPER_CLASS = "com.miui.home.recents.ScreenPinnedHelper";

    private final XposedInterface ctx;
    private final ClassLoader classLoader;

    private HyperosLauncherGestureBlockHook(XposedInterface ctx, ClassLoader classLoader) {
        this.ctx = ctx;
        this.classLoader = classLoader;
    }

    public static void install(XposedInterface ctx, ClassLoader classLoader) {
        new HyperosLauncherGestureBlockHook(ctx, classLoader).install();
    }

    private void install() {
        try {
            Class<?> helperClass = Class.forName(HELPER_CLASS, false, classLoader);

            for (Method method : helperClass.getDeclaredMethods()) {
                if ("stopScreenPinning".equals(method.getName())) {
                    ctx.hook(method).intercept(chain -> {
                        ModuleLog.i("blocked MIUI/HyperOS gesture from stopping screen pinning");
                        return null;
                    });
                }
            }
            ModuleLog.i("hooked ScreenPinnedHelper.stopScreenPinning");
        } catch (Throwable t) {
            ModuleLog.e("failed to hook MIUI ScreenPinnedHelper.stopScreenPinning", t);
        }
    }
}
