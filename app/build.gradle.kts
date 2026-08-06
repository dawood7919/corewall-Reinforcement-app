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
        versionCode = 35
        versionName = "8.2"
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
}
