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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import dev.koyufox.fuckpinning.utils.ModuleLog;
import io.github.libxposed.api.XposedInterface;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

public final class DefaultScreenPinnedInputConsumerHook {
    private static final String SCREEN_PINNED_CONSUMER_CLASS = "com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer";
    private static final String QUICKSTEP_PACKAGE = "com.android.quickstep";
    private static final String CONTEXT_CLASS = "android.content.Context";

    private static volatile boolean dexKitLoadAttempted;
    private static volatile boolean dexKitLoaded;

    private final XposedInterface ctx;
    private final ClassLoader classLoader;

    private DefaultScreenPinnedInputConsumerHook(XposedInterface ctx, ClassLoader classLoader) {
        this.ctx = ctx;
        this.classLoader = classLoader;
    }

    public static void install(XposedInterface ctx, ClassLoader classLoader) {
        new DefaultScreenPinnedInputConsumerHook(ctx, classLoader).install();
    }

    private void install() {
        if (!ensureDexKitLoaded()) {
            ModuleLog.w("DexKit unavailable, skip install");
            return;
        }

        boolean installed = installScreenPinnedConsumerHookWithDexKit();
        if (installed) {
            ModuleLog.i("DexKit ScreenPinnedInputConsumer hook active");
            return;
        }

        ModuleLog.w("DexKit hook not matched");
    }

    private static synchronized boolean ensureDexKitLoaded() {
        if (dexKitLoadAttempted) {
            return dexKitLoaded;
        }

        dexKitLoadAttempted = true;
        try {
            System.loadLibrary("dexkit");
            dexKitLoaded = true;
        } catch (Throwable ignored) {
            dexKitLoaded = false;
        }
        return dexKitLoaded;
    }

    private boolean installScreenPinnedConsumerHookWithDexKit() {
        try (DexKitBridge bridge = DexKitBridge.create(classLoader, false)) {
            FindMethod query = new FindMethod()
                    .searchPackages(QUICKSTEP_PACKAGE)
                    .matcher(new MethodMatcher()
                            .declaredClass(SCREEN_PINNED_CONSUMER_CLASS)
                            .returnType("void")
                    .paramTypes(CONTEXT_CLASS, null)
                    .addInvoke(new MethodMatcher()
                        .name("stopScreenPinning")
                        .returnType("void")
                        .paramCount(0)
                    )
                    );

            Set<String> hookedMethodSigns = new HashSet<>();
            int candidateCount = 0;

            for (MethodData methodData : bridge.findMethod(query)) {
                if (!methodData.isMethod()) {
                    continue;
                }

                Method method = resolveMethod(methodData);
                if (method == null) {
                    continue;
                }

                candidateCount++;

                String sign = method.toGenericString();
                if (!hookedMethodSigns.add(sign)) {
                    continue;
                }

                ctx.hook(method).intercept(chain -> {
                    handleBlock(chain);
                    return null;
                });
                ModuleLog.i("hooked primary method candidate: " + formatMethod(method));
            }

            ModuleLog.i("DexKit candidates scanned: " + candidateCount);

            if (!hookedMethodSigns.isEmpty()) {
                ModuleLog.i("DexKit hook candidates matched: " + hookedMethodSigns.size());
                return true;
            }

            ModuleLog.w("DexKit no method candidate matched in " + SCREEN_PINNED_CONSUMER_CLASS);
            return false;
        } catch (Throwable t) {
            ModuleLog.e("DexKit hook install failed", t);
            return false;
        }
    }

    private Method resolveMethod(MethodData methodData) {
        try {
            return methodData.getMethodInstance(classLoader);
        } catch (Throwable t) {
            ModuleLog.e("failed to resolve DexKit method instance", t);
            return null;
        }
    }

    private static void handleBlock(XposedInterface.Chain chain) throws Throwable {
        try {
            if (!isScreenPinnedConsumer(chain.getThisObject())) {
                chain.proceed();
                return;
            }
        } catch (Throwable t) {
            ModuleLog.e("primary method hook failed, keep stock behavior", t);
            chain.proceed();
        }
    }

    private static String formatMethod(Method method) {
        if (method == null) {
            return "<null>";
        }

        Class<?>[] params = method.getParameterTypes();
        StringBuilder builder = new StringBuilder();
        builder.append(method.getDeclaringClass().getName())
                .append('.')
                .append(method.getName())
                .append('(');
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(params[i].getSimpleName());
        }
        builder.append(')');
        return builder.toString();
    }

    private static boolean isScreenPinnedConsumer(Object consumer) {
        if (consumer == null) {
            return false;
        }

        String className = consumer.getClass().getName();
        if (SCREEN_PINNED_CONSUMER_CLASS.equals(className)) {
            return true;
        }

        String lower = className.toLowerCase(Locale.ROOT);
        return lower.contains("screen") && lower.contains("pinned") && lower.contains("consumer");
    }
}
