package com.ideaforge.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

public class IdeaForgeServer {

    private static final int PORT = 3000;
    private static final String OPENCODE_API_URL = "https://opencode.ai/zen/v1/chat/completions";
    private static final String MODEL_ID = "big-pickle";
    private static final String PROJECTS_DIR = System.getProperty("user.home") + "/ideaforge_projects";
    private static final String APK_DIR = System.getProperty("user.home") + "/ideaforge_apks";
    private static final String ANDROID_SDK = "/opt/android_sdk";
    private static final String BUILD_TOOLS = ANDROID_SDK + "/build-tools/34.0.0";
    private static final String PLATFORMS = ANDROID_SDK + "/platforms/android-35";
    private static final String JAVA_HOME = "/opt/java/jdk-17.0.19+10";

    private static String apiKey = System.getenv("OPENCODE_API_KEY");
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final ConcurrentHashMap<String, BuildJob> builds = new ConcurrentHashMap<>();
    private static final AtomicInteger jobCounter = new AtomicInteger(0);
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    static class BuildJob {
        String id;
        String idea;
        String projectName;
        String packageName;
        String status = "building";
        String stage = "CONNECTING";
        float progress = 0f;
        String message = "Initializing...";
        String error;
        String downloadUrl;
        String apkPath;
        List<String> logs = new ArrayList<>();
        long createdAt = System.currentTimeMillis();

        synchronized void update(String stage, float progress, String message) {
            this.stage = stage;
            this.progress = progress;
            this.message = message;
            this.logs.add("[" + stage + "] " + message);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) apiKey = args[0];
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Usage: java IdeaForgeServer <OPENCODE_API_KEY>");
            System.out.println("Or set OPENCODE_API_KEY environment variable");
            System.exit(1);
        }

        new File(PROJECTS_DIR).mkdirs();
        new File(APK_DIR).mkdirs();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/v1/health", IdeaForgeServer::handleHealth);
        server.createContext("/api/v1/build", IdeaForgeServer::handleBuild);
        server.createContext("/api/v1/assistant/chat", IdeaForgeServer::handleChat);
        server.setExecutor(executor);
        server.start();

