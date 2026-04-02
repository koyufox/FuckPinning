package dev.koyufox.fuckpinning;

import android.content.Context;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class TrebuchetLauncherGestureBlockHook {
    private static final String TAG = "[FuckPinning]";
    private static final String SCREEN_PINNED_CONSUMER_CLASS = "com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer";
    private static final String ALT_SCREEN_PINNED_CONSUMER_CLASS = "com.android.quickstep.ScreenPinnedInputConsumer";
    private static final String GESTURE_STATE_SIMPLE_NAME = "GestureState";

    // Legacy Trebuchet path; only used as fallback when primary signature hook does not match.
    private static final String UTILS_CLASS = "com.android.quickstep.InputConsumerUtils";
    private static final String INPUT_CONSUMER_CLASS = "com.android.quickstep.InputConsumer";

    private TrebuchetLauncherGestureBlockHook() {
    }

    public static void install(ClassLoader classLoader) {
        boolean primaryHookInstalled = installScreenPinnedConsumerHook(classLoader);
        if (primaryHookInstalled) {
            XposedBridge.log(TAG + " primary ScreenPinnedInputConsumer signature hook active");
            return;
        }

        XposedBridge.log(TAG + " primary hook not matched, fallback activated: InputConsumerUtils.newConsumer");
        installLegacyFallbackHook(classLoader);
    }

    private static boolean installScreenPinnedConsumerHook(ClassLoader classLoader) {
        try {
            Class<?> consumerClass = resolveScreenPinnedConsumerClass(classLoader);
            if (consumerClass == null) {
                XposedBridge.log(TAG + " ScreenPinnedInputConsumer class not found for primary hook");
                return false;
            }

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

            XposedBridge.log(TAG + " no primary method candidate matched in " + consumerClass.getName());
            return false;
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to install primary signature hook: " + t);
            return false;
        }
    }

    private static Class<?> resolveScreenPinnedConsumerClass(ClassLoader classLoader) {
        try {
            return XposedHelpers.findClass(SCREEN_PINNED_CONSUMER_CLASS, classLoader);
        } catch (Throwable ignored) {
            try {
                return XposedHelpers.findClass(ALT_SCREEN_PINNED_CONSUMER_CLASS, classLoader);
            } catch (Throwable t) {
                XposedBridge.log(TAG + " failed to resolve ScreenPinnedInputConsumer class: " + t);
                return null;
            }
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

    private static void installLegacyFallbackHook(ClassLoader classLoader) {
        try {
            Class<?> utilsClass = XposedHelpers.findClass(UTILS_CLASS, classLoader);
            XposedBridge.hookAllMethods(utilsClass, "newConsumer", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    handleAfterNewConsumer(param, classLoader);
                }
            });
            XposedBridge.log(TAG + " fallback hooked InputConsumerUtils.newConsumer");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to install fallback hook InputConsumerUtils.newConsumer: " + t);
        }
    }

    private static void handleAfterNewConsumer(XC_MethodHook.MethodHookParam param, ClassLoader classLoader) {
        try {
            Object consumer = param.getResult();
            if (!isScreenPinnedConsumer(consumer)) {
                return;
            }

            Object noOpConsumer = getNoOpConsumer(classLoader);
            if (noOpConsumer != null) {
                param.setResult(noOpConsumer);
                XposedBridge.log(TAG + " fallback replaced ScreenPinnedInputConsumer with InputConsumer.NO_OP");
                return;
            }

            // Safety fallback
            XposedBridge.log(TAG + " fallback InputConsumer.NO_OP unavailable, keep original ScreenPinnedInputConsumer");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " fallback newConsumer post-hook failed: " + t);
        }
    }

    private static Object getNoOpConsumer(ClassLoader classLoader) {
        try {
            Class<?> consumerClass = XposedHelpers.findClass(INPUT_CONSUMER_CLASS, classLoader);
            
            // First try to see if NO_OP exists (older AOSP versions)
            try {
                Object noOp = XposedHelpers.getStaticObjectField(consumerClass, "NO_OP");
                if (noOp != null) {
                    return noOp;
                }
            } catch (Throwable ignored) {
                // NO_OP missing, likely removed or renamed in this Launcher version
            }

            // Fallback: create a dynamic proxy that implements InputConsumer and does absolutely nothing.
            return java.lang.reflect.Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[]{consumerClass},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                            String methodName = method.getName();
                            if ("isConsumerDetachedFromGesture".equals(methodName)) {
                                return false; // Default to not detached
                            } else if ("getName".equals(methodName) || "toString".equals(methodName)) {
                                return "FuckPinningDummyConsumer";
                            }
                            
                            Class<?> returnType = method.getReturnType();
                            if (returnType == boolean.class) {
                                return false;
                            } else if (returnType == int.class) {
                                return 0;
                            } else if (returnType == consumerClass) {
                                return proxy; // Return itself to avoid NPE in chained calls
                            }
                            return null; // For void return types
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to create fallback InputConsumer: " + t);
            return null;
        }
    }

    private static boolean isScreenPinnedConsumer(Object consumer) {
        if (consumer == null) {
            return false;
        }

        String className = consumer.getClass().getName();
        if (SCREEN_PINNED_CONSUMER_CLASS.equals(className)) {
            return true;
        }

        // Trebuchet in Android 10/11 had a slightly different package structure sometimes
        if (ALT_SCREEN_PINNED_CONSUMER_CLASS.equals(className)) {
            return true;
        }

        String lower = className.toLowerCase(Locale.ROOT);
        return lower.contains("screen") && lower.contains("pinned") && lower.contains("consumer");
    }
}
