package com.eva.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "eva_prefs")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile var cachedToken: String? = null
    @Volatile var cachedRefreshToken: String? = null

    private val _unauthorizedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unauthorizedEvent: SharedFlow<Unit> = _unauthorizedEvent.asSharedFlow()

    fun emitUnauthorized() { _unauthorizedEvent.tryEmit(Unit) }

    companion object {
        private val TOKEN_KEY        = stringPreferencesKey("jwt_token")
        private val USER_ID_KEY      = stringPreferencesKey("user_id")
        private val USER_NAME_KEY    = stringPreferencesKey("user_name")
        private val CONSENT_MEDICAL  = booleanPreferencesKey("consent_medical")
        private val CONSENT_AI       = booleanPreferencesKey("consent_ai")
        // CONSENT_PRIVACY хранится только локально — принятие политики конфиденциальности
        // на уровне приложения, бэкенд хранит только CONSENT_MEDICAL и CONSENT_AI
        private val CONSENT_PRIVACY  = booleanPreferencesKey("consent_privacy")
        private val CONSENT_SHOWN    = booleanPreferencesKey("consent_shown")
        private val ONBOARDING_DONE  = booleanPreferencesKey("onboarding_done")
        private val HEALTH_SETUP_DONE = booleanPreferencesKey("health_setup_done")
        private val DARK_THEME       = booleanPreferencesKey("dark_theme")
        private val FAVORITES_KEY    = stringPreferencesKey("favorite_doctors")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val FCM_TOKEN_KEY     = stringPreferencesKey("fcm_token")
        // Health data — хранится локально (нет backend endpoint)
        private val ALLERGIES_KEY    = stringPreferencesKey("health_allergies")
        private val CHRONIC_KEY      = stringPreferencesKey("health_chronic")
        private val DOB_KEY          = stringPreferencesKey("health_dob")
    }

    val token:           Flow<String?>  = context.dataStore.data.map { it[TOKEN_KEY] }
    val refreshToken:    Flow<String?>  = context.dataStore.data.map { it[REFRESH_TOKEN_KEY] }
    val userId:          Flow<String?>  = context.dataStore.data.map { it[USER_ID_KEY] }
    val userName:        Flow<String?>  = context.dataStore.data.map { it[USER_NAME_KEY] }
    val consentMedical:  Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_MEDICAL] == true }
    val consentAi:       Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_AI] == true }
    val consentPrivacy:  Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_PRIVACY] == true }
    val consentShown:    Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_SHOWN] == true }
    val onboardingDone:  Flow<Boolean>  = context.dataStore.data.map { it[ONBOARDING_DONE] == true }
    val healthSetupDone: Flow<Boolean>  = context.dataStore.data.map { it[HEALTH_SETUP_DONE] == true }
    val darkTheme:       Flow<Boolean>  = context.dataStore.data.map { it[DARK_THEME] == true }
    val favoriteDoctors: Flow<String>   = context.dataStore.data.map { it[FAVORITES_KEY] ?: "[]" }
    val allergies:       Flow<String>   = context.dataStore.data.map { it[ALLERGIES_KEY] ?: "" }
    val chronicDiseases: Flow<String>   = context.dataStore.data.map { it[CHRONIC_KEY] ?: "" }
    val dateOfBirth:     Flow<String>   = context.dataStore.data.map { it[DOB_KEY] ?: "" }

    val requiredConsentAccepted: Flow<Boolean> = context.dataStore.data.map {
        it[CONSENT_MEDICAL] == true && it[CONSENT_PRIVACY] == true
    }

    suspend fun saveAuth(token: String, refreshToken: String, userId: String, fullName: String) {
        cachedToken        = token
        cachedRefreshToken = refreshToken
        context.dataStore.edit { prefs ->
            val prevUserId = prefs[USER_ID_KEY]
            // Сбрасываем согласие ТОЛЬКО при смене пользователя, не при каждом входе
            if (prevUserId != null && prevUserId != userId) {
                prefs.remove(CONSENT_SHOWN)
                prefs.remove(CONSENT_MEDICAL)
                prefs.remove(CONSENT_AI)
                prefs.remove(CONSENT_PRIVACY)
            }
            prefs[TOKEN_KEY]         = token
            prefs[REFRESH_TOKEN_KEY] = refreshToken
            prefs[USER_ID_KEY]       = userId
            prefs[USER_NAME_KEY]     = fullName
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        cachedToken        = accessToken
        cachedRefreshToken = refreshToken
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY]         = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
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

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    suspend fun setHealthSetupDone() {
        context.dataStore.edit { it[HEALTH_SETUP_DONE] = true }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[DARK_THEME] = enabled }
    }

    suspend fun saveFavorites(json: String) {
        context.dataStore.edit { it[FAVORITES_KEY] = json }
    }

    suspend fun saveHealthData(allergies: String, chronic: String, dob: String) {
        context.dataStore.edit {
            it[ALLERGIES_KEY] = allergies
            it[CHRONIC_KEY]   = chronic
            it[DOB_KEY]       = dob
        }
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[USER_NAME_KEY] = name }
    }

    suspend fun clearAuth() {
        cachedToken        = null
        cachedRefreshToken = null
        context.dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USER_NAME_KEY)
            prefs.remove(CONSENT_MEDICAL)
            prefs.remove(CONSENT_AI)
            prefs.remove(CONSENT_PRIVACY)
            prefs.remove(CONSENT_SHOWN)
            // Здоровье и избранное НЕ чистим — пусть остаются
        }
    }
    val fcmToken: Flow<String?> = context.dataStore.data.map { it[FCM_TOKEN_KEY] }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { it[FCM_TOKEN_KEY] = token }
    }
}