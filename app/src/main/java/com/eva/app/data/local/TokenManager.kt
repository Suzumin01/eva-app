package com.eva.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "eva_prefs")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val TOKEN_KEY          = stringPreferencesKey("jwt_token")
        private val USER_ID_KEY        = stringPreferencesKey("user_id")
        private val USER_NAME_KEY      = stringPreferencesKey("user_name")
        private val CONSENT_MEDICAL    = booleanPreferencesKey("consent_medical")
        private val CONSENT_AI         = booleanPreferencesKey("consent_ai")
        private val CONSENT_PRIVACY    = booleanPreferencesKey("consent_privacy")
        // true = экран согласий уже показывался после последнего входа
        private val CONSENT_SHOWN      = booleanPreferencesKey("consent_shown")
    }

    val token:          Flow<String?>  = context.dataStore.data.map { it[TOKEN_KEY] }
    val userId:         Flow<String?>  = context.dataStore.data.map { it[USER_ID_KEY] }
    val userName:       Flow<String?>  = context.dataStore.data.map { it[USER_NAME_KEY] }
    val consentMedical: Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_MEDICAL] == true }
    val consentAi:      Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_AI] == true }
    val consentPrivacy: Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_PRIVACY] == true }
    val consentShown:   Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_SHOWN] == true }

    // Обязательные согласия (медицина + политика) приняты
    val requiredConsentAccepted: Flow<Boolean> = context.dataStore.data.map {
        it[CONSENT_MEDICAL] == true && it[CONSENT_PRIVACY] == true
    }

    suspend fun saveAuth(token: String, userId: String, fullName: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY]     = token
            prefs[USER_ID_KEY]   = userId
            prefs[USER_NAME_KEY] = fullName
            // Сброс флага показа согласий при новом входе
            prefs.remove(CONSENT_SHOWN)
        }
    }

    suspend fun saveConsents(medical: Boolean, ai: Boolean, privacy: Boolean) {
        context.dataStore.edit {
            it[CONSENT_MEDICAL] = medical
            it[CONSENT_AI]      = ai
            it[CONSENT_PRIVACY] = privacy
            it[CONSENT_SHOWN]   = true
        }
    }

    suspend fun markConsentShown() {
        context.dataStore.edit { it[CONSENT_SHOWN] = true }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { it.clear() }
    }
}