        System.out.println("IdeaForge Backend running on port " + PORT);
        System.out.println("OpenCode Zen API: configured");
        System.out.println("Android SDK: " + ANDROID_SDK);
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String json = "{\"status\":\"ok\",\"version\":\"1.0.0\",\"model\":\"" + MODEL_ID + "\",\"uptime\":\"" +
            Duration.ofMillis(System.currentTimeMillis()) + "\"}";
        sendResponse(exchange, 200, json);
    }

    private static void handleBuild(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("POST".equals(method) && "/api/v1/build".equals(path)) {
            handleBuildSubmit(exchange);
        } else if ("GET".equals(method) && path.matches("/api/v1/build/[^/]+/status")) {
            String id = path.split("/")[4];
            handleBuildStatus(exchange, id);
        } else if ("DELETE".equals(method) && path.matches("/api/v1/build/[^/]+")) {
            String id = path.split("/")[3];
            handleBuildCancel(exchange, id);
        } else {
            sendResponse(exchange, 404, "{\"error\":\"Not found\"}");
        }
    }

    private static void handleBuildSubmit(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes());
        String idea = extractJsonString(body, "idea");
        String projectName = extractJsonString(body, "projectName");
        String packageName = extractJsonString(body, "packageName");

        if (idea == null || idea.isBlank()) {
            sendResponse(exchange, 400, "{\"error\":\"idea is required\"}");
            return;
        }
        if (projectName == null || projectName.isBlank()) projectName = "MyApp";
        if (packageName == null || packageName.isBlank()) packageName = "com.example.myapp";

        String id = "build-" + jobCounter.incrementAndGet();
        BuildJob job = new BuildJob();
        job.id = id;
        job.idea = idea;
        job.projectName = projectName;
        job.packageName = packageName;
        builds.put(id, job);

        executor.submit(() -> runBuildPipeline(job));

        String json = "{\"id\":\"" + id + "\",\"status\":\"building\",\"message\":\"Build submitted\"}";
        sendResponse(exchange, 201, json);
    }

    private static void handleBuildStatus(HttpExchange exchange, String id) throws IOException {
        BuildJob job = builds.get(id);
        if (job == null) {
            sendResponse(exchange, 404, "{\"error\":\"Build not found\"}");
            return;
        }
        synchronized (job) {
            String json = "{\"id\":\"" + job.id + "\",\"status\":\"" + job.status +
                "\",\"stage\":\"" + job.stage + "\",\"progress\":" + job.progress +
                ",\"message\":\"" + escapeJson(job.message) + "\"" +
                (job.error != null ? ",\"error\":\"" + escapeJson(job.error) + "\"" : "") +
                (job.downloadUrl != null ? ",\"downloadUrl\":\"" + job.downloadUrl + "\"" : "") +
                "}";
            sendResponse(exchange, 200, json);
        }
    }

    private static void handleBuildCancel(HttpExchange exchange, String id) throws IOException {
        BuildJob job = builds.get(id);
        if (job == null) {
            sendResponse(exchange, 404, "{\"error\":\"Build not found\"}");
            return;
        }
        synchronized (job) {
            job.status = "cancelled";
            job.message = "Build cancelled by user";
        }
        sendResponse(exchange, 200, "{\"message\":\"Build cancelled\"}");
    }

    private static void handleChat(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes());
        String message = extractJsonString(body, "message");
        if (message == null || message.isBlank()) {
            sendResponse(exchange, 400, "{\"error\":\"message is required\"}");
            return;
        }

        try {
            String aiResponse = callOpenCodeZen("You are IdeaForge AI assistant. Help users with their app ideas. Be concise and helpful. " +
                "User message: " + message);
            String escaped = escapeJson(aiResponse);
            sendResponse(exchange, 200, "{\"response\":\"" + escaped + "\"}");
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"AI service unavailable: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static void runBuildPipeline(BuildJob job) {
        try {
            job.update("CONNECTING", 5f, "Calling Big Pickle AI...");
            Thread.sleep(500);

            if ("cancelled".equals(job.status)) return;

            String aiPrompt = buildCodeGenerationPrompt(job.idea, job.projectName, job.packageName);
            String aiResponse = callOpenCodeZen(aiPrompt);

            job.update("GENERATING_CODE", 25f, "AI generated code successfully");
            Thread.sleep(300);

            if ("cancelled".equals(job.status)) return;

            String projectDir = PROJECTS_DIR + "/" + job.id;
            new File(projectDir).mkdirs();

            Map<String, String> files = parseGeneratedFiles(aiResponse, job.packageName, job.projectName);
            writeProjectFiles(projectDir, files, job.packageName);

            job.update("BUILDING_PROJECT", 40f, "Project files created, building APK...");
            Thread.sleep(300);

            if ("cancelled".equals(job.status)) return;

            boolean buildSuccess = buildApk(projectDir, job);

            if ("cancelled".equals(job.status)) return;

            if (buildSuccess) {
                String apkPath = findApk(projectDir);
                if (apkPath != null) {
                    String destApk = APK_DIR + "/" + job.id + ".apk";
                    Files.copy(Path.of(apkPath), Path.of(destApk), StandardCopyOption.REPLACE_EXISTING);
                    job.apkPath = destApk;
                    job.downloadUrl = "http://localhost:" + PORT + "/download/" + job.id + ".apk";

                    synchronized (job) {
                        job.status = "completed";
                        job.stage = "COMPLETED";
                        job.progress = 100f;
                        job.message = "Build complete!";
                    }
                    job.update("COMPLETED", 100f, "APK ready for download");
                } else {
                    failBuild(job, "Build succeeded but APK not found");
                }
            } else {
                failBuild(job, "Build failed. Check logs for details.");
            }

        } catch (Exception e) {
            failBuild(job, "Build error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    private static void failBuild(BuildJob job, String error) {
        synchronized (job) {
            job.status = "failed";
            job.stage = "FAILED";
            job.progress = 0f;
            job.message = error;
            job.error = error;
        }
    }

    private static String callOpenCodeZen(String prompt) throws Exception {
        String messagesJson = "{\"model\":\"" + MODEL_ID + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" +
            escapeJson(prompt) + "\"}],\"max_tokens\":16000}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OPENCODE_API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(messagesJson))
            .timeout(Duration.ofSeconds(180))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenCode API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return extractContent(response.body());
    }

    private static String buildCodeGenerationPrompt(String idea, String projectName, String packageName) {
        return "Generate a complete, working Android app project in Kotlin with Jetpack Compose for this idea:\n\n" +
            idea + "\n\n" +
            "Project name: " + projectName + "\n" +
            "Package name: " + packageName + "\n\n" +
            "Output EACH file in this exact format, one after another:\n\n" +
            "===FILE:path/to/file===\n" +
            "file contents here\n" +
            "===END FILE===\n\n" +
            "Generate these files:\n" +
            "1. app/src/main/java/" + packageName.replace(".", "/") + "/MainActivity.kt\n" +
            "2. app/src/main/java/" + packageName.replace(".", "/") + "/ui/theme/Theme.kt\n" +
            "3. app/src/main/java/" + packageName.replace(".", "/") + "/ui/theme/Color.kt\n" +
            "4. app/src/main/java/" + packageName.replace(".", "/") + "/ui/theme/Type.kt\n" +
            "5. app/src/main/java/" + packageName.replace(".", "/") + "/MainApp.kt (main UI screens)\n" +
            "6. app/src/main/res/values/strings.xml\n" +
            "7. app/src/main/res/values/colors.xml\n" +
            "8. app/src/main/AndroidManifest.xml\n\n" +
            "Requirements:\n" +
            "- Use Material3 and Jetpack Compose\n" +
            "- All code must compile and work\n" +
            "- Make the app functional with real logic\n" +
            "- Use only standard Android libraries\n" +
            "- minSdk=26, targetSdk=35, compileSdk=35\n" +
            "- Use KSP for annotation processing\n" +
            "- Output COMPLETE file contents, no placeholders or truncation";
    }

    private static Map<String, String> parseGeneratedFiles(String aiResponse, String packageName, String projectName) {
        Map<String, String> files = new LinkedHashMap<>();
        String[] parts = aiResponse.split("===FILE:");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int pathEnd = part.indexOf("===");
            if (pathEnd < 0) continue;
            String path = part.substring(0, pathEnd).trim();
            String content = part.substring(pathEnd + 3);
            if (content.startsWith("\n")) content = content.substring(1);
            int endIdx = content.indexOf("===END FILE===");
            if (endIdx >= 0) content = content.substring(0, endIdx);
            files.put(path, content.trim());
        }

        if (!files.containsKey("build.gradle.kts") && !files.containsKey("build.gradle")) {
            files.put("build.gradle.kts", generateBuildGradle(packageName));
        }
        if (!files.containsKey("settings.gradle.kts") && !files.containsKey("settings.gradle")) {
            files.put("settings.gradle.kts", "pluginManagement {\n    google()\n    mavenCentral()\n    gradlePluginPortal()\n}\n" +
                "dependencyResolutionManagement {\n    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n    google()\n    mavenCentral()\n}\n" +
                "rootProject.name = \"" + projectName + "\"\ninclude(\":app\")\n");
        }
        if (!files.containsKey("gradle.properties")) {
            files.put("gradle.properties", "org.gradle.jvmargs=-Xmx1024m\nandroid.useAndroidX=true\nkotlin.code.style=official\nandroid.nonTransitiveRClass=true\n");
        }
        if (!files.containsKey("app/build.gradle.kts") && !files.containsKey("app/build.gradle")) {
            files.put("app/build.gradle.kts", generateAppBuildGradle(packageName));
        }
        if (!files.containsKey("app/proguard-rules.pro")) {
            files.put("app/proguard-rules.pro", "# Add project specific ProGuard rules here.\n");
        }

        return files;
    }

    private static String generateBuildGradle(String packageName) {
        return "plugins {\n    id(\"com.android.application\") version \"8.7.3\" apply false\n" +
            "    id(\"org.jetbrains.kotlin.android\") version \"2.0.21\" apply false\n" +
            "    id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.0.21\" apply false\n" +
            "    id(\"com.google.devtools.ksp\") version \"2.0.21-1.0.28\" apply false\n}\n";
    }

    private static String generateAppBuildGradle(String packageName) {
        return "plugins {\n    id(\"com.android.application\")\n    id(\"org.jetbrains.kotlin.android\")\n" +
            "    id(\"org.jetbrains.kotlin.plugin.compose\")\n    id(\"com.google.devtools.ksp\")\n}\n\n" +
            "android {\n    namespace = \"" + packageName + "\"\n    compileSdk = 35\n\n" +
            "    defaultConfig {\n        applicationId = \"" + packageName + "\"\n" +
            "        minSdk = 26\n        targetSdk = 35\n        versionCode = 1\n        versionName = \"1.0\"\n    }\n\n" +
            "    buildTypes {\n        release {\n            isMinifyEnabled = false\n            proguardFiles(getDefaultProguardFile(\"proguard-android-optimize.txt\"), \"proguard-rules.pro\")\n        }\n    }\n\n" +
            "    compileOptions {\n        sourceCompatibility = JavaVersion.VERSION_17\n        targetCompatibility = JavaVersion.VERSION_17\n    }\n\n" +
            "    kotlinOptions {\n        jvmTarget = \"17\"\n    }\n\n" +
            "    buildFeatures {\n        compose = true\n    }\n}\n\n" +
            "dependencies {\n    implementation(platform(\"androidx.compose:compose-bom:2024.12.01\"))\n" +
            "    implementation(\"androidx.core:core-ktx:1.15.0\")\n" +
            "    implementation(\"androidx.lifecycle:lifecycle-runtime-ktx:2.8.7\")\n" +
            "    implementation(\"androidx.activity:activity-compose:1.9.3\")\n" +
            "    implementation(\"androidx.compose.ui:ui\")\n" +
            "    implementation(\"androidx.compose.ui:ui-graphics\")\n" +
            "    implementation(\"androidx.compose.ui:ui-tooling-preview\")\n" +
            "    implementation(\"androidx.compose.material3:material3\")\n" +
            "    debugImplementation(\"androidx.compose.ui:ui-tooling\")\n}\n";
    }

    private static void writeProjectFiles(String projectDir, Map<String, String> files, String packageName) throws IOException {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path filePath = Path.of(projectDir, entry.getKey());
            if (filePath.getParent() != null) filePath.getParent().toFile().mkdirs();
            Files.writeString(filePath, entry.getValue());
        }

        Path gradlew = Path.of(projectDir, "gradlew");
        if (!Files.exists(gradlew)) {
            Files.writeString(gradlew, generateGradlew());
            gradlew.toFile().setExecutable(true);
        }
        Path gradlewBat = Path.of(projectDir, "gradlew.bat");
        Files.writeString(gradlewBat, "@rem Gradle wrapper script\n@rem Please do not delete\n");
    }

    private static String generateGradlew() {
        return "#!/bin/sh\n\n" +
            "##############################################################################\n" +
            "# Gradle start up script for POSIX\n" +
            "##############################################################################\n\n" +
            "APP_NAME=\"Gradle\"\n" +
            "APP_BASE_NAME=$(basename \"$0\")\n\n" +
            "DEFAULT_JVM_OPTS='\"-Xmx64m\" \"-Xms64m\"'\n\n" +
            "MAX_FD=maximum\n\n" +
            "warn () { echo \"$*\"; }\n" +
            "die () { echo; echo \"$*\"; echo; exit 1; }\n\n" +
            "CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar\n\n" +
            "exec java $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \\\n" +
            "  -classpath \"$CLASSPATH\" \\\n" +
            "  org.gradle.wrapper.GradleWrapperMain \"$@\"\n";
    }

    private static boolean buildApk(String projectDir, BuildJob job) {
        try {
            job.update("BUILDING_PROJECT", 45f, "Setting up Gradle wrapper...");

            Path gradleWrapperDir = Path.of(projectDir, "gradle/wrapper");
            gradleWrapperDir.toFile().mkdirs();

            Path gradleWrapperJar = gradleWrapperDir.resolve("gradle-wrapper.jar");
            if (!Files.exists(gradleWrapperJar)) {
                String wrapperUrl = "https://services.gradle.org/distributions/gradle-8.9-bin.zip";
                Path wrapperProps = gradleWrapperDir.resolve("gradle-wrapper.properties");
                Files.writeString(wrapperProps,
                    "distributionBase=GRADLE_USER_HOME\n" +
                    "distributionPath=wrapper/dists\n" +
                    "distributionUrl=" + wrapperUrl + "\n" +
                    "networkTimeout=10000\n" +
                    "validateDistributionUrl=true\n" +
                    "zipStoreBase=GRADLE_USER_HOME\n" +
                    "zipStorePath=wrapper/dists\n");

                downloadGradleWrapperJar(gradleWrapperJar);
            }

            job.update("BUILDING_PROJECT", 55f, "Running Gradle build...");
            Thread.sleep(300);

            String gradleHome = System.getProperty("user.home") + "/.gradle";
            ProcessBuilder pb = new ProcessBuilder(
                JAVA_HOME + "/bin/java",
                "-Xmx512m",
                "-Dorg.gradle.appname=gradlew",
                "-classpath", gradleWrapperJar.toString(),
                "org.gradle.wrapper.GradleWrapperMain",
                "assembleDebug",
                "--no-daemon",
                "--stacktrace"
            );
            pb.directory(new File(projectDir));
            pb.environment().put("JAVA_HOME", JAVA_HOME);
            pb.environment().put("ANDROID_HOME", ANDROID_SDK);
            pb.environment().put("GRADLE_USER_HOME", gradleHome);
            pb.redirectErrorStream(true);

            job.update("BUILDING_PROJECT", 60f, "Compiling code...");

            Process process = pb.start();
            String buildOutput;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (line.contains("BUILD") || line.contains("error") || line.contains("FAILED")) {
                        job.update("BUILDING_PROJECT", job.progress + 0.5f, line);
                    }
                    if (sb.length() > 50000) {
                        sb.delete(0, sb.length() - 30000);
                    }
                }
                buildOutput = sb.toString();
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                job.update("DOWNLOADING_APK", 90f, "Build successful, locating APK...");
                return true;
            } else {
                String errorSnippet = extractBuildError(buildOutput);
                failBuild(job, "Build failed: " + errorSnippet);
                return false;
            }

        } catch (Exception e) {
            failBuild(job, "Build error: " + e.getMessage());
            return false;
        }
    }

    private static void downloadGradleWrapperJar(Path dest) throws Exception {
        URL url = new URL("https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar");
        try (InputStream in = url.openStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.out.println("Failed to download gradle-wrapper.jar: " + e.getMessage());
            Files.write(dest, new byte[0]);
        }
    }

    private static String extractBuildError(String output) {
        String[] lines = output.split("\n");
        StringBuilder errors = new StringBuilder();
        for (String line : lines) {
            if (line.contains("error:") || line.contains("FAILURE") || line.contains("FAILED")) {
                errors.append(line.trim()).append("; ");
                if (errors.length() > 500) break;
            }
        }
        return errors.length() > 0 ? errors.toString() : "Unknown build error (check server logs)";
    }

    private static String findApk(String projectDir) {
        try {
            Path appBuild = Path.of(projectDir, "app/build/outputs/apk/debug");
            if (Files.exists(appBuild)) {
                try (Stream<Path> files = Files.list(appBuild)) {
                    Optional<String> apk = files
                        .map(p -> p.toString())
                        .filter(s -> s.endsWith(".apk"))
                        .findFirst();
                    if (apk.isPresent()) return apk.get();
                }
            }
            Path altBuild = Path.of(projectDir, "app/build/outputs/apk");
            if (Files.exists(altBuild)) {
                try (Stream<Path> walk = Files.walk(altBuild, 3)) {
                    Optional<String> apk = walk
                        .map(p -> p.toString())
                        .filter(s -> s.endsWith(".apk"))
                        .findFirst();
                    if (apk.isPresent()) return apk.get();
                }
            }
        } catch (Exception e) {
            System.out.println("Error finding APK: " + e.getMessage());
        }
        return null;
    }

    private static String extractContent(String responseJson) {
        try {
            int choicesIdx = responseJson.indexOf("\"content\":\"");
            if (choicesIdx < 0) {
                choicesIdx = responseJson.indexOf("\"content\": \"");
            }
            if (choicesIdx < 0) {
                return responseJson;
            }
            int start = responseJson.indexOf("\"", choicesIdx + 11) + 1;
            int end = findContentEnd(responseJson, start);
            if (end > start) {
                return unescapeJson(responseJson.substring(start, end));
            }
        } catch (Exception e) {
            System.out.println("Parse error: " + e.getMessage());
        }
        return responseJson;
    }

    private static int findContentEnd(String json, int start) {
        int i = start;
        boolean escaped = false;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
            i++;
        }
        return json.length();
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx = json.indexOf(":", idx + search.length());
        if (idx < 0) return null;
        idx++;
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        if (idx >= json.length()) return null;

        if (json.charAt(idx) == '"') {
            idx++;
            StringBuilder sb = new StringBuilder();
            while (idx < json.length()) {
                char c = json.charAt(idx);
                if (c == '\\' && idx + 1 < json.length()) {
                    char next = json.charAt(idx + 1);
                    switch (next) {
                        case '"': sb.append('"'); idx += 2; continue;
                        case '\\': sb.append('\\'); idx += 2; continue;
                        case 'n': sb.append('\n'); idx += 2; continue;
                        case 't': sb.append('\t'); idx += 2; continue;
                        default: sb.append(c); idx++; continue;
                    }
                }
                if (c == '"') break;
                sb.append(c);
                idx++;
            }
            return sb.toString();
        }
        return null;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n").replace("\\r", "\r")
            .replace("\\t", "\t").replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
