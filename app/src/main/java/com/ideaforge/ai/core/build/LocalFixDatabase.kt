package com.ideaforge.ai.core.build

import android.util.Log

private const val TAG = "LocalFixDB"

data class KnownFix(
    val id: String,
    val patterns: List<Regex>,
    val description: String,
    val fixTemplate: String,
    val targetFiles: List<String> = emptyList(),
    val priority: Int = 0
)

class LocalFixDatabase {

    private val fixes: List<KnownFix> = listOf(
        KnownFix(
            id = "missing_compose_bom",
            patterns = listOf(Regex("is not allowed for the 'compose' feature", RegexOption.IGNORE_CASE),
                Regex("compose.*requires.*version", RegexOption.IGNORE_CASE),
                Regex("compose.*plugin.*version", RegexOption.IGNORE_CASE)),
            description = "Missing or incorrect Compose BOM version in app/build.gradle.kts",
            fixTemplate = """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "%PACKAGE%"
    compileSdk = 35
    defaultConfig {
        applicationId = "%PACKAGE%"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}""",
            targetFiles = listOf("app/build.gradle.kts"),
            priority = 10
        ),
        KnownFix(
            id = "missing_settings_gradle",
            patterns = listOf(Regex("settings.gradle", RegexOption.IGNORE_CASE),
                Regex("pluginManagement", RegexOption.IGNORE_CASE),
                Regex("dependencyResolutionManagement", RegexOption.IGNORE_CASE)),
            description = "Missing or incomplete settings.gradle.kts",
            fixTemplate = """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "%PROJECT_NAME%"
include(":app")""",
            targetFiles = listOf("settings.gradle.kts"),
            priority = 10
        ),
        KnownFix(
            id = "missing_android_manifest",
            patterns = listOf(Regex("AndroidManifest.xml", RegexOption.IGNORE_CASE),
                Regex("manifest.*not found", RegexOption.IGNORE_CASE),
                Regex("missing.*manifest", RegexOption.IGNORE_CASE)),
            description = "Missing AndroidManifest.xml",
            fixTemplate = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity
            android:name=".%PACKAGE_PATH%.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>""",
            targetFiles = listOf("app/src/main/AndroidManifest.xml"),
            priority = 10
        ),
        KnownFix(
            id = "unresolved_reference_import",
            patterns = listOf(Regex("unresolved reference", RegexOption.IGNORE_CASE),
                Regex("cannot be resolved", RegexOption.IGNORE_CASE),
                Regex("unresolved", RegexOption.IGNORE_CASE)),
            description = "Missing import in Kotlin file — add the required import",
            fixTemplate = "import %PACKAGE%.%MISSING_SYMBOL%",
            priority = 5
        ),
        KnownFix(
            id = "type_mismatch",
            patterns = listOf(Regex("type mismatch", RegexOption.IGNORE_CASE),
                Regex("inferred type", RegexOption.IGNORE_CASE),
                Regex("required.*found", RegexOption.IGNORE_CASE)),
            description = "Type mismatch error — likely needs explicit type annotation or cast",
            fixTemplate = "",
            priority = 4
        ),
        KnownFix(
            id = "missing_gradle_properties",
            patterns = listOf(Regex("gradle\\.properties", RegexOption.IGNORE_CASE),
                Regex("android\\.useAndroidX", RegexOption.IGNORE_CASE)),
            description = "Missing gradle.properties",
            fixTemplate = """android.useAndroidX=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx1024m
android.nonTransitiveRClass=true""",
            targetFiles = listOf("gradle.properties"),
            priority = 10
        ),
        KnownFix(
            id = "missing_gradle_wrapper",
            patterns = listOf(Regex("gradlew.*not found", RegexOption.IGNORE_CASE),
                Regex("gradle wrapper", RegexOption.IGNORE_CASE),
                Regex("could not find.*gradle", RegexOption.IGNORE_CASE)),
            description = "Gradle wrapper missing from project — use gradle wrapper --gradle-version 8.9",
            fixTemplate = "",
            priority = 7
        ),
        KnownFix(
            id = "duplicate_class",
            patterns = listOf(Regex("duplicate class", RegexOption.IGNORE_CASE),
                Regex("duplicate.*found", RegexOption.IGNORE_CASE)),
            description = "Duplicate class found in dependencies — check for conflicting libraries",
            fixTemplate = "",
            priority = 3
        ),
        KnownFix(
            id = "api_level_mismatch",
            patterns = listOf(Regex("compileSdk", RegexOption.IGNORE_CASE),
                Regex("minSdk.*greater", RegexOption.IGNORE_CASE),
                Regex("requires.*api level", RegexOption.IGNORE_CASE)),
            description = "SDK/API level mismatch in build.gradle.kts",
            fixTemplate = """android {
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }
}""",
            targetFiles = listOf("app/build.gradle.kts"),
            priority = 8
        ),
        KnownFix(
            id = "aapt2_error",
            patterns = listOf(Regex("aapt2", RegexOption.IGNORE_CASE),
                Regex("resource compilation", RegexOption.IGNORE_CASE),
                Regex("failed.*crunch", RegexOption.IGNORE_CASE)),
            description = "AAPT2 resource compilation error — likely malformed XML in resources",
            fixTemplate = "",
            priority = 5
        ),
        KnownFix(
            id = "kotlin_version_conflict",
            patterns = listOf(Regex("kotlin.*version.*incompatible", RegexOption.IGNORE_CASE),
                Regex("kotlin version.*\\d+\\.\\d+\\.\\d+.*expected.*\\d+\\.\\d+\\.\\d+", RegexOption.IGNORE_CASE)),
            description = "Kotlin version mismatch between plugin and dependencies",
            fixTemplate = "",
            priority = 6
        ),
        KnownFix(
            id = "missing_activity_compose",
            patterns = listOf(Regex("activity-ktx", RegexOption.IGNORE_CASE),
                Regex("activity-compose", RegexOption.IGNORE_CASE),
                Regex("setContent.*unresolved", RegexOption.IGNORE_CASE)),
            description = "Missing activity-compose dependency",
            fixTemplate = "",
            priority = 4
        ),
        KnownFix(
            id = "compose_theme_missing",
            patterns = listOf(Regex("MaterialTheme", RegexOption.IGNORE_CASE),
                Regex("colorScheme", RegexOption.IGNORE_CASE),
                Regex("Compose.*theme.*not found", RegexOption.IGNORE_CASE)),
            description = "Missing Material3 theme — ensure compose-bom is in dependencies",
            fixTemplate = "",
            priority = 5
        ),
        KnownFix(
            id = "gradle_daemon_oom",
            patterns = listOf(Regex("out of memory", RegexOption.IGNORE_CASE),
                Regex("GC overhead", RegexOption.IGNORE_CASE),
                Regex("metaspace", RegexOption.IGNORE_CASE),
                Regex("Java heap space", RegexOption.IGNORE_CASE)),
            description = "Gradle daemon ran out of memory — increase JVM args",
            fixTemplate = "org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m",
            targetFiles = listOf("gradle.properties"),
            priority = 7
        ),
        KnownFix(
            id = "namespace_required",
            patterns = listOf(Regex("namespace.*must", RegexOption.IGNORE_CASE),
                Regex("namespace.*not set", RegexOption.IGNORE_CASE),
                Regex("Android namespace", RegexOption.IGNORE_CASE)),
            description = "Missing namespace in app/build.gradle.kts",
            fixTemplate = "",
            priority = 9
        )
    )

