import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing: `../keystore/scriptwidget.properties` (outside the repo, like the other apps'
 * key files) or, e.g. on CI, the SCRIPTWIDGET_* environment variables. Without either, release
 * builds are left unsigned.
 */
val releaseSigning: Map<String, String>? = run {
    val file = rootProject.file("../keystore/scriptwidget.properties")
    if (file.exists()) {
        val props = Properties().apply { file.reader().use(::load) }
        val storeFile = props.getProperty("storeFile")?.let { path ->
            File(path).takeIf { it.isAbsolute } ?: file.parentFile.resolve(path)
        }
        mapOf(
            "storeFile" to requireNotNull(storeFile) { "storeFile missing in $file" }.path,
            "storePassword" to requireNotNull(props.getProperty("storePassword")) { "storePassword missing in $file" },
            "keyAlias" to requireNotNull(props.getProperty("keyAlias")) { "keyAlias missing in $file" },
            "keyPassword" to requireNotNull(props.getProperty("keyPassword")) { "keyPassword missing in $file" },
        )
    } else {
        val env = mapOf(
            "storeFile" to System.getenv("SCRIPTWIDGET_KEYSTORE_FILE"),
            "storePassword" to System.getenv("SCRIPTWIDGET_KEYSTORE_PASSWORD"),
            "keyAlias" to System.getenv("SCRIPTWIDGET_KEY_ALIAS"),
            "keyPassword" to System.getenv("SCRIPTWIDGET_KEY_PASSWORD"),
        )
        env.takeIf { values -> values.values.all { !it.isNullOrBlank() } }?.mapValues { it.value!! }
    }
}

android {
    namespace = "kr.b1ink.scriptwidget"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "kr.b1ink.scriptwidget"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // CI passes the release tag: -PversionName=1.2.3 -PversionCode=10203
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigning?.let { signing ->
            create("release") {
                storeFile = file(signing.getValue("storeFile"))
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Release APKs per ABI (QuickJS is native, ~0.8MB per ABI) plus a universal one. Debug builds
    // keep a single APK so installing from the IDE and device tests are unchanged.
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data-android"))
    implementation(project(":widget"))
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.work.testing)
    androidTestImplementation(testFixtures(project(":domain")))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    // compose-ui-test pulls Espresso 3.5, which can't inject input on recent Android versions.
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
