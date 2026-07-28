package com.ideaforge.ai.core.constants

object AppConstants {
    const val MAX_IDE_LENGTH = 5000
    const val MIN_IDE_LENGTH = 10
    const val DATABASE_NAME = "ideaforge_database"
    const val BUILD_HISTORY_TABLE = "build_history"
    const val PROJECT_TABLE = "projects"
    const val BUILD_LOG_TABLE = "build_logs"

    const val OPENCODE_API_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    const val OPENCODE_API_KEY = ""
    const val MODEL_ID = "gemini-2.5-flash"
    const val MODEL_NAME = "Gemini 2.5 Flash"
    const val MODEL_PROVIDER = "Google (Free)"

    const val CONNECT_TIMEOUT = 60L
    const val READ_TIMEOUT = 120L
    const val WRITE_TIMEOUT = 30L

    const val NOTIFICATION_CHANNEL_ID = "build_progress"
    const val NOTIFICATION_ID = 1001
    const val BUILD_NOTIFICATION_ID = 1001
    const val WORK_BUILD_TAG = "build_worker"
    const val DATASTORE_NAME = "ideaforge_preferences"
    const val MIN_SDK = 26
    const val TARGET_SDK = 35

    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_LANGUAGE = "language"
    const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val KEY_AUTO_FIX_ERRORS = "auto_fix_errors"
    const val KEY_MAX_RETRIES = "max_retries"
    const val KEY_CODE_QUALITY = "code_quality"

    const val PROJECTS_DIR = "IdeaForgeProjects"
}