    fun findFixes(errorLogs: String, currentFiles: Map<String, String>): List<KnownFix> {
        val matched = fixes.filter { fix ->
            fix.patterns.any { pattern ->
                pattern.containsMatchIn(errorLogs)
            }
        }.sortedByDescending { it.priority }

        if (matched.isNotEmpty()) {
            Log.d(TAG, "Found ${matched.size} local fix(es) for error logs")
            matched.forEach { Log.d(TAG, "  Fix: ${it.id} — ${it.description} (priority ${it.priority})") }
        }
        return matched
    }

    fun applyFix(fix: KnownFix, currentFiles: MutableMap<String, String>, projectName: String, packageName: String): Map<String, String> {
        val applied = mutableMapOf<String, String>()
        if (fix.fixTemplate.isBlank()) return applied

        val pkgPath = packageName.replace(".", "/")
        val content = fix.fixTemplate
            .replace("%PACKAGE%", packageName)
            .replace("%PACKAGE_PATH%", pkgPath)
            .replace("%PROJECT_NAME%", projectName)

        if (fix.targetFiles.isEmpty()) return applied

        for (targetFile in fix.targetFiles) {
            val existing = currentFiles[targetFile]
            if (existing == null || existing.isBlank() || fix.priority >= 8) {
                currentFiles[targetFile] = content
                applied[targetFile] = content
                Log.d(TAG, "Applied local fix '${fix.id}' to $targetFile")
            }
        }
        return applied
    }
}
