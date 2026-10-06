# Documentation screenshots

This fixture produces real PiCal UI screenshots with fictional project data. It uses
the existing Compose screens, repository validation, and Room implementation.

The normal Android build files and production application code are not changed.
The init script adds documentation-only sources and a debug manifest overlay,
and sets the package to **`com.ahmedismail.flowtrack.readme`**. The launcher label
is **PiCal Docs Preview**, while the screen branding stays PiCal.

## Build and capture

```powershell
.\gradlew.bat :app:assembleDebug --init-script tools/readme-preview/init.gradle
```

Confirm the merged manifest package is `com.ahmedismail.flowtrack.readme` before
installation. Copy the resulting APK to a temporary location; it must not replace
`output/apk/PiCal-1.1-debug.apk`.

```text
adb install -r <temporary-preview.apk>
adb shell am start -n com.ahmedismail.flowtrack.readme/com.ahmedismail.flowtrack.MainActivity
```

The private seed provider checks that exact isolated package before writing any
data. It creates a fictional cooling-water project, two areas, five demo team
members, four workflow stages, twelve validated progress records, and sample
attendance. No real project, contact, or inspection data is used.

Capture with Android's screenshot command and UI hierarchy tools. The optional
`tools/phone_review.py` helper accepts these environment variables:

- `ANDROID_SERIAL`: the connected device serial.
- `PICAL_REVIEW_DIR`: a workspace-relative capture directory.
- `PICAL_EXPECTED_PACKAGE`: set to `com.ahmedismail.flowtrack.readme` to stop
  automation when another application is foreground.

Numeric field entry is checked against the UI value after typing. Calculator
examples use explicitly entered coefficients; they are not material approvals.
README screenshot assets are unaltered device captures copied to `docs/images/`.

## Finish

Remove only the documentation package:

```text
adb uninstall com.ahmedismail.flowtrack.readme
```

Rebuild normally, without the init script, to restore the standard APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Check that the standard merged manifest has package `com.ahmedismail.flowtrack`,
and that `ReadmeSeedProvider` is absent from its DEX files. The preview seed must
never be distributed in a normal application build.
