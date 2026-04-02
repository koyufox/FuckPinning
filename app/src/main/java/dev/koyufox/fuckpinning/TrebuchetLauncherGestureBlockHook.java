package dev.koyufox.fuckpinning;

import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class TrebuchetLauncherGestureBlockHook {
    private static final String TAG = "[FuckPinning]";
    private static final String UTILS_CLASS = "com.android.quickstep.InputConsumerUtils";
    private static final String INPUT_CONSUMER_CLASS = "com.android.quickstep.InputConsumer";

    private TrebuchetLauncherGestureBlockHook() {
    }

    public static void install(ClassLoader classLoader) {
        try {
            Class<?> utilsClass = XposedHelpers.findClass(UTILS_CLASS, classLoader);
            XposedBridge.hookAllMethods(utilsClass, "newConsumer", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    handleAfterNewConsumer(param, classLoader);
                }
            });
            XposedBridge.log(TAG + " hooked InputConsumerUtils.newConsumer");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to hook InputConsumerUtils.newConsumer: " + t);
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
                XposedBridge.log(TAG + " replaced ScreenPinnedInputConsumer with InputConsumer.NO_OP");
                return;
            }

            // Safety fallback
            XposedBridge.log(TAG + " InputConsumer.NO_OP unavailable, keep original ScreenPinnedInputConsumer");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " newConsumer post-hook failed: " + t);
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
        if ("com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer".equals(className)) {
            return true;
        }

        // Trebuchet in Android 10/11 had a slightly different package structure sometimes
        if ("com.android.quickstep.ScreenPinnedInputConsumer".equals(className)) {
            return true;
        }

        String lower = className.toLowerCase(Locale.ROOT);
        return lower.contains("screen") && lower.contains("pinned") && lower.contains("consumer");
    }
}
