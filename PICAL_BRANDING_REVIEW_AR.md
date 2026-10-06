# PiCal — تحديث الهوية وشاشة حول التطبيق

6 أكتوبر 2026 — الإصدار 1.1، البناء 2.

- تغيير الاسم الظاهر إلى PiCal في اللغتين، وعناوين جميع مولدات PDF وأسماء ملفاتها وإشعارات التقارير.
- أيقونة π من أنابيب معدنية بإضاءة سماوية وكهرمانية، مع أحجام launcher وأيقونة adaptive وطبقة monochrome لـ Android 13 فأحدث. الشعار يظهر داخل رأس التطبيق وصفحة التعريف.
- شاشة «حول PiCal»، متاحة من زر المعلومات أعلى الشاشات. تتضمن التعريف والمزايا والإصدار والمطور ومراجع الحسابات وحدود الإجهاد الآلي وشرح حفظ البيانات. تدعم العربية والإنجليزية والتمرير والرجوع إلى الشاشة السابقة.
- يبقى معرّف Android وقاعدة البيانات كما هما لتثبيت التحديث فوق التطبيق الموجود والاحتفاظ ببياناته؛ الاسم الداخلي القديم في الحزمة ليس اسمًا ظاهرًا للمستخدم.
- اسم التطبيق الجديد يطبق على الملفات المولدة بعد التحديث؛ التقارير السابقة المحفوظة لم يعاد تحريرها.

التحقق: 93 اختبار وحدة بلا فشل، بناء ناجح، Lint بلا أخطاء. تثبيت وفحص على HONOR MTN-NX1 Android 16. اختبرت اللغتين والتنقل والرجوع، وولدت وفحصت 13 عينة PDF تحمل PiCal دون الاسم السابق. استبعدت أداة معاينة PDF من APK النهائي.

APK: [PiCal-1.1-debug.apk](output/apk/PiCal-1.1-debug.apk). الأدلة في `ui-test/20261006/pical`، والأصل الشفاف في [pical-icon.png](assets/branding/pical-icon.png).

## إنشاء الأيقونة

أُنشئت باستخدام أداة imagegen المدمجة، ثم جهزت أحجام موارد Android بواسطة `tools/prepare_launcher_icon.py`. لم تُستخدم CLI لتوليد الصورة.

النص النهائي المستخدم للتوليد:

> Use case: logo-brand. Asset type: production Android launcher foreground icon for PiCal, a piping and mechanical engineering calculator. Create one centered iconic lowercase mathematical pi symbol π built from thick polished steel pipes, clear unmistakable π silhouette with horizontal top beam and two separated vertical legs, restrained teal/cyan and amber flow highlights within the pipes. Premium industrial engineering aesthetic, simple geometry and clean bold silhouette readable at 48px, front facing orthographic view, restrained metallic depth, no perspective scene. Navy and teal app palette with small amber accents. Transparent background; isolated symbol only, fills central 80% of square artwork with generous transparent margin. No rounded-square tile, no surrounding frame, no text or lettering, no watermark, no tiny details, no floor, no cast shadow outside symbol.

## زر التواصل

أضيف زر «تواصل عبر LinkedIn» داخل بطاقة التعريف، بالعربية والإنجليزية. يفتح `https://www.linkedin.com/in/ahmed-ismail-soliman` عبر المتصفح أو LinkedIn، مع رسالة مفهومة إذا لم يتوفر تطبيق لفتح الرابط.

تم تثبيت تحديث زر التواصل على هاتف HONOR واختبار الضغط عليه: فتح تطبيق LinkedIn صفحة Ahmed Ismail Soliman بنجاح، ثم أعيد الهاتف إلى PiCal. لم ترسل رسائل أو تعدل بيانات الملف الشخصي.
