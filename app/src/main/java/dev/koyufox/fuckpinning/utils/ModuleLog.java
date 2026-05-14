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

import io.github.libxposed.api.XposedInterface;

public final class ModuleLog {
    public static final String TAG = "FuckPinning";

    private static volatile XposedInterface ctx;

    private ModuleLog() {
    }

    public static void init(XposedInterface xposedInterface) {
        ctx = xposedInterface;
    }

    public static void i(String msg) {
        XposedInterface c = ctx;
        if (c != null) c.log(android.util.Log.INFO, TAG, msg);
    }

    public static void w(String msg) {
        XposedInterface c = ctx;
        if (c != null) c.log(android.util.Log.WARN, TAG, msg);
    }

    public static void e(String msg) {
        XposedInterface c = ctx;
        if (c != null) c.log(android.util.Log.ERROR, TAG, msg);
    }

    public static void e(String msg, Throwable t) {
        XposedInterface c = ctx;
        if (c != null) c.log(android.util.Log.ERROR, TAG, msg, t);
    }
}
