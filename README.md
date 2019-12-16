### App Instructions

**Getting Started**
- Setup bitbucket - add SSH keys
  - bitbucket.org -> Settings -> SSH Keys -> Add Key
  - SSH Key Generate Instructions: https://confluence.atlassian.com/bitbucket/set-up-an-ssh-key-728138079.html
- Your username has been added to User Group `App Developers` - should have access to `android-app` repository
- Clone Android app repository
```
git clone git@bitbucket.org:yrobotinc/android-app.git
```

**Making changes**
- Create own git branch off of: `master`, e.g `feature/front-end`
- Create pull request to `master` when ready to merge so we can view code changes

**Code Overview**
- The `android-app` project started from this project: https://github.com/adafruit/Bluefruit_LE_Connect_Android_V2, which is available on the Google Play store if you want to test out their released version.  Many of the views and source files have been removed or modified.
- The project uses `MPAndroidChart` for plotting data.  A modified version of `MPChartLib` is in the root directory `android-app/MPChartLib`.
- BLE 4.0+ (UART service only) is used for communication with the device.

- [Additional Code Overview ...](doc/code-overview.md)

---

### System Context

##### Full Hardware System
```
[    Exosuit "Device"   ]   <->   [ Android Device ]

K66 <--(SPI)--> nRF52832 <--(BLE)--> [Android App]
```

##### Mock System (Android-only)
```
            [          Android Device          ]

            [Mock BLE Communication <-> Android App]
```

### Mock BLE Communication Module

This will function as a simulated back-end to temporarily replace communication with our device to avoid relying on bluetooth or hardware dependencies.

This module doesn't exist yet, but will be created and pushed to the repository by Friday 12/20/19.

The interface to your code will be something like:

```java
// Rx
public void onUartRx(@NonNull byte[] data);

// Tx
public void sendUartTx(@NonNull byte[] data);
```

---
