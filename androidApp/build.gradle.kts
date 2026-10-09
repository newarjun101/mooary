plugins {
    alias(libs.plugins.androidApplication)
    // MainActivity hosts the shared Compose UI, so this module needs the Compose compiler
    // too. Without it the composable lambda passed to setContent is compiled as a plain
    // Function0 while the signature activity-compose exposes at runtime is a Composer-based
    // Function2 — a NoSuchMethodError on the first frame rather than a compile error. Pinned
    // to the same Kotlin version as :composeApp so the two agree.
    alias(libs.plugins.composeCompiler)
}

/**
 * Copies `:composeApp`'s prepared Compose resources into an Android assets layout.
 *
 * Compose Multiplatform keeps its strings in `.cvr` files, and on Android
 * `DefaultAndroidResourceReader` reads them from the APK's assets. The Compose Gradle plugin
 * normally copies them there, but only wires that up for the classic
 * `com.android.library` plugin. This project consumes `:composeApp` through AGP 9's
 * `com.android.kotlin.multiplatform.library`, where the wiring never registers: the plugin
 * creates no `assembleAndroidMainResources` task and the assets stay empty.
 *
 * Nothing reports this. The APK builds, installs and launches, then dies on the first
 * `stringResource` call with `MissingResourceException`.
 *
 * Declared as a task with a real output directory rather than a `Sync`, because AGP derives
 * both the source path and the task dependency from the output property it is handed. Pointing
 * it at a directory owned by the build script instead silently produced an APK with no
 * resources in it.
 */
/** Task-name prefixes of :composeApp's Compose resource preparation chain. */
val composeResourceTaskPrefixes = listOf(
    "prepareComposeResourcesTaskFor",
    "convertXmlValueResourcesFor",
    "copyNonXmlValueResourcesFor",
)

abstract class StageComposeResources : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val preparedResources: DirectoryProperty

    @get:Input
    abstract val resourceSourceSet: Property<String>

    @get:Input
    abstract val resourcePackage: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun stage() {
        val source = preparedResources.get()
            .dir(resourceSourceSet.get())
            .dir("composeResources")
            .asFile
        check(source.isDirectory) {
            "No prepared Compose resources for source set '${resourceSourceSet.get()}' at $source. " +
                ":composeApp may have stopped generating them for the Android target."
        }

        val target = outputDir.get()
            .dir("composeResources")
            .dir(resourcePackage.get())
            .asFile
        target.deleteRecursively()
        source.copyRecursively(target, overwrite = true)

        // The resource package is baked into the reader's lookup path, so a rename that is
        // not mirrored here still builds and still crashes. Catch it at build time instead.
        val staged = target.walkTopDown().count { it.extension == "cvr" }
        check(staged > 0) {
            "Staged no .cvr resources into $target. If the resource package was renamed, update " +
                "composeResourcePackage to '${resourcePackage.get()}'."
        }
    }
}

/** The Kotlin source set whose Compose resources are packaged into the APK. */
val composeResourceSourceSet = "commonMain"

/** Must match the package the Compose resource accessors are generated into. */
val composeResourcePackage = "com.arjun.soul_note.resources"

val stageComposeResourcesAssets = tasks.register<StageComposeResources>("stageComposeResourcesAssets") {
    group = "compose resources"
    description = "Stages :composeApp Compose resources as Android assets."

    preparedResources.set(
        project(":composeApp").layout.buildDirectory
            .dir("generated/compose/resourceGenerator/preparedResources")
    )
    resourceSourceSet.set(composeResourceSourceSet)
    resourcePackage.set(composeResourcePackage)
    outputDir.set(layout.buildDirectory.dir("generated/composeAssets"))

    // The Compose plugin does prepare the Android resources, it just never publishes them
    // anywhere. Preparation is a per-source-set chain — prepare, then convert the XML values
    // or copy the rest — with no aggregate task, so the families are matched by name: that
    // stays correct when a source set is added, where listing today's tasks would not.
    dependsOn(
        project(":composeApp").tasks.matching { task ->
            composeResourceTaskPrefixes.any { prefix -> task.name.startsWith(prefix) }
        }
    )
}

/**
 * Publishes the staged resources into every variant's assets.
 *
 * Through the Variant API rather than `sourceSets.main.assets.srcDir(...)`, because AGP 9
 * rejects Providers there, and the older form would not have carried the task dependency
 * either — leaving a clean build free to race the copy above.
 */
