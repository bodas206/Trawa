# Patrix

نسخة مصدر أولية لتطبيق **Patrix Version 1** بواجهة مرنة للهاتف والـiPad والمحاكي.

## المنفذ في هذه النسخة

- package: `com.patrix.app`
- اسم التطبيق: `Patrix`
- واجهة Login مرتبطة بمفتاح وصول.
- التحقق من المفتاح عبر Supabase Edge Function باستخدام HTTPS.
- تخزين بصمة المفتاح SHA-256 فقط في قاعدة البيانات، وليس المفتاح الخام.
- ربط المفتاح بأول جهاز يستخدمه.
- Main View بألوان مستوحاة من الشعار المرفق.
- بطاقة Free Fire 1.132.1 مع دعم 32/64 bit كنص واجهة.
- زر Start يطلب صلاحية Android VPN.
- عرض الجهاز المتصل، حالة المفتاح، وتاريخ الانتهاء.
- زر الحصول على المفتاح يفتح: https://web.telegram.org/k/#@Just_AbDoS
- `minifyEnabled` و`shrinkResources` مفعّلان في release.

## Supabase

- المشروع: `Trawa`
- Edge Function: `patrix-validate-key`
- Endpoint: `https://gnoiwuhdjyilqycfoorh.supabase.co/functions/v1/patrix-validate-key`
- جداول داخلية محمية بـRLS: `patrix_licenses`, `patrix_login_audit`

## ملاحظات مهمة

هذه النسخة تنفّذ طبقة الواجهة والتحقق وصلاحية VPN. خدمة الـVPN نفسها ما زالت هيكلًا أوليًا ولا تحتوي على محرك tunnel أو إعدادات الخادم الفعلية، لأن بيانات الخادم غير موجودة في الملفات المرفقة. لا يمكن ضمان أن يكون أي APK غير قابل للهندسة العكسية بشكل مطلق؛ الحماية العملية تكون عبر HTTPS، عدم وضع أسرار Supabase داخل التطبيق، R8/obfuscation، توقيع release، والتحقق من سلامة النسخة.

## البناء

افتح المجلد في Android Studio ثم نفّذ:

```bash
./gradlew assembleRelease
```

بيئة التنفيذ الحالية لا تحتوي Android SDK/Gradle، لذلك تم تسليم مشروع المصدر جاهزًا للبناء بدل ادعاء إنشاء APK لم يتم اختباره.
