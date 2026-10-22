# Vidio on the Mi TV Stick FHD: the APKPure split-APK trap

**Symptom:** every Vidio Android TV build sideloaded onto the stick crashed
on launch with:

```
java.lang.UnsatisfiedLinkError: No implementation found for
java.lang.String com.vidio.androidtv.data.other.TvNdkConfig.appsflyerDevKeyBase64(...)
```

**Wrong first theory:** Android 9 incompatibility. It isn't.

**Actual cause:** APKPure's `/b/APK/<package>` endpoint serves only the
**base APK** of a split app — no `config.armeabi_v7a` split, so the native
library the app needs at startup is simply absent and `TvApplication`
crashes in `onCreate`.

**Fix:** download the **full XAPK** instead
(`https://d.apkpure.com/b/XAPK/com.vidio.android.tv?versionCode=<code>`,
~24 MB for v2.69.10 / versionCode 981) and install all splits together:

```bash
# unzip the .xapk, then:
adb install-multiple base.apk config.armeabi_v7a.apk config.en.apk \
                     config.in.apk config.xhdpi.apk
```

Verified working on the Mi TV Stick FHD (Android TV 9, PI.2055): the app
launches and reaches its sign-in screen (QR / pairing code / Google /
Guest).

Lesson for any split-packaged TV app (increasingly all of them): if a
sideload crashes with `UnsatisfiedLinkError` in an NDK config class,
suspect a missing native split before you suspect the Android version.
