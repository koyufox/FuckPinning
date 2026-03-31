# FuckPinning

This module currently contains two hooks:
- `PowerLongPressUnpinHook`: hooks `com.android.server.policy.PhoneWindowManager.powerLongPress` in the `android` process (system_server).
- `LauncherGestureBlockHook`: hooks `com.android.quickstep.TouchInteractionService.e0` in the `com.zui.launcher` process.

Behavior in current version:
- If the device is in lock task mode (screen pinning), long-press power exits lock task mode.
- If not in lock task mode, original power long-press behavior is untouched.
- In `com.zui.launcher`, when `ScreenPinnedInputConsumer` is selected, it is replaced by the default consumer to block gesture unpin without returning null.

## Build

```bash
./gradlew assembleDebug
```

## Install and scope

1. Install the generated APK.
2. Enable the module in LSPosed/Xposed.
3. Reboot.

# Note
Now it only support zui and only tested on TB320FC v1.1.350

