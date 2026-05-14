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

import androidx.annotation.NonNull;

import dev.koyufox.fuckpinning.utils.ModuleLog;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class ModuleMain extends XposedModule {
    private static final String MIUI_VERSION_CODE_PROP = "ro.miui.ui.version.code";
    private static final String TARGET_HYPEROS_CODE = "816";

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        ModuleLog.init(this);
        ModuleLog.i("loaded in " + param.getProcessName()
                + " (isSystemServer=" + param.isSystemServer() + ")");
        ModuleLog.i("framework: " + getFrameworkName()
                + " v" + getFrameworkVersionCode() + " API " + getApiVersion());
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        ModuleLog.i("system server starting, installing power key hooks");
        ClassLoader classLoader = param.getClassLoader();

        if (isHyperOsCode816()) {
            ModuleLog.i("detected HyperOS code " + TARGET_HYPEROS_CODE);
            HyperosPowerKeyRuleLongPressUnpinHook.install(this, classLoader);
        } else {
            PowerKeyRuleLongPressUnpinHook.install(this, classLoader);
        }
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        String packageName = param.getPackageName();
        ClassLoader classLoader = param.getClassLoader();

        if ("com.zui.launcher".equals(packageName) || "com.android.launcher3".equals(packageName)) {
            ModuleLog.i("installing launcher gesture block for " + packageName);
            DefaultScreenPinnedInputConsumerHook.install(this, classLoader);
        } else if ("com.miui.home".equals(packageName)) {
            ModuleLog.i("installing MIUI launcher gesture block for " + packageName);
            HyperosLauncherGestureBlockHook.install(this, classLoader);
        }
    }

    private static boolean isHyperOsCode816() {
        try {
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method getMethod = systemPropertiesClass.getMethod("get", String.class, String.class);
            Object value = getMethod.invoke(null, MIUI_VERSION_CODE_PROP, "");
            return TARGET_HYPEROS_CODE.equals(String.valueOf(value));
        } catch (Throwable t) {
            return false;
        }
    }
}
