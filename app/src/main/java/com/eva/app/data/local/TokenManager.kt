package com.eva.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
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
        private val TOKEN_KEY        = stringPreferencesKey("jwt_token")
        private val USER_ID_KEY      = stringPreferencesKey("user_id")
        private val USER_NAME_KEY    = stringPreferencesKey("user_name")
        private val CONSENT_MEDICAL  = booleanPreferencesKey("consent_medical")
        private val CONSENT_AI       = booleanPreferencesKey("consent_ai")
        private val CONSENT_PRIVACY  = booleanPreferencesKey("consent_privacy")
        private val CONSENT_SHOWN    = booleanPreferencesKey("consent_shown")
        private val ONBOARDING_DONE  = booleanPreferencesKey("onboarding_done")
        private val DARK_THEME       = booleanPreferencesKey("dark_theme")
        private val FAVORITES_KEY    = stringPreferencesKey("favorite_doctors")  // JSON array of IDs
        private val ALLERGIES_KEY    = stringPreferencesKey("health_allergies")
        private val CHRONIC_KEY      = stringPreferencesKey("health_chronic")
        private val INSURANCE_KEY    = stringPreferencesKey("health_insurance")
        private val DOB_KEY          = stringPreferencesKey("health_dob")
    }

    val token:           Flow<String?>  = context.dataStore.data.map { it[TOKEN_KEY] }
    val userId:          Flow<String?>  = context.dataStore.data.map { it[USER_ID_KEY] }
    val userName:        Flow<String?>  = context.dataStore.data.map { it[USER_NAME_KEY] }
    val consentMedical:  Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_MEDICAL] == true }
    val consentAi:       Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_AI] == true }
    val consentPrivacy:  Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_PRIVACY] == true }
    val consentShown:    Flow<Boolean>  = context.dataStore.data.map { it[CONSENT_SHOWN] == true }
    val onboardingDone:  Flow<Boolean>  = context.dataStore.data.map { it[ONBOARDING_DONE] == true }
    val darkTheme:       Flow<Boolean>  = context.dataStore.data.map { it[DARK_THEME] == true }
    val favoriteDoctors: Flow<String>   = context.dataStore.data.map { it[FAVORITES_KEY] ?: "[]" }
    val allergies:       Flow<String>   = context.dataStore.data.map { it[ALLERGIES_KEY] ?: "" }
    val chronicDiseases: Flow<String>   = context.dataStore.data.map { it[CHRONIC_KEY] ?: "" }
    val insurancePolicy: Flow<String>   = context.dataStore.data.map { it[INSURANCE_KEY] ?: "" }
    val dateOfBirth:     Flow<String>   = context.dataStore.data.map { it[DOB_KEY] ?: "" }

    val requiredConsentAccepted: Flow<Boolean> = context.dataStore.data.map {
        it[CONSENT_MEDICAL] == true && it[CONSENT_PRIVACY] == true
    }

    suspend fun saveAuth(token: String, userId: String, fullName: String) {
        context.dataStore.edit { prefs ->
            val prevUserId = prefs[USER_ID_KEY]
            if (prevUserId != null && prevUserId != userId) {
                prefs.remove(CONSENT_SHOWN)
                prefs.remove(CONSENT_MEDICAL)
                prefs.remove(CONSENT_AI)
                prefs.remove(CONSENT_PRIVACY)
            }
            prefs[TOKEN_KEY]     = token
            prefs[USER_ID_KEY]   = userId
            prefs[USER_NAME_KEY] = fullName
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

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[DARK_THEME] = enabled }
    }

    suspend fun saveFavorites(json: String) {
        context.dataStore.edit { it[FAVORITES_KEY] = json }
    }

    suspend fun saveHealthData(
        allergies: String, chronic: String, insurance: String, dob: String
    ) {
        context.dataStore.edit {
            it[ALLERGIES_KEY] = allergies
            it[CHRONIC_KEY]   = chronic
            it[INSURANCE_KEY] = insurance
            it[DOB_KEY]       = dob
        }
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[USER_NAME_KEY] = name }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USER_NAME_KEY)
            prefs.remove(CONSENT_MEDICAL)
            prefs.remove(CONSENT_AI)
            prefs.remove(CONSENT_PRIVACY)
            prefs.remove(CONSENT_SHOWN)
        }
    }
}