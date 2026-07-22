# Core Wall QA/QC — Android Native

تطبيق أندرويد **Native** (Kotlin + Jetpack Compose + Room) — منصة أدوات للكور وول
بتختار الأداة من القائمة الجانبية (Drawer) والتبويبات السفلية بتتغير حسب الأداة:

1. **Corewall Reinforcement** — متابعة تسليح الكور وول دور بدور: مسقط تفاعلي،
   جداول مدايات التسليح (31 حائط + 49 كمرة على 48 دور)، حالات فحص، كومنتات،
   Attention diff، وتصدير PDF/PNG.
2. **Corewall Counting** — عدّ الأسياخ الرأسية: دوس على أي جدار وسجّل العدد
   والقطر (الموقع والشوب دروينج)، الأعداد بتظهر على البلان بصيغة `22Ø12` في منتصف
   الجدار موازية له وبحجم نسبي من البلان، ريبورت إجماليات لكل قطر، وتصدير
   الدروينج بالأعداد PDF/PNG.

إضافة أداة جديدة = قيمة في `AppModule` + تبويباتها وشاشاتها في `MainActivity` —
الدروار والتبويبات بيتظبطوا تلقائي. كل البيانات بتتحفظ محليًا في Room.

## هيكل المشروع

```
app/src/main/
├── assets/
│   ├── plan-elements.json      # إحداثيات وفئات الـ63 عنصر + viewBox (read-only)
│   └── schedule-data.json      # جداول التسليح وقايمة الأدوار الـ48 (read-only)
└── java/com/corewall/qaqc/
    ├── MainActivity.kt         # Scaffold + التبويبات الأربعة
    ├── MainViewModel.kt        # MVVM: StateFlow لكل حالة التطبيق
    ├── data/
    │   ├── model/              # موديلات JSON (kotlinx-serialization)
    │   ├── db/                 # Room: أسماء، حالات فحص، كومنتات، تعديلات قيم
    │   ├── AppRepository.kt    # دمج بيانات الأصول مع تعديلات Room
    │   └── SettingsStore.kt    # الثيم وإعدادات العرض
    ├── domain/
    │   ├── ScheduleLogic.kt    # activeRange: حوائط (غير شاملة النهاية) / كمرات (شاملة) + كشف فجوات البيانات
    │   ├── AttentionDiff.kt    # Diff تلقائي للتسليح بين الأدوار المتجاورة
    │   └── SteelCalculator.kt  # T25-200 → قطر × تباعد → مساحة/متر، و6T32 → مساحة إجمالية
    ├── ui/
    │   ├── plan/               # المسقط التفاعلي (Canvas + zoom/pan/tap) + Sheet التفاصيل والتسمية + التصدير
    │   ├── attention/          # تبويب Attention
    │   ├── tools/              # بحث + حاسبة حديد + ملخص الدور
    │   ├── settings/           # 3 ثيمات + نسخة احتياطية JSON
    │   └── theme/              # iOS فاتح / دارك OLED / Blueprint بحواف حادة
    └── export/PlanExporter.kt  # PDF (PdfDocument) وPNG (Bitmap) — دور واحد أو مقارنة دورين
```

## التشغيل محليًا

المتطلبات: JDK 17+ وAndroid SDK (compileSdk 35). أسهل طريقة: افتح المشروع في
Android Studio (Ladybug أو أحدث) وشغّل على جهاز/محاكي، أو من الطرفية:

```bash
./gradlew assembleDebug
# الـAPK هيطلع في: app/build/outputs/apk/debug/app-debug.apk
```

## تحميل الـAPK الجاهز

كل push بيبني الـAPK تلقائيًا عبر GitHub Actions (`.github/workflows/build-apk.yml`):

1. **لينك ثابت**: من صفحة [Releases](../../releases) — release اسمه
   **latest-debug** بيتحدّث مع كل بناء، وفيه `app-debug.apk` جاهز للتثبيت.
2. أو من تبويب **Actions** → آخر run ناجح → artifact باسم `corewall-qaqc-debug-apk`.

النسخة Debug موقّعة بمفتاح Android الافتراضي — كافية للاستخدام الميداني، مش محتاجة توقيع رسمي.

## ملاحظات

- بيانات الجداول في `assets/` بتتقرا read-only، وأي تعديل يدوي بيتخزن في Room
  كـ"فرق" فوقها — تقدر ترجّع الأصل في أي وقت.
- فجوات البيانات الحقيقية في الجدول الأصلي (زي T1-W2C بين 3M و06) بتظهر
  كتحذير واضح في الـSheet وتبويب Attention — مش بتختفي بصمت.
- عناصر الفئة `other` (3 عناصر) لسه نوعها غير مؤكد — معروضة مؤقتًا بلون
  البيمات الداخلية (فيه TODO في الكود).
