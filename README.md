# FuckPinning

Behavior:
- If the device is in lock task mode (screen pinning/app pinning), long-press power exits lock task mode.
### Notice
To exit lock task mode on HyperOS, you should ***short-press power+volup***, it's workaround, for HyperOS might launch voice assistant via long-press power.
- If not in lock task mode, original power long-press behavior is untouched.

## Build

```bash
./gradlew assembleDebug
```

## Install and scope

1. Install the generated APK.
2. Enable the module in LSPosed/Xposed.
3. Reboot.

## Note

Support HyperOS, zui, stock android rom with Trebuchet
### Tested on
1. TB320FC with zui V1.1.350
2. cmi with lineage-23.2
3. fuxi with HyperOS 3.0.3.0
