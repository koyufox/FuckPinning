package dev.koyufox.fuckpinning;

import android.content.Context;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class DefaultScreenPinnedInputConsumerHook {
    private static final String TAG = "[FuckPinning]";
    private static final String SCREEN_PINNED_CONSUMER_CLASS = "com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer";
    private static final String GESTURE_STATE_SIMPLE_NAME = "GestureState";

    private DefaultScreenPinnedInputConsumerHook() {
    }

    public static void install(ClassLoader classLoader) {
        boolean installed = installScreenPinnedConsumerHook(classLoader);
        if (installed) {
            XposedBridge.log(TAG + " primary ScreenPinnedInputConsumer signature hook active");
            return;
        }

        XposedBridge.log(TAG + " primary hook not matched, no fallback enabled");
    }

    private static boolean installScreenPinnedConsumerHook(ClassLoader classLoader) {
        try {
            Class<?> consumerClass = XposedHelpers.findClass(SCREEN_PINNED_CONSUMER_CLASS, classLoader);
            Method[] declaredMethods = consumerClass.getDeclaredMethods();
            int hookedCount = 0;

            for (Method method : declaredMethods) {
                if (!isPrimaryTargetMethod(method)) {
                    continue;
                }

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        handleBeforePrimaryMethod(param, method);
                    }
                });
                hookedCount++;
                XposedBridge.log(TAG + " hooked primary method candidate: " + formatMethod(method));
            }

            if (hookedCount > 0) {
                XposedBridge.log(TAG + " primary hook candidates matched: " + hookedCount);
                return true;
            }

            XposedBridge.log(TAG + " no primary method candidate matched in " + SCREEN_PINNED_CONSUMER_CLASS);
            return false;
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to install primary signature hook: " + t);
            return false;
        }
    }

    private static boolean isPrimaryTargetMethod(Method method) {
        if (method == null || Modifier.isStatic(method.getModifiers())) {
            return false;
        }

        if (method.getReturnType() != void.class) {
            return false;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 2) {
            return false;
        }

        if (!Context.class.isAssignableFrom(parameterTypes[0])) {
            return false;
        }

        return looksLikeGestureState(parameterTypes[1]);
    }

    private static boolean looksLikeGestureState(Class<?> maybeGestureStateClass) {
        if (maybeGestureStateClass == null) {
            return false;
        }

        if (GESTURE_STATE_SIMPLE_NAME.equals(maybeGestureStateClass.getSimpleName())) {
            return true;
        }

        String lowerName = maybeGestureStateClass.getName().toLowerCase(Locale.ROOT);
        return lowerName.contains("gesture") && lowerName.contains("state");
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