# Operit Translate — مترجم فوري للفيديو والكلام

تطبيق أندرويد لترجمة الكلام والصوت بشكل فوري، مع طبقة عائمة فوق كل التطبيقات.
**كل النماذج والـ APIs تختارها أنت من داخل التطبيق** — لا شيء مثبّت في الكود.

## المزايا
- التقاط من **الميكروفون** و/أو **صوت النظام** (MediaProjection).
- تحويل الكلام لنص (STT) عبر أي خدمة متوافقة مع OpenAI.
- ترجمة (MT) عبر أي خدمة متوافقة مع OpenAI (chat/completions).
- عرض النص الأصلي + الترجمة، مع طبقة عائمة (Overlay) فوق أي تطبيق.
- كشف سكوت بسيط (VAD) لتقليل الطلبات.

## البناء
```bash
# افتح المجلد في Android Studio (Ladybug أو أحدث)، ثم:
./gradlew assembleDebug
# أو من Android Studio: Build > Build Bundle(s)/APK(s) > Build APK(s)
```
الناتج: `app/build/outputs/apk/debug/app-debug.apk`

## أول تشغيل
1. افتح تبويب **الإعدادات**.
2. أدخل:
   - **STT**: Endpoint + Key + Model (افتراضياً OpenAI `whisper-1`).
   - **MT**: Endpoint + Key + Model (افتراضياً OpenAI `gpt-4o-mini`).
   - لغات المصدر/الهدف.
   - **مصدر الصوت**: ميكروفون / صوت النظام / الاثنان.
3. احفظ، ثم ارجع لتبويب **الترجمة** واضغط **بدء**.

## قيود مهمة (اقرأها)
- **صوت النظام**: يعمل فقط مع التطبيقات التي تسمح بـ `allowAudioPlaybackCapture`.
  يوتيوب ونتفليكس وسبوتيفاي **محجوبون** غالباً بسبب DRM. الحل العملي: شغّل الصوت على
  السماعة واستخدم **الميكروفون**.
- الطبقة العائمة تحتاج إذن `SYSTEM_ALERT_WINDOW`.
- الترجمة الفورية تعتمد على سرعة الـ API — قد تكون هناك تأخيرة بسيطة.

## الملفات الرئيسية
- `audio/MicCapture.kt` — التقاط الميكروفون + VAD.
- `audio/SystemAudioCapture.kt` — التقاط صوت النظام.
- `net/ApiClient.kt` — STT + MT (متوافق OpenAI).
- `service/CaptureService.kt` — الخط: صوت → نص → ترجمة → عرض.
- `overlay/OverlayService.kt` — الطبقة العائمة.
- `ui/MainScreen.kt` + `ui/SettingsScreen.kt` — الواجهة.

> ملاحظة: راجع الكود وعدّل ما يلزم. أنا كتبت المشروع كاملاً لكن لم أبنِه/أختبره فعلياً
> على جهاز — تحتاج تفتحه في Android Studio وتبنيه.
