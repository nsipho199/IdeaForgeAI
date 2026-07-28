# IdeaForge AI — Production Validation Runbook

## Status
Autonomous repair architecture is **complete**. No code changes needed.

## Server Constraints
- No Android emulator / display / Gradle network access / DNS blocked
- **All steps must run on physical Android device via AndroidIDE**

---

## Test 1: Unit Tests (15 tests)

Open AndroidIDE terminal at project root:

```bash
./gradlew app:testDebugUnitTest --tests "com.ideaforge.ai.core.build.AutonomousRepairE2ETest"
```

**Expected:** BUILD SUCCESSFUL — 15/15 passing

Custom test scenarios covered:
| Test | Scenario |
|------|----------|
| 1a | `buildManager_autonomousLoop_successAfterGradleFix` |
| 1b | `buildManager_autonomousLoop_successAfterKotlinFix` |
| 1c | `buildManager_autonomousLoop_successAfterManifestFix` |
| 2a | `repairAgent_analyze_gradleError_returnsGradleCategory` |
| 2b | `repairAgent_analyze_manifestError_returnsManifestCategory` |
| 2c | `repairAgent_localFixSearch_returnsFix` |
| 3a | `orchestrator_fallback_geminiUnavailable_usesLocalFix` |
| 4a | `snapshotManager_createAndRollback_restoresOriginalFile` |
| 4b | `snapshotManager_noSnapshot_returnsNull` |
| 5a | `buildManager_maxRetriesEnforced_after10Failures` |
| 5b | `buildManager_extendsProgressCallback_withStageUpdates` |
| 6a | `localFixDatabase_findFixes_missingComposeBom_returnsFix` |
| 6b | `localFixDatabase_applyFix_missingComposeBom_fixesFile` |
| 6c | `repairAgent_extractFailingFiles_fromGradleLog` |
| 6d | `buildManager_rollbackOnNoChange` |

---

## Test 2: Real Autonomous Repair — Gradle Dependency Break

### 2a — Create Test Project

1. Open IdeaForgeAI app on AndroidIDE
2. Enter any app idea (e.g., "Simple counter app")
3. Tap **Build**
4. Wait for initial generation — verify first BUILD SUCCESSFUL

### 2b — Inject Error

Go to **Settings → Manage Generated Projects** (or navigate to generated output). Find the generated project's `app/build.gradle.kts`. Change:

```kotlin
// WRONG
implementation(platform("androidx.compose:compose-bom:2099.99.99"))
```

### 2c — Rebuild

Build again in IdeaForgeAI. Monitor the log:

```
[BUILD] Starting autonomous build pipeline
[AI] Requesting Gemini code generation...
[BUILD] Upload complete (branch: main)
[BUILD] Build triggered (run: <id>)
[BUILD] [In Progress] Compiling...
[BUILD] Compilation failed
  ↓
[ANALYZER] Analyzing build failure...
[ANALYZER] Category: GRADLE
[ANALYZER] Root cause: compose-bom version 2099.99.99 not found
  ↓
[FIX SEARCH] Checking local error intelligence database...
[FIX SEARCH] Found 1 local solution(s): [missing_compose_bom]
  ↓
[AI] Provider: Local Fix Database
[PATCH] Modified app/build.gradle.kts
  ↓
[REBUILD] Attempt 1/10 with 1 patched file(s)
  ↓
[SUCCESS] APK generated: <path>
```

### 2d — Record

| Criterion | Yes/No |
|-----------|--------|
| Error detected automatically? |
| Correct category (GRADLE)? |
| Local fix found? |
| Fix applied to file? |
| Rebuild triggered automatically? |
| APK produced? |
| Repair attempts (count): |

---

## Test 3: Gemini Disabled — Fallback Test

### 3a — Repeat same broken project (or re-break)

Ensure `app/build.gradle.kts` still has the bad compose-bom.

### 3b — Disable Gemini

1. IdeaForgeAI → **Settings**
2. **AI Model** section → Clear the API key field
3. Save

### 3c — Build

Tap **Build**. Monitor the log:

```
[BUILD] Starting autonomous build pipeline
[AI] Requesting Gemini code generation...
  ↓ error: API key not configured
[AI] Generation failed: No API key
  ↓
[ANALYZER] Analyzing build failure...
[ANALYZER] Category: GRADLE
[FIX SEARCH] Found 1 local solution(s): [missing_compose_bom]
[AI] Provider: Local Fix Database      ← NO Gemini call
[PATCH] Modified app/build.gradle.kts
[REBUILD] Attempt 1/10
[SUCCESS] APK generated
```

**Important:** The fix for the compose-bom error is in **LocalFixDatabase**, not in Gemini. The local DB has all 14 fix patterns including `missing_compose_bom`. This proves the fallback chain works even when Gemini is completely unavailable.

### 3d — Record

| Criterion | Yes/No |
|-----------|--------|
| Gemini unavailable detected? |
| Fallback activated? |
| LocalFixDatabase matched error? |
| Fix template applied? |
| Pipeline continued without Gemini? |
| APK generated? |

---

## Test 4: Rollback Verification

During any repair attempt, if the pipeline logs:

```
[REBUILD] AI fix returned no changes, rolling back...
[ROLLBACK] Original project restored from snapshot
```

This proves the snapshot/rollback safety net works. Confirm by inspection:

1. Open the repaired file(s)
2. Verify they match the pre-repair content
3. Verify build continues after rollback

---

## Final Report Template

Copy this into your response:

```
Real test result:
- Tests passed:           ___ / 15
- Test 2 (Gradle fix):
    Error detected:       Yes / No
    Category correct:     Yes / No
    Fix generated:        Yes / No
    APK produced:         Yes / No
    Repair attempts:      ___
- Test 3 (Gemini failover):
    Fallback activated:   Yes / No
    Local DB matched:     Yes / No
    APK produced:         Yes / No
- Safety checks:
    Max 10 attempts:      Yes / No
    Rollback verified:    Yes / No
    No crashes:           Yes / No
    Log prefixes correct: Yes / No
- Time taken:             ___ minutes
- Remaining blockers:     ___ (list)
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `android.util.Log` ClassNotFoundException | Test not using Android runner | Add `@RunWith(AndroidJUnit4::class)` or use `testOptions.unitTests.includeAndroidResources = true` |
| Tests compile but skip | Missing mock dependencies | Run `./gradlew app:dependencies` to check test config |
| "No matching tests found" | Typo in test filter | Verify exact class name: `com.ideaforge.ai.core.build.AutonomousRepairE2ETest` |
| Build fails with OOM | 1024m heap limit | Ensure `gradle.properties` has `org.gradle.jvmargs=-Xmx1024m` |
| GitHub Actions never completes | Token scopes wrong | Verify token has `repo` + `workflow` scopes (Settings → Developer settings → Tokens) |

---

*Architecture frozen until this runbook passes all 3 tests.*
