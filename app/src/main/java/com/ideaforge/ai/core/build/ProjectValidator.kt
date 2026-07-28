package com.ideaforge.ai.core.build

import java.io.File

object ProjectValidator {

    data class ValidationResult(val valid: Boolean, val errors: List<String>, val warnings: List<String>)

    fun validate(projectDir: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val dir = File(projectDir)

        if (!dir.exists() || !dir.isDirectory) {
            return ValidationResult(false, listOf("Project directory does not exist"), emptyList())
        }

        val requiredRootFiles = listOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties")
        for (file in requiredRootFiles) {
            if (!File(dir, file).exists()) errors.add("Missing root file: $file")
        }

        if (!File(dir, "app/build.gradle.kts").exists() && !File(dir, "app/build.gradle").exists()) {
            errors.add("Missing app/build.gradle.kts")
        }

        if (!File(dir, "app/src/main/AndroidManifest.xml").exists()) {
            errors.add("Missing AndroidManifest.xml")
        }

        val javaDir = File(dir, "app/src/main/java")
        if (javaDir.exists()) {
            val ktFiles = javaDir.walkTopDown().filter { it.extension == "kt" }.toList()
            if (ktFiles.isEmpty()) {
                errors.add("No Kotlin source files found")
            }
            ktFiles.forEach { file ->
                val content = file.readText()
                if (content.length < 20) warnings.add("Suspiciously short file: ${file.relativeTo(dir)}")
                if (!content.contains("package ")) warnings.add("Missing package declaration: ${file.relativeTo(dir)}")
            }
        } else {
            errors.add("No source directory found")
        }

        val manifest = File(dir, "app/src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            val content = manifest.readText()
            if (!content.contains("<manifest")) errors.add("Invalid AndroidManifest.xml")
        }

        val resDir = File(dir, "app/src/main/res")
        if (resDir.exists()) {
            if (!File(resDir, "values/strings.xml").exists()) warnings.add("Missing strings.xml")
        }

        val buildGradle = File(dir, "app/build.gradle.kts")
        if (buildGradle.exists()) {
            val content = buildGradle.readText()
            if (!content.contains("compileSdk")) warnings.add("Missing compileSdk in build.gradle.kts")
            if (!content.contains("minSdk")) warnings.add("Missing minSdk in build.gradle.kts")
        }

        return ValidationResult(errors.isEmpty(), errors, warnings)
    }

    fun getRequiredFiles(packageName: String): List<String> {
        val pkgPath = packageName.replace(".", "/")
        return listOf(
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "app/build.gradle.kts",
            "app/proguard-rules.pro",
            "app/src/main/AndroidManifest.xml",
            "app/src/main/java/$pkgPath/MainActivity.kt",
            "app/src/main/res/values/strings.xml"
        )
    }
}
