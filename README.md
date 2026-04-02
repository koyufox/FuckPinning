# FuckPinning

Behavior:
- If the device is in lock task mode (screen pinning/app pinning), long-press power exits lock task mode.
- If not in lock task mode, original power long-press behavior is untouched.

## Build

```bash
./gradlew assembleDebug
```

## Install and scope

1. Install the generated APK.
2. Enable the module in LSPosed/Xposed.
3. Reboot.

# Note

Support zui and stock android rom with Trebuchet
Tested on TB320FC with zui V1.1.350 and cmi with lineage-23.2
