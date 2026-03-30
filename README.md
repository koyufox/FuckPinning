# fuckpinning (LSPosed/Xposed)

This module hooks `com.android.server.policy.PhoneWindowManager.powerLongPress` in the `android` process.

Behavior in current version:
- If the device is in lock task mode (screen pinning), long-press power exits lock task mode.
- If not in lock task mode, original power long-press behavior is untouched.

## Build

```bash
./gradlew assembleDebug
```

## Install and scope

1. Install the generated APK.
2. Enable the module in LSPosed/Xposed.
3. Scope the module to `android` (system_server process).
4. Reboot.

## Verification

1. Enter screen pinning.
2. Long-press power.
3. Expected result: exit screen pinning directly, no global actions takeover.

## Notes

- This stage intentionally does not modify `NavigationBar.onLongPressNavigationButtons` or `NavigationBar.onHomeLongClick`.
- If lock task exit fails, the hook falls back to stock power behavior.
