import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    androidLibrary {
        namespace = "com.arjun.mooary"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder { sourceSetTreeName = "test" }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export(libs.kotlinx.datetime)
            binaryOption("bundleId", "com.arjun.mooary")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            // First-party Compose, and the reason is motion: material3 only pulls in
            // animation-core, so animateColorAsState and AnimatedContent are absent without
            // this. Segmented controls and empty-to-filled transitions pop as hard cuts
            // otherwise, which is the single most "cheap app" tell there is. Deliberately
            // *not* compose-material3-adaptive or a chart library -- see AGENTS.md.
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coil.compose)
            implementation(libs.compottie)
            implementation(libs.compottie.resources)

            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            // api: `diaryModules: List<Module>` is consumed by every platform entry point.
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.navigation.compose)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.koin.test)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver.android)
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.security.crypto)
            // The only Firebase SDK artifact in the project. Push is the one thing that
            // genuinely needs Google's transport; everything else Firebase-shaped here is
            // hand-rolled over REST from shared code. Inert unless `diary.firebaseMessaging`
            // is set, because the token is only requested when the registrar is asked.
            implementation(libs.firebase.messaging)
        }

        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqldelight.driver.sqlite)
        }

        // Desktop-only because it is the one target whose SQLite driver can be pointed at an
        // in-memory database from a plain unit test. These tests exist to catch SQL that is
        // valid but wrong -- a bare INSERT where an upsert was meant, say -- which no
        // compilation or mocked test can see.
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(libs.sqldelight.driver.sqlite)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.driver.native)
        }
    }
}

sqldelight {
    databases {
        create("DiaryDatabase") {
            packageName.set("com.arjun.mooary.data.local.db")
            generateAsync.set(false)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(false)
        }
    }
}

/**
 * The Cloud Messaging build flag, generated into commonMain.
 *
 * The flag reaches `:androidApp` as a manifest placeholder, which switches the messaging
 * *service* off. That is not enough on its own: the service is only the receiver, while the
 * token is fetched by an explicit `getToken()` call that shared code makes on every sign-in.
 * With the flag off, a default build would still reach for a token and write a device
 * document -- so "off" would mean "receives nothing" rather than "is not present", and the
 * feature could not be turned off without also shipping a build with the dependency removed.
 *
 * So the flag is compiled in rather than read at runtime. A constant is the honest encoding:
 * the decision is a property of the build, and a runtime probe could only ever answer "what
 * does the device think", which is a different question.
 */
val generateBuildFlags = tasks.register("generateBuildFlags") {
    group = "build"
    description = "Generates BuildFlags.kt carrying -Pdiary.firebaseMessaging."

    val enabled = providers.gradleProperty("diary.firebaseMessaging").orNull?.toBoolean() ?: true
    val outputDir = layout.buildDirectory.dir("generated/buildFlags")

    inputs.property("fcmEnabled", enabled)
    outputs.dir(outputDir)

    doLast {
        val file = outputDir.get().asFile
            .resolve("com/arjun/mooary/core/notifications/BuildFlags.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Generated by generateBuildFlags. Do not edit.
            |package com.arjun.mooary.core.notifications
            |
            |/**
            | * Whether this build carries Cloud Messaging, i.e. was made with
            | * `-Pdiary.firebaseMessaging=true`. Shared code consults it so a default build
            | * never asks the messaging SDK for a token; see `PushRegistrar.android.kt`.
            | */
            |internal const val FCM_ENABLED: Boolean = $enabled
            |
            """.trimMargin()
        )
        logger.lifecycle("Cloud Messaging flag compiled in: $enabled")
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildFlags)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.arjun.mooary.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.arjun.mooary.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Mooary"
            packageVersion = "1.0.0"
            description = "Private diary and mood tracker"
        }
    }
}