/**
 * Turns the developer's `google-services.json` into the flat resource the app actually reads.
 *
 * The app deliberately does not use the `com.google.gms.google-services` plugin: it reaches
 * Firestore and Identity Toolkit over REST from shared code, so it needs three values —
 * api key, project id, app id — and nothing else that plugin provides. A whole plugin plus its
 * version alignment to get three strings is not a trade worth making for a project that also
 * builds on iOS and desktop, where the plugin does not apply at all.
 *
 * The transformation is not a copy. `google-services.json` nests its app under `client` as a
 * *JSON array* of per-platform entries, while `Platform.android.kt` reads a `client` *object*
 * with `current_key` / `mobilesdk_app_id`. Dropping the console's file into `res/raw`
 * unchanged therefore loads nothing at all, and it fails silently — the app just starts in
 * offline mode — which is the worst possible failure mode for a setup step. Generating the
 * expected shape removes the mismatch.
 *
 * Output goes to a build directory rather than `src/main/res/raw` so the generated file can
 * never be committed by accident and never shows up in an IDE's resource view as if it were
 * hand-written. It is registered as a generated res directory instead, which keeps it out of
 * the source tree and makes the dependency explicit.
 *
 * Missing or unparseable input is not an error: the app is fully functional offline, so a
 * build on a machine with no Firebase project must still succeed.
 */
abstract class GenerateFirebaseConfig : DefaultTask() {

    // A file collection rather than an `InputFile` property: Gradle validates that an
    // `InputFile` exists before the action ever runs, and `@Optional` does not exempt it, so
    // a checkout with no `google-services.json` — a perfectly valid offline build — would fail
    // configuration. A collection accepts the empty case and the action decides what it means.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val googleServices: ConfigurableFileCollection

    @get:Input
    abstract val emulator: Property<Boolean>

    /**
     * Whether Cloud Messaging is wired into the manifest.
     *
     * Off by default because the messaging SDK needs the Cloud Messaging API enabled on the
     * project, and a build must not depend on a console setting nobody can verify from here.
     * With it off the service is not declared, no token is ever requested, and the rest of the
     * app is unaffected.
     */
    @get:Input
    abstract val pushEnabled: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val target = outputDir.get().dir("raw").file("firebase_config.json").asFile
        val source = googleServices.files.firstOrNull()

        if (source == null || !source.isFile) {
            logger.lifecycle(
                "No google-services.json found; building without Firebase. " +
                    "See docs/firebase-setup.md to enable sync."
            )
            target.delete()
            return
        }

        val root = runCatching { groovy.json.JsonSlurper().parse(source) as Map<*, *> }.getOrNull()
        if (root == null) {
            logger.warn("google-services.json at $source is not valid JSON; building without Firebase.")
            target.delete()
            return
        }

