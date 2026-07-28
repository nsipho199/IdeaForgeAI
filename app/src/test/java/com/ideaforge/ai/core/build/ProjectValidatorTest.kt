package com.ideaforge.ai.core.build

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File

    @Before
    fun setup() {
        projectDir = tempFolder.newFolder("testproject")
    }

    @Test
    fun `valid project passes validation`() {
        createValidProjectStructure()
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertTrue("Expected valid project, errors: ${result.errors}", result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `missing root build gradle fails`() {
        createValidProjectStructure()
        File(projectDir, "build.gradle.kts").delete()
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("build.gradle.kts") })
    }

    @Test
    fun `missing settings gradle fails`() {
        createValidProjectStructure()
        File(projectDir, "settings.gradle.kts").delete()
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("settings.gradle.kts") })
    }

    @Test
    fun `missing gradle properties fails`() {
        createValidProjectStructure()
        File(projectDir, "gradle.properties").delete()
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("gradle.properties") })
    }

    @Test
    fun `missing app build gradle fails`() {
        createValidProjectStructure()
        File(projectDir, "app/build.gradle.kts").delete()
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("app/build.gradle.kts") })
    }

    @Test
    fun `missing AndroidManifest fails`() {
        createValidProjectStructure()
        File(projectDir, "app/src/main/AndroidManifest.xml").delete()
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("AndroidManifest") })
    }

    @Test
    fun `missing kotlin source files fails`() {
        createValidProjectStructure()
        File(projectDir, "app/src/main/java").deleteRecursively()
        File(projectDir, "app/src/main/java").mkdirs()
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("No Kotlin source files") })
    }

    @Test
    fun `missing source directory fails`() {
        createValidProjectStructure()
        File(projectDir, "app/src/main/java").deleteRecursively()
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("No source directory") })
    }

    @Test
    fun `non-existent directory fails`() {
        val result = ProjectValidator.validate("/nonexistent/path")
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("does not exist") })
    }

    @Test
    fun `short kotlin file produces warning`() {
        createValidProjectStructure()
        val ktFile = File(projectDir, "app/src/main/java/com/example/MainActivity.kt")
        ktFile.writeText("package com.example\nfun main() {}")
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertTrue(result.warnings.any { it.contains("Suspiciously short") })
    }

    @Test
    fun `missing package declaration produces warning`() {
        createValidProjectStructure()
        val ktFile = File(projectDir, "app/src/main/java/com/example/MainActivity.kt")
        ktFile.writeText("fun main() {\nprintln(\"hello\")\n// pad to exceed 20 chars\n123456789012345678901234567890\n}")
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertTrue(result.warnings.any { it.contains("Missing package declaration") })
    }

    @Test
    fun `invalid manifest produces error`() {
        createValidProjectStructure()
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        manifest.writeText("<html><body>Not a manifest</body></html>")
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertTrue(result.errors.any { it.contains("Invalid AndroidManifest") })
    }

    @Test
    fun `missing strings xml produces warning`() {
        createValidProjectStructure()
        File(projectDir, "app/src/main/res/values/strings.xml").delete()
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertTrue(result.warnings.any { it.contains("strings.xml") })
    }

    @Test
    fun `missing compileSdk in build gradle produces warning`() {
        createValidProjectStructure()
        val buildGradle = File(projectDir, "app/build.gradle.kts")
        buildGradle.writeText("plugins {\n    id(\"com.android.application\")\n}\nandroid {\n    minSdk = 26\n}")
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertTrue(result.warnings.any { it.contains("compileSdk") })
    }

    @Test
    fun `missing minSdk in build gradle produces warning`() {
        createValidProjectStructure()
        val buildGradle = File(projectDir, "app/build.gradle.kts")
        buildGradle.writeText("plugins {\n    id(\"com.android.application\")\n}\nandroid {\n    compileSdk = 35\n}")
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertTrue(result.warnings.any { it.contains("minSdk") })
    }

    @Test
    fun `getRequiredFiles returns correct paths`() {
        val files = ProjectValidator.getRequiredFiles("com.example.app")
        assertTrue(files.contains("build.gradle.kts"))
        assertTrue(files.contains("settings.gradle.kts"))
        assertTrue(files.contains("gradle.properties"))
        assertTrue(files.contains("app/build.gradle.kts"))
        assertTrue(files.contains("app/proguard-rules.pro"))
        assertTrue(files.contains("app/src/main/AndroidManifest.xml"))
        assertTrue(files.contains("app/src/main/java/com/example/app/MainActivity.kt"))
        assertTrue(files.contains("app/src/main/res/values/strings.xml"))
    }

    @Test
    fun `multiple errors all reported`() {
        // Missing everything
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertTrue(result.errors.size >= 4)
    }

    @Test
    fun `manifest with manifest tag passes`() {
        createValidProjectStructure()
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        manifest.writeText("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n<application />\n</manifest>")
        val result = ProjectValidator.validate(projectDir.absolutePath)
        assertFalse(result.errors.any { it.contains("Invalid AndroidManifest") })
    }

    private fun createValidProjectStructure() {
        // Root files
        File(projectDir, "build.gradle.kts").writeText("plugins {\n    id(\"com.android.application\") version \"8.7.3\" apply false\n}")
        File(projectDir, "settings.gradle.kts").writeText("pluginManagement { repositories { google(); mavenCentral() } }\nrootProject.name = \"test\"")
        File(projectDir, "gradle.properties").writeText("org.gradle.jvmargs=-Xmx1024m")

        // App build.gradle.kts
        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText("plugins {\n    id(\"com.android.application\")\n}\nandroid {\n    compileSdk = 35\n    minSdk = 26\n    defaultConfig {\n        minSdk = 26\n        targetSdk = 35\n    }\n}")

        // Proguard
        File(appDir, "proguard-rules.pro").writeText("-keep class ** { *; }")

        // Manifest
        val mainDir = File(appDir, "src/main")
        mainDir.mkdirs()
        File(mainDir, "AndroidManifest.xml").writeText("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n<application android:label=\"Test\">\n<activity android:name=\".MainActivity\" />\n</application>\n</manifest>")

        // Kotlin source
        val ktDir = File(mainDir, "java/com/example")
        ktDir.mkdirs()
        File(ktDir, "MainActivity.kt").writeText("package com.example\n\nimport android.os.Bundle\nimport androidx.activity.ComponentActivity\n\nclass MainActivity : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n    }\n}")

        // Resources
        val resDir = File(mainDir, "res/values")
        resDir.mkdirs()
        File(resDir, "strings.xml").writeText("<resources>\n    <string name=\"app_name\">Test</string>\n</resources>")
    }
}
