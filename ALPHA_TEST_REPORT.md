# IdeaForge AI — Alpha Test Report

**Version:** 1.0.0-alpha
**Date:** _______________
**Device:** _______________
**Android Version:** _______________
**Tester:** _______________

---

## 1. First-Run Experience

| # | Test Case | Status | Notes |
|---|-----------|--------|-------|
| 1.1 | Fresh install — app launches without crash | PASS / FAIL | |
| 1.2 | Home screen shows "GitHub Token Required" warning | PASS / FAIL | |
| 1.3 | Tapping warning navigates to Settings | PASS / FAIL | |
| 1.4 | Enter valid GitHub token (ghp_...) | PASS / FAIL | |
| 1.5 | Token saved — warning disappears from Home | PASS / FAIL | |
| 1.6 | Token masked in Settings (••••xxxx) | PASS / FAIL | |
| 1.7 | Kill app, reopen — token persists | PASS / FAIL | |
| 1.8 | Token stored in EncryptedSharedPreferences (verify via adb) | PASS / FAIL | |

---

## 2. Build Tests — Complete Pipeline

### Test 2.1: Hello World
**Prompt:** "A simple Hello World app with a centered text saying Hello World"

| Stage | Status | Duration |
|-------|--------|----------|
| AI Generation | PASS / FAIL | ___s |
| Validation | PASS / FAIL | ___s |
| Repository Setup | PASS / FAIL | ___s |
| File Upload | PASS / FAIL | ___s |
| GitHub Actions Trigger | PASS / FAIL | ___s |
| Build Status Polling | PASS / FAIL | ___s |
| Log Retrieval | PASS / FAIL | ___s |
| APK Download | PASS / FAIL | ___s |
| APK Verification | PASS / FAIL | |
| APK Installation | PASS / FAIL | |
| **Total Build Time** | | **___m ___s** |

---

### Test 2.2: Calculator
**Prompt:** "A calculator app with basic arithmetic operations (+, -, *, /), a display screen, and buttons for digits 0-9 and operations"

| Stage | Status | Duration |
|-------|--------|----------|
| AI Generation | PASS / FAIL | ___s |
| Validation | PASS / FAIL | ___s |
| Repository Setup | PASS / FAIL | ___s |
| File Upload | PASS / FAIL | ___s |
| GitHub Actions Trigger | PASS / FAIL | ___s |
| Build Status Polling | PASS / FAIL | ___s |
| Log Retrieval | PASS / FAIL | ___s |
| APK Download | PASS / FAIL | ___s |
| APK Verification | PASS / FAIL | |
| APK Installation | PASS / FAIL | |
| **Total Build Time** | | **___m ___s** |

---

### Test 2.3: Notes
**Prompt:** "A notes app where I can create, edit, and delete notes. Each note has a title and content. Notes are displayed in a list."

| Stage | Status | Duration |
|-------|--------|----------|
| AI Generation | PASS / FAIL | ___s |
| Validation | PASS / FAIL | ___s |
| Repository Setup | PASS / FAIL | ___s |
| File Upload | PASS / FAIL | ___s |
| GitHub Actions Trigger | PASS / FAIL | ___s |
| Build Status Polling | PASS / FAIL | ___s |
| Log Retrieval | PASS / FAIL | ___s |
| APK Download | PASS / FAIL | ___s |
| APK Verification | PASS / FAIL | |
| APK Installation | PASS / FAIL | |
| **Total Build Time** | | **___m ___s** |

---

### Test 2.4: To-do List
**Prompt:** "A to-do list app where I can add tasks, mark them complete, and delete them. Tasks should have checkboxes."

| Stage | Status | Duration |
|-------|--------|----------|
| AI Generation | PASS / FAIL | ___s |
| Validation | PASS / FAIL | ___s |
| Repository Setup | PASS / FAIL | ___s |
| File Upload | PASS / FAIL | ___s |
| GitHub Actions Trigger | PASS / FAIL | ___s |
| Build Status Polling | PASS / FAIL | ___s |
| Log Retrieval | PASS / FAIL | ___s |
| APK Download | PASS / FAIL | ___s |
| APK Verification | PASS / FAIL | |
| APK Installation | PASS / FAIL | |
| **Total Build Time** | | **___m ___s** |

