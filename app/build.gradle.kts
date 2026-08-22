plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.corewall.qaqc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.corewall.qaqc"
        minSdk = 26
        targetSdk = 35
        versionCode = 74
        versionName = "11.23"

        // PDFium مكتبة أصلية، ومعاها ٤ معماريات = ١٨ ميجا. الأجهزة الحقيقية
        // كلها ARM؛ الـx86 للمحاكيات بس. بنشيلهم فبنوفّر ١٠ ميجا من الـAPK.
        // لو احتجت تشغّل على محاكي Intel، ضيف "x86_64" هنا.
        ndk {
            // محرك LibreDWG المحلي يشمل ARM64 المستهدف (Galaxy S25 Ultra)
            // مع الإبقاء على ARMv7 كي لا تتعطل الأجهزة التي كانت مدعومة سابقاً.
            abiFilters.clear()
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }

        // بصمة الكوميت جوّه التطبيق.
        //
        // السبب عملي: النسخ بتتوزّع كملف APK مباشر، والسؤال "انت شغّال على
        // أنهي بناء؟" بيتكرر في كل مرة. الكوميت في شاشة "عن التطبيق"
        // بيجاوبه من غير تخمين، وبيربط اللي على الجهاز باللي في الريبو.
        // الـCI بيبعت `BUILD_COMMIT`؛ البناء المحلي بيكتب "local".
        buildConfigField(
            "String",
            "BUILD_COMMIT",
            "\"${System.getenv("BUILD_COMMIT") ?: "local"}\""
        )
    }

    // مفتاح توقيع ثابت متسجّل في الريبو — كل بناء بيتوقّع بنفس المفتاح،
    // فأي تحديث بيتثبت فوق القديم من غير ما تمسحه أو تفقد بياناتك.
    signingConfigs {
        create("stable") {
            storeFile = file("stable-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // مطلوب من AGP 8: من غيره `BuildConfig` مابيتولّدش أصلاً.
        buildConfig = true
    }
    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // BouncyCastle بيشحن نسخ متعددة من نفس ملفات الميتاداتا (واحدة لكل
    // إصدار Java)، والدمج بيقع عليها. مالهاش أي أثر وقت التشغيل.
    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/license.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/notice.txt",
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        )
    }
}

// بيخلّي Room يكتب المخطط المتولّد على القرص. الفايدة مش أرشيفية:
// سكربت `tools/check_room_schema.py` بيقارن الفهارس اللي الترحيل بيعملها
// بالفهارس اللي Room متوقّعها، فأي اختلاف بيوقّع البناء بدل ما يطلع APK
// بيقفل أول ما يفتح.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // اختبارات وحدة على الـJVM — من غير أندرويد. الغرض تثبيت محرّك
    // الحساب وهندسة الـPDF: أي تحسين أداء لازم يفضل بيطلع نفس الأرقام.
    testImplementation("junit:junit:4.13.2")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ─────────────────────────────────────────────────────────────
    // مكتبات بتحلّ محل كود مكتوب بالإيد.
    //
    // التطبيق كان ١٥ اعتماد ومفيش ولا واحد منهم بيعمل شغل واجهة أو
    // وسائط أو شبكة — يعني الـHTTP والصور والماركداون والـPDF والرسوم
    // والإيماءات كلهم متكتبين من الصفر (حوالي ٤٠٠٠ سطر). ده مش توفير،
    // ده صيانة دايمة لحاجة محلولة برّه.
    //
    // النسخ هنا متختارة لتوافق Compose 1.7 (BOM 2024.09.03) وKotlin
    // 2.0.21 — مش الأحدث على الإطلاق. ترقية الـtoolchain مشروع لوحده
    // وملهاش عائد على المستخدم دلوقتي.
    // ─────────────────────────────────────────────────────────────

    // تحميل الصور: كاش ذاكرة وقرص وإلغاء تلقائي مع التمرير.
    // بيحلّ محل rememberThumb المكتوب بالإيد (سبب تهتهة شبكة الملفات).
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")

    // شبكة + SSE. من غير ده الردود المتدفّقة مستحيلة أصلاً:
    // HttpURLConnection بيقرا الجسم كله قبل ما يفكّه.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // رسوم بيانية — بديل الرسم اليدوي على Canvas.
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-beta.3")

    // عارض ماركداون — بديل الـparser المكتوب بالإيد.
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.27.0")

    // ─────────────────────────────────────────────────────────────
    // محرّك الـPDF: PDFium (نفس المحرّك اللي في كروم).
    //
    // ليه مش `android.graphics.pdf.PdfRenderer` اللي كنا بنستخدمه:
    // مالوش **طبقة نص** خالص. يعني البحث والتحديد والنسخ مش ناقصين —
    // هما مستحيلين معماريّاً عليه. وكمان مابيعرفش يرندر جزء من الصفحة،
    // فالتكبير كان بيمطّط صورة واحدة والرسمة بتضيع بالظبط لما تقرّب.
    //
    // النسخة 1.0.30 بالذات مش الأحدث، وده مقصود: الإصدارات بعدها متبنية
    // على Kotlin 2.2+ و2.4، وإحنا على 2.0.21 — الميتاداتا بتاعتها
    // مابتتقريش من كومبايلر أقدم، فالبناء بيقع. و1.0.30 كمان مابتجرّش
    // Guava وراها (اللي بعدها بيجرّوها).
    implementation("io.legere:pdfiumandroid:1.0.30")

    // تحرير بنية المستند: PDFBox.
    //
    // PDFium بيرسم وبيقرا بس — الواجهة اللي المكتبة بتكشفها مافيهاش إنشاء
    // تعليقات ولا دمج ولا علامة مائية. PDFBox بيشتغل على مستوى كائنات
    // الـPDF نفسها، فهو اللي بيقدر يكتب `/Annots` حقيقية بمظهر (`/AP`)
    // تفتح في Acrobat وتتعدّل، بدل ما نرستر الصفحة لصورة.
    //
    // التكلفة صريحة: ~٣ ميجا للمكتبة + ٤.٧ ميجا خطوط وموارد في الأصول +
    // BouncyCastle (بييجي كاعتماد). سايبين BouncyCastle مكانه عن قصد —
    // شيله بيوفّر مساحة بس بيخلّي أي ملف متشفّر يرمي `NoClassDefFoundError`
    // في يد المستخدم بدل رسالة مفهومة.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // التعرّف الضوئي على النص: Tesseract عن طريق tess-two.
    //
    // ليه المكتبة دي بالذات: هي الوحيدة على Maven Central اللي بتدعم
    // **العربي** وبتشتغل بالكامل على الجهاز. البديل الأخف (ML Kit) مالوش
    // عربي خالص، وTesseract 4 (أحدث وأدق وحزمه أصغر بكتير) مش منشور
    // غير على JitPack — ومش هنبني على مكتبة ما قدرناش نفحص واجهتها.
    //
    // المحرّك هنا 3.05، يعني حزم اللغة لازم تبقى من فرع `3.04.00`
    // (الحزم الأحدث LSTM بيرفضها بصمت). الحزم **مابتتشحنش مع التطبيق** —
    // بتتحمّل من الإعدادات لما المستخدم يطلب.
    implementation("com.rmtheis:tess-two:9.1.0")
}
