# Android PDF preview harness

`ReportPreviewMain.kt` is a standalone QA entry point, excluded from every app source set.
It generated the four PDF fixtures on the connected Android 16 phone using the production
report generator without installing or updating the application.

The shell `app_process` environment lacks Zygote font initialization on this device. The
harness initializes Roboto and Noto Naskh Arabic from system font files. Its reflection is
device-specific and must not be included in an installed app (Android Lint correctly
flags those private APIs for application use).

For a repeat, build a throwaway debug APK with
`gradlew.bat :app:assembleDebug -PflowtrackReportPreview=true`.
This opt-in property adds this tools directory only to the debug source set.
Push that APK to a dedicated `/data/local/tmp` directory, mark it read-only and invoke:

```text
CLASSPATH=/data/local/tmp/<review-directory>/review.apk app_process /system/bin com.ahmedismail.flowtrack.ReportPreviewMain /data/local/tmp/<review-directory>
```

Do not install this throwaway APK. Rebuild without the property before delivery and verify
that `ReportPreviewMain.class` is absent from normal debug classes. Verify all thirteen
`REPORT_OK` messages (B31.3/B31.1 pass, fail, missing and long-reference cases, bend, vessel, both miter reports and weight),
pull the test PDFs, render every page, and check status, numbers, wrapping and pagination. The Python
inspection script currently lives at `tmp/verify_finished_reports.py`.

Example inputs are synthetic, not an actual material approval or physical pipe inspection.
