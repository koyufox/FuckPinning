package dev.koyufox.fuckpinning;

import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class LauncherGestureBlockHook {
    private static final String TAG = "[FuckPinning]";
    private static final String TIS_CLASS = "com.android.quickstep.TouchInteractionService";
    private static final String DEFAULT_CONSUMER_FIELD = "q";

    private LauncherGestureBlockHook() {
    }

    public static void install(ClassLoader classLoader) {
        try {
            Class<?> tisClass = XposedHelpers.findClass(TIS_CLASS, classLoader);
            XposedBridge.hookAllMethods(tisClass, "e0", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    handleAfterE0(param);
                }
            });
            XposedBridge.log(TAG + " hooked TouchInteractionService.e0");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to hook TouchInteractionService.e0: " + t);
        }
    }

    private static void handleAfterE0(XC_MethodHook.MethodHookParam param) {
        try {
            Object consumer = param.getResult();
            if (!isScreenPinnedConsumer(consumer)) {
                return;
            }

            Object fallbackConsumer = getDefaultConsumer(param.thisObject);
            if (fallbackConsumer != null) {
                param.setResult(fallbackConsumer);
                XposedBridge.log(TAG + " replaced ScreenPinnedInputConsumer with default consumer");
                return;
            }

            // Safety fallback: keep original result if we cannot resolve a replacement.
            XposedBridge.log(TAG + " default consumer unavailable, keep original ScreenPinnedInputConsumer");
        } catch (Throwable t) {
            // Never break launcher gesture dispatch on hook errors.
            XposedBridge.log(TAG + " e0 post-hook failed, fallback to stock behavior: " + t);
        }
    }

    private static Object getDefaultConsumer(Object touchInteractionService) {
        if (touchInteractionService == null) {
            return null;
        }

        try {
            return XposedHelpers.getObjectField(touchInteractionService, DEFAULT_CONSUMER_FIELD);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to resolve default consumer field '" + DEFAULT_CONSUMER_FIELD + "': " + t);
            return null;
        }
    }

    private static boolean isScreenPinnedConsumer(Object consumer) {
        if (consumer == null) {
            return false;
        }

        String className = consumer.getClass().getName();
        if ("com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer".equals(className)) {
            return true;
        }

        String lower = className.toLowerCase(Locale.ROOT);
        return lower.contains("screen") && lower.contains("pinned") && lower.contains("consumer");
    }
}
