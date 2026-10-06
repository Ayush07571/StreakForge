package com.ayush.streakforge

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStorage(context: Context) {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secret_tokens",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("secret_tokens_fallback", Context.MODE_PRIVATE)
        }
    }

    fun getActiveUser(): String = prefs.getString("active_user", "") ?: ""

    fun setActiveUser(user: String) {
        prefs.edit().putString("active_user", user.trim()).apply()
    }

    fun getToken(user: String): String {
        if (user.isBlank()) return ""
        return prefs.getString("token_$user", "") ?: ""
    }

    fun saveCredentials(user: String, token: String) {
        val trimmedUser = user.trim()
        val trimmedToken = token.trim()
        prefs.edit()
            .putString("active_user", trimmedUser)
            .putString("token_$trimmedUser", trimmedToken)
            .apply()
    }

    fun clearToken(user: String) {
        prefs.edit().remove("token_$user").apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
