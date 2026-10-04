import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.spotless)
}

android {
    namespace = "dev.devdigi.music"
    compileSdk = 36
    testBuildType = providers.gradleProperty("unitTestBuildType").orElse("debug").get()

    defaultConfig {
        applicationId = "dev.devdigi.music"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        absolutePaths = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.androidx.datastore.preferences.core)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(
        platform(libs.androidx.compose.bom),
    )
    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4,
    )
    androidTestImplementation(
        libs.androidx.test.ext.junit,
    )
    androidTestImplementation(
        libs.androidx.test.runner,
    )
}

tasks.register<Test>("navidromeIntegrationTest") {
    val debugUnitTest =
        tasks.named<Test>("testDebugUnitTest")

    group = "verification"
    description =
        "Runs the Docker-backed synthetic Navidrome JVM integration test."

    dependsOn(debugUnitTest)

    testClassesDirs =
        debugUnitTest.get().testClassesDirs
    classpath =
        debugUnitTest.get().classpath

    filter {
        includeTestsMatching(
            "dev.devdigi.music.integration.NavidromeIntegrationTest",
        )
    }

    systemProperty(
        "navidrome.integration",
        "true",
    )

    reports.junitXml.outputLocation.set(
        layout.buildDirectory.dir(
            "test-results/navidromeIntegrationTest",
        ),
    )

    reports.html.outputLocation.set(
        layout.buildDirectory.dir(
            "reports/tests/navidromeIntegrationTest",
        ),
    )

    outputs.upToDateWhen { false }
}

spotless {
    ratchetFrom("origin/develop")
    kotlin {
        // Android source sets are not inferred reliably by Spotless.
        target("src/**/*.kt")
        ktlint("1.8.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
