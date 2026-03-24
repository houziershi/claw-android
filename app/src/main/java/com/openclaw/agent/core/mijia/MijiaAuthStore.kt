package com.openclaw.agent.core.mijia

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "mijia_auth"

@Singleton
class MijiaAuthStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(auth: MijiaAuth) {
        prefs.edit().apply {
            putString("userId", auth.userId)
            putString("cUserId", auth.cUserId)
            putString("ssecurity", auth.ssecurity)
            putString("serviceToken", auth.serviceToken)
            putString("passToken", auth.passToken)
            putString("ua", auth.ua)
            putString("deviceId", auth.deviceId)
            putString("passO", auth.passO)
            putString("locale", auth.locale)
            putLong("expireTime", auth.expireTime)
            apply()
        }
    }

    fun load(): MijiaAuth? {
        val userId = prefs.getString("userId", null) ?: return null
        val ssecurity = prefs.getString("ssecurity", null) ?: return null
        val serviceToken = prefs.getString("serviceToken", null) ?: return null
        return MijiaAuth(
            userId = userId,
            cUserId = prefs.getString("cUserId", "") ?: "",
            ssecurity = ssecurity,
            serviceToken = serviceToken,
            passToken = prefs.getString("passToken", "") ?: "",
            ua = prefs.getString("ua", "") ?: "",
            deviceId = prefs.getString("deviceId", "") ?: "",
            passO = prefs.getString("passO", "") ?: "",
            locale = prefs.getString("locale", "zh_CN") ?: "zh_CN",
            expireTime = prefs.getLong("expireTime", 0L)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isAuthenticated(): Boolean = load() != null
}
