package dev.koyufox.fuckpinning;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

public final class DefaultScreenPinnedInputConsumerHook {
    private static final String TAG = "[FuckPinning]";
    private static final String SCREEN_PINNED_CONSUMER_CLASS = "com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer";
    private static final String QUICKSTEP_PACKAGE = "com.android.quickstep";
    private static final String CONTEXT_CLASS = "android.content.Context";

    private static volatile boolean dexKitLoadAttempted;
    private static volatile boolean dexKitLoaded;

    private DefaultScreenPinnedInputConsumerHook() {
    }

    public static void install(ClassLoader classLoader) {
        if (!ensureDexKitLoaded()) {
            XposedBridge.log(TAG + " DexKit unavailable, skip install");
            return;
        }

        boolean installed = installScreenPinnedConsumerHookWithDexKit(classLoader);
        if (installed) {
            XposedBridge.log(TAG + " DexKit ScreenPinnedInputConsumer hook active");
            return;
        }

        XposedBridge.log(TAG + " DexKit hook not matched");
    }

    private static synchronized boolean ensureDexKitLoaded() {
        if (dexKitLoadAttempted) {
            return dexKitLoaded;
        }

        dexKitLoadAttempted = true;
        try {
            System.loadLibrary("dexkit");
            dexKitLoaded = true;
            XposedBridge.log(TAG + " DexKit native library loaded");
        } catch (Throwable t) {
            dexKitLoaded = false;
            XposedBridge.log(TAG + " failed to load DexKit native library: " + t);
        }
        return dexKitLoaded;
    }

    private static boolean installScreenPinnedConsumerHookWithDexKit(ClassLoader classLoader) {
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

                Method method = resolveMethod(methodData, classLoader);
                if (method == null) {
                    continue;
                }

                candidateCount++;

                String sign = method.toGenericString();
                if (!hookedMethodSigns.add(sign)) {
                    continue;
                }

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        handleBeforePrimaryMethod(param, method);
                    }
                });
                XposedBridge.log(TAG + " hooked primary method candidate: " + formatMethod(method));
            }

            XposedBridge.log(TAG + " DexKit candidates scanned: " + candidateCount);

            if (!hookedMethodSigns.isEmpty()) {
                XposedBridge.log(TAG + " DexKit hook candidates matched: " + hookedMethodSigns.size());
                return true;
            }

            XposedBridge.log(TAG + " DexKit no method candidate matched in " + SCREEN_PINNED_CONSUMER_CLASS);
            return false;
        } catch (Throwable t) {
            XposedBridge.log(TAG + " DexKit hook install failed: " + t);
            return false;
        }
    }

    private static Method resolveMethod(MethodData methodData, ClassLoader classLoader) {
        try {
            return methodData.getMethodInstance(classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to resolve DexKit method instance: " + t);
            return null;
        }
    }

    private static void handleBeforePrimaryMethod(XC_MethodHook.MethodHookParam param, Method method) {
        try {
            if (!isScreenPinnedConsumer(param.thisObject)) {
                return;
            }

            // Short-circuit the pin-exit action path while keeping launcher event loop intact.
            param.setResult(null);
            XposedBridge.log(TAG + " blocked primary method: " + formatMethod(method));
        } catch (Throwable t) {
            XposedBridge.log(TAG + " primary method hook failed, keep stock behavior: " + t);
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