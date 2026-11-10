# Isolated Android acceptance test

This harness uses Android Instrumentation, the real Room database/repository, actual
PDF generators and the unchanged application screens. It is compiled only with
the explicit init script. It never targets the user's production application.

Packages:

- Debug: `com.ahmedismail.flowtrack.acceptance`
- Release: `com.ahmedismail.flowtrack.acceptance.release`

The instrumentation refuses any other package. `phase=full` requires an empty
isolated database and builds fictional project data. `phase=verify` checks that
the same records survived restart or package upgrade and regenerates the reports.

```powershell
.\gradlew.bat :app:assembleDebug --init-script tools/acceptance/init.gradle
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am instrument -w -e phase full com.ahmedismail.flowtrack.acceptance/com.ahmedismail.flowtrack.AcceptanceInstrumentation
```

For a signed Release test (configure signing first):

```powershell
.\gradlew.bat :app:assembleRelease --init-script tools/acceptance/init.gradle -PacceptanceVersionCode=3
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am instrument -w -e phase full com.ahmedismail.flowtrack.acceptance.release/com.ahmedismail.flowtrack.AcceptanceInstrumentation
.\gradlew.bat :app:assembleRelease --init-script tools/acceptance/init.gradle -PacceptanceVersionCode=4
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am instrument -w -e phase verify com.ahmedismail.flowtrack.acceptance.release/com.ahmedismail.flowtrack.AcceptanceInstrumentation
```

The override version applies only with this init script. Both acceptance packages
are separate from `com.ahmedismail.flowtrack`; never clear or uninstall the latter
as part of this test.

Pull `<external-files>/acceptance` from the isolated package. The canonical fixtures
are 13 calculator PDFs plus project, empty-project, welding, employee and empty-
employee PDFs. `verify_reports.py <pulled-directory>` checks values and page bounds;
render and visually inspect every page as a separate step.

After testing, uninstall only the two acceptance packages and rebuild without
the init script. Verify that no instrumentation or fixture classes remain in the
distribution APK. See [Release instructions](../../docs/RELEASE.md).