        val projectInfo = root["project_info"] as? Map<*, *> ?: emptyMap<Any, Any>()
        // The console emits one `client` entry per platform; the Android entry is the only
        // one this app can use, and picking by `client_info.android_client_info` is what keeps
        // the task correct for a project that also has an iOS app registered.
        val androidClient = (root["client"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.firstOrNull { (it["client_info"] as? Map<*, *>)?.get("android_client_info") != null }
            ?: (root["client"] as? Map<*, *>)
            ?: emptyMap<Any, Any>()

        val clientInfo = androidClient["client_info"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val apiKeyEntries = androidClient["api_key"] as? List<*>
        val apiKey = apiKeyEntries?.firstOrNull()
            ?.let { (it as? Map<*, *>)?.get("current_key") as? String }
        val appId = clientInfo["mobilesdk_app_id"] as? String
        val projectId = projectInfo["project_id"] as? String

        if (apiKey.isNullOrBlank() || appId.isNullOrBlank() || projectId.isNullOrBlank()) {
            logger.warn(
                "google-services.json at $source has no Android client with api_key and " +
                    "mobilesdk_app_id; building without Firebase. " +
                    "Check the project has a registered Android app."
            )
            target.delete()
            // The generated strings have to go too. Left in place they satisfy the manifest's
            // four meta-data references with a previous project's values, so the SDK would
            // initialise against a project this build is not configured for -- and a push
            // registration would then be written to somebody else's account. Deleting only the
            // raw JSON makes the next build fail loudly on an unresolved @string instead.
            outputDir.get().dir("values").file("firebase_push_strings.xml").asFile.delete()
            return
        }

        val json = buildString {
            appendLine("{")
            appendLine("""  "current_key": "${jsonEscape(apiKey)}",""")
            appendLine("""  "mobilesdk_app_id": "${jsonEscape(appId)}",""")
            appendLine("""  "project_id": "${jsonEscape(projectId)}",""")
            appendLine("""  "project_number": "${jsonEscape(projectInfo["project_number"] as? String ?: "")}",""")
            appendLine("""  "storage_bucket": "${jsonEscape(projectInfo["storage_bucket"] as? String ?: "")}",""")
            // The emulator flag is the one value the console cannot supply, because it is a
            // property of where this build is pointing rather than of the project.
            appendLine("""  "emulator": ${emulator.get()}""")
            append("}")
        }

        target.parentFile.mkdirs()
        target.writeText(json)

        // FirebaseApp's startup provider reads these *resource names* directly. The manifest
        // metadata configures Messaging defaults, but does not configure FirebaseApp itself.
        // The google-services plugin normally writes this file; without it Firebase logs
        // "no default options" at every cold start and Cloud Messaging never connects.
        // `gcm_defaultSenderId` is the project number, not the app id.
        val senderId = projectInfo["project_number"] as? String ?: ""
        val strings = outputDir.get().dir("values").file("firebase_push_strings.xml").asFile
        strings.parentFile.mkdirs()
        strings.writeText(
            buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("""<!-- Generated from google-services.json. Do not edit; see GenerateFirebaseConfig. -->""")
                appendLine("<resources>")
                appendLine("""    <string name="google_app_id" translatable="false">${xmlEscape(appId)}</string>""")
                appendLine("""    <string name="gcm_defaultSenderId" translatable="false">${xmlEscape(senderId)}</string>""")
                appendLine("""    <string name="google_api_key" translatable="false">${xmlEscape(apiKey)}</string>""")
                appendLine("""    <string name="project_id" translatable="false">${xmlEscape(projectId)}</string>""")
                append("</resources>")
            }
        )

        logger.lifecycle("Generated Firebase config for project '$projectId' (emulator=${emulator.get()}).")
        if (pushEnabled.get()) {
            logger.lifecycle("Cloud Messaging wired (diary.firebaseMessaging=true).")
        }
    }

    /** Values come from the console, but never let one break the file it is written into. */
    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

val generateFirebaseConfig = tasks.register<GenerateFirebaseConfig>("generateFirebaseConfig") {
    group = "firebase"
    description = "Generates res/raw/firebase_config.json from google-services.json."
    googleServices.from(rootProject.layout.projectDirectory.file("google-services.json"))
    emulator.set(providers.gradleProperty("diary.firebaseEmulator").orNull?.toBoolean() ?: false)
    pushEnabled.set(providers.gradleProperty("diary.firebaseMessaging").orNull?.toBoolean() ?: false)
    outputDir.set(layout.buildDirectory.dir("generated/firebaseConfig"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(stageComposeResourcesAssets) {
            it.outputDir
        }

        // Registered as a generated resource directory so AGP wires the dependency itself.
        // Doing it by hand through `sourceSets` would leave a clean build free to run the
        // resource merge before this task, producing an APK with no `firebase_config` in it
        // and no error to explain why.
        variant.sources.res?.addGeneratedSourceDirectory(
            generateFirebaseConfig,
            GenerateFirebaseConfig::outputDir,
        )
    }
}

android {
    namespace = "com.arjun.soul_note.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.arjun.soul_note"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The messaging service is declared but disabled unless the flag is on. Keeping the
        // element present either way means the manifest does not change shape between the two
        // builds, and `android:enabled` is the only thing the system consults.
        manifestPlaceholders["fcmEnabled"] =
            providers.gradleProperty("diary.firebaseMessaging").orNull?.toBoolean() ?: false
    }

    androidResources {
        localeFilters += setOf("en", "my")
    }

    signingConfigs {
        // Debug signing only. Release keystores are supplied by CI via
        // -Pdiary.keystorePath / -Pdiary.keystorePassword / -Pdiary.keyAlias /
        // -Pdiary.keyPassword and never committed to the repository.
        create("releaseFromEnv") {
            val storePath = providers.gradleProperty("diary.keystorePath").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("diary.keystorePassword").orNull
                keyAlias = providers.gradleProperty("diary.keyAlias").orNull
                keyPassword = providers.gradleProperty("diary.keyPassword").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (providers.gradleProperty("diary.keystorePath").isPresent) {
                signingConfig = signingConfigs.getByName("releaseFromEnv")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/versions/9/previous-compilation-data.bin",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "ObsoleteLintCustomCheck")
    }
}

dependencies {
    implementation(project(":composeApp"))

    // Declared in :composeApp as `implementation`, so they are not on this module's compile
    // classpath even though the shared code needs them at runtime.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
    // Also declared in :composeApp, which uses it to ask for a registration token. Not shared
    // transitively because that declaration is `implementation`, so it is repeated rather than
    // promoted to `api` for the sake of one class.
    implementation(libs.firebase.messaging)
    // Crashlytics is deliberately not a dependency here, and cannot be added as-is.
    //
    // The Gradle plugin it needs fetches its app id out of the google-services plugin's
    // `process<Variant>GoogleServices` task and throws "Google-Services plugin not found" while
    // creating `uploadCrashlyticsMappingFileRelease`, which breaks `:androidApp:assembleRelease`
    // at task-graph time. This app replaces google-services with `generateFirebaseConfig` (see
    // above), so applying that plugin is not an option either: it would look for
    // `google-services.json` inside `androidApp/` and regenerate resources this build already
    // generates. And the SDK without the plugin is worse than missing — it throws
    // "The Crashlytics build ID is missing" from `FirebaseInitProvider`, so the app dies before
    // its first frame, which no build failure reports. All three configurations were tried.
    // Analytics has no such coupling: it reads `google_app_id` from the resource
    // generateFirebaseConfig writes.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
