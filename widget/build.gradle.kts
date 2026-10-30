plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    // Existing findings are recorded in lint-baseline.xml; new errors fail the build (CI runs lintDebug).
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkDependencies = false
    }

    namespace = "kr.b1ink.scriptwidget.widget"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Presentation depends only on the domain; concrete data implementations are wired in :app.
    api(project(":domain"))
    api(libs.glance.appwidget)
    api(libs.work.runtime.ktx)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)

    androidTestImplementation(testFixtures(project(":domain")))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.glance.appwidget.testing)
}