---

### Test 2.5: Weather App
**Prompt:** "A weather app that shows current temperature, weather condition with icon, humidity, wind speed, and a 5-day forecast."

| Stage | Status | Duration |
|-------|--------|----------|
| AI Generation | PASS / FAIL | ___s |
| Validation | PASS / FAIL | ___s |
| Repository Setup | PASS / FAIL | ___s |
| File Upload | PASS / FAIL | ___s |
| GitHub Actions Trigger | PASS / FAIL | ___s |
| Build Status Polling | PASS / FAIL | ___s |
| Log Retrieval | PASS / FAIL | ___s |
| APK Download | PASS / FAIL | ___s |
| APK Verification | PASS / FAIL | |
| APK Installation | PASS / FAIL | |
| **Total Build Time** | | **___m ___s** |

---

## 3. Build Success Rate

| Metric | Value |
|--------|-------|
| Total builds attempted | /5 |
| Builds succeeded | /5 |
| Builds failed | /5 |
| Builds needing AI repair | /5 |
| AI repair success rate | /5 |
| **Overall success rate** | **__%** |

---

## 4. Failure Simulation Tests

| # | Scenario | Expected Behavior | Actual Behavior | Status |
|---|----------|-------------------|-----------------|--------|
| 4.1 | Disable internet during upload | Upload fails, error shown | | PASS / FAIL |
| 4.2 | Restore internet after disconnect | Retry succeeds or error shown | | PASS / FAIL |
| 4.3 | Force close app during build | WorkManager resumes on reopen | | PASS / FAIL |
| 4.4 | Reboot phone during build | WorkManager resumes after reboot | | PASS / FAIL |
| 4.5 | Low storage (<100MB free) | Graceful error message | | PASS / FAIL |
| 4.6 | GitHub API rate limit (429) | Retry with backoff, then error | | PASS / FAIL |
| 4.7 | Invalid GitHub token | Error: "token required" | | PASS / FAIL |
| 4.8 | Expired GitHub token | Error from GitHub API | | PASS / FAIL |
| 4.9 | Build timeout (>15 min) | Timeout error shown | | PASS / FAIL |

---

## 5. Performance Metrics

### 5.1 Build Timing (average across all builds)

| Stage | Average Duration |
|-------|-----------------|
| AI Code Generation | ___s |
| Project Validation | ___s |
| File Upload (GitHub API) | ___s |
| GitHub Actions Build | ___m ___s |
| APK Download | ___s |
| **Total Pipeline** | **___m ___s** |

### 5.2 Resource Usage

| Metric | Value |
|--------|-------|
| Peak memory (PSS) | ___MB |
| Storage consumed per build | ___MB |
| Total storage after 5 builds | ___MB |
| Battery drain during single build | ___% |
| Network data used per build | ___MB |

---

## 6. Crash Log

| # | Timestamp | Screen/Action | Exception | Root Cause |
|---|-----------|---------------|-----------|------------|
| | | | | |
| | | | | |
| | | | | |

**Total crashes:** ___

---

## 7. UI/UX Issues

| # | Screen | Issue Description | Severity |
|---|--------|-------------------|----------|
| | | | |
| | | | |

---

## 8. Known Issues

| # | Issue | Severity | Workaround |
|---|-------|----------|------------|
| 1 | FOREGROUND_SERVICE_DATA_SYNC permission missing (FIXED) | Critical | Fixed in code |
| 2 | WorkManager not configured for Hilt (FIXED) | Critical | Fixed in code |
| | | | |

---

## 9. Recommendations

- [ ] Ready for Beta
- [ ] Needs fixes before Beta (list below)

** blockers:**

---

## 10. Sign-off

| Role | Name | Date | Approve |
|------|------|------|---------|
| Tester | | | YES / NO |
| Developer | | | YES / NO |
