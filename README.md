# Operit Translate v2

مترجم أندرويد — نص + مستندات (TXT / MD / HTML / EPUB / PDF)، ست محركات، ومفتاح خاص اختياري.

## الميزات
- **6 محركات ترجمة** جاهزة، تختار واحد من الإعدادات:
  - `Google (مجاني)` — بدون مفتاح
  - `Bing (مجاني)` — بدون مفتاح
  - `MyMemory (مجاني)` — بدون مفتاح
  - `OpenAI متوافق` — مفتاح + endpoint + اسم موديل (Groq / OpenRouter / DeepSeek / أي API متوافق)
  - `Google Gemini` — مفتاح
  - `LibreTranslate` — endpoint (اختياري مفتاح)
- **ترجمة نص** سريعة من تبويب «نص».
- **ترجمة مستندات**: تختار ملف (TXT / Markdown / HTML / EPUB / PDF) ويترجمه فقرة فقرة مع شريط تقدّم.
- **نمط العرض**: «أصل + ترجمة» (الترجمة تحت الأصل) أو «ترجمة فقط» — قابل للتغيير بأي وقت.
- **تصدير**: TXT أو HTML ومشاركته من التطبيق.
- كاش داخلي للترجمة قابل للتعطيل.

## ملاحظات صريحة
- استخراج النص من PDF يعتمد على Android PdfRenderer (يعمل مع PDF فيه طبقة نص). ملفات PDF المصوّرة (صورة فقط) تحتاج OCR.
- ترجمة الفيديو/الصوت المباشر (YouTube/Netflix…) غير مدعومة بسبب DRM وحقوق البث؛ الاعتماد الأساسي هنا على النص والمستندات.

## البناء
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
./gradlew :app:assembleDebug
# الناتج: app/build/outputs/apk/debug/app-debug.apk
```

يتطلب Android SDK (android-34). على معالجات ARM استخدم aapt2 من Maven (انظر `gradle.properties`).

## البنية
```
com.operit.translate
├─ Config.kt              إعدادات + مفاتيح
├─ Translator.kt          محرك الترجمة النصية (تقطيع + كاش)
├─ engine/                المحركات الستة + السجل
├─ doc/                   قراءة/كتابة/ترجمة المستندات
└─ ui/MainScreen.kt       الواجهة (نص / مستندات / إعدادات)
```