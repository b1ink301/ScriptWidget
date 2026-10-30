plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":domain"))
    // Multiplatform artifact: resolves to the JVM variant (with desktop natives) here and in tests,
    // and to the Android variant when consumed by the app.
    implementation(libs.quickjs.kt)
    implementation(libs.okhttp)
    implementation(libs.datastore.preferences.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(testFixtures(project(":domain")))
}

// Compatibility report against community scripts (kept outside the repo):
// ./gradlew :data:test --tests '*CompatibilityReport*' -PcompatCorpus=/path/to/scripts
// Checking a community gallery catalog: ./gradlew :data:test --tests '*CatalogFileCheck*' -Pcatalog=/path/to/index.json
tasks.test {
    systemProperty("compat.corpus", providers.gradleProperty("compatCorpus").getOrElse(""))
    inputs.property("compatCorpus", providers.gradleProperty("compatCorpus").getOrElse(""))
    systemProperty("catalog.file", providers.gradleProperty("catalog").getOrElse(""))
    inputs.property("catalog", providers.gradleProperty("catalog").getOrElse(""))
}
