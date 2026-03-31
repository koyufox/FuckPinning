package dev.koyufox.fuckpinning;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "[FuckPinning]";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("android".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + " loaded in android process");
            PowerLongPressUnpinHook.install(lpparam.classLoader);
            return;
        }

        if ("com.zui.launcher".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + " loaded in com.zui.launcher process");
            LauncherGestureBlockHook.install(lpparam.classLoader);
        }
    }
}
