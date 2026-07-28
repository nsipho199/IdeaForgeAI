package com.ideaforge.ai.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureTokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias: String? by lazy {
        try {
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        } catch (e: Exception) {
            Log.e("SecureTokenStorage", "Failed to create master key: ${e.message}")
            null
        }
    }

    private val prefs: SharedPreferences? by lazy {
        val alias = masterKeyAlias ?: return@lazy null
        try {
            EncryptedSharedPreferences.create(
                "ideaforge_secure_prefs",
                alias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SecureTokenStorage", "Failed to create encrypted prefs: ${e.message}")
            null
        }
    }

    private val fallbackPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("ideaforge_fallback_prefs", Context.MODE_PRIVATE)
    }

    fun getGitHubToken(): String {
        val encrypted = prefs?.getString(KEY_GITHUB_TOKEN, null)
        if (!encrypted.isNullOrBlank()) return encrypted
        return fallbackPrefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
    }

    fun setGitHubToken(token: String) {
        try { prefs?.edit()?.putString(KEY_GITHUB_TOKEN, token)?.apply() } catch (_: Exception) {}
        fallbackPrefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }

    fun clearGitHubToken() {
        try { prefs?.edit()?.remove(KEY_GITHUB_TOKEN)?.apply() } catch (_: Exception) {}
        fallbackPrefs.edit().remove(KEY_GITHUB_TOKEN).apply()
    }

    fun hasGitHubToken(): Boolean = getGitHubToken().isNotBlank()

    fun getOpenCodeApiKey(): String {
        val encrypted = prefs?.getString(KEY_OPENCODE_API_KEY, null)
        if (!encrypted.isNullOrBlank()) return encrypted
        return fallbackPrefs.getString(KEY_OPENCODE_API_KEY, "") ?: ""
    }

    fun setOpenCodeApiKey(key: String) {
        try { prefs?.edit()?.putString(KEY_OPENCODE_API_KEY, key)?.apply() } catch (_: Exception) {}
        fallbackPrefs.edit().putString(KEY_OPENCODE_API_KEY, key).apply()
    }

    companion object {
        private const val KEY_GITHUB_TOKEN = "gh_pat_encrypted"
        private const val KEY_OPENCODE_API_KEY = "opencode_api_key"
    }
}
