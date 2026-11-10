# PiCal Release 1.2

الإصدار: **1.2** — `versionCode=3` — الحزمة `com.ahmedismail.flowtrack`.

ملف التوزيع المباشر: [PiCal-1.2-release.apk](../output/releases/PiCal-1.2-release.apk).

## التوقيع

- مفتاح Release مستقل، RSA 3072، داخل `.signing/pical-release.p12`.
- إعدادات البناء المحلية في `.signing/keystore.properties`. كلمات المرور لم تطبع في سجل التنفيذ، وجرى تقييد صلاحيات المجلد على حساب Windows الحالي وSYSTEM.
- الملفان خاصان ومحجوبان عبر `.gitignore`. احفظ نسخة احتياطية آمنة من **المجلد كاملًا**، ولا ترفعه إلى مستودع أو ترسله مع APK. يلزم نفس المفتاح لتحديث إصدارات التوزيع لاحقًا.
- الشهادة العامة وبصمة SHA-256 ونتيجة `apksigner` موجودة بجوار APK في `output/releases/`؛ هذه ليست المفتاح الخاص.
- التوقيع النهائي تحقق بـ APK Signature Scheme v2، المناسب للحد الأدنى API 24. التطبيق غير قابل للتصحيح، ولا يتضمن أدوات القبول أو البيانات التجريبية.

## إعادة البناء

استخدم JDK 17 أو JDK 21 المتوافق مع Gradle 8.7. Java 25 الموجود في نسخة Android Studio الحالية لا يناسب هذا المشروع دون تحديث أدوات البناء.

```powershell
$env:JAVA_HOME = 'C:\Users\PC\.jdks\jbr-21.0.11'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
```

يقرأ Gradle ملف `.signing/keystore.properties` افتراضيًا. يمكن تحديد ملف آخر بمتغير البيئة `PICAL_SIGNING_PROPERTIES`. مسار `storeFile` نسبي إلى ملف الخصائص نفسه. يوجد قالب بلا أسرار في [keystore.properties.example](../keystore.properties.example).

يمنع `checkReleaseSigning` إخراج نسخة توزيع بلا إعدادات توقيع. لا تعِد تشغيل `tools/create_release_key.ps1` لاستبدال المفتاح؛ الأداة ترفض استبدال الملفات الموجودة.

خرج Gradle الأصلي: `app/build/outputs/apk/release/app-release.apk`.

```powershell
& 'E:\Coding\SDK\build-tools\34.0.0\apksigner.bat' verify --verbose --print-certs output/releases/PiCal-1.2-release.apk
& 'E:\Coding\SDK\build-tools\34.0.0\zipalign.exe' -c -v 4 output/releases/PiCal-1.2-release.apk
Get-FileHash output/releases/PiCal-1.2-release.apk -Algorithm SHA256
```

## التثبيت والتحديث

نسخ Release القادمة الموقعة بنفس المفتاح وبـ versionCode أعلى يمكن تثبيتها كتحديث. تحقق ذلك فعليًا على الهاتف باستخدام حزمة قبول معزولة، من رقم بناء 3 إلى 4، مع بقاء المشروع والمراحل والسجلات والحضور.

**نسخة Debug القديمة لها شهادة مختلفة**؛ APK الحالي لا يثبت فوقها مباشرة. لم تُحذف النسخة الموجودة أو بياناتها. لا تزل Debug إذا كانت تحتوي بيانات مهمة قبل ترتيب الاحتفاظ بها؛ نقل قاعدة البيانات بين الشهادتين ليس ميزة منفذة في التطبيق.

## نتيجة القبول

راجع [تقرير القبول](../output/acceptance/ACCEPTANCE_20261021_AR.md). النسخة النهائية مبنية دون أي init script للمعاينة أو القبول، وتحقق استبعاد `AcceptanceInstrumentation` و`ReadmeSeedProvider` و`ReportPreviewMain` و`CalculatorReportFixtures` من DEX.
