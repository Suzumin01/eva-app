package com.eva.app.data.repository

import com.eva.app.data.api.*
import com.eva.app.data.local.TokenManager
import com.eva.app.util.Resource
import kotlinx.coroutines.flow.first
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

suspend fun <T> safeApiCall(call: suspend () -> Response<T>): Resource<T> {
    return try {
        val response = call()
        if (response.isSuccessful && response.body() != null) {
            Resource.Success(response.body()!!)
        } else {
            val errorMessage = response.errorBody()?.string()
                ?.let { body ->
                    runCatching { org.json.JSONObject(body).getString("message") }.getOrNull()
                }
                ?: "Ошибка: ${response.code()}"
            Resource.Error(errorMessage)
        }
    } catch (e: Exception) {
        Resource.Error("Нет соединения с сервером: ${e.localizedMessage}")
    }
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: EvaApi,
    private val tokenManager: TokenManager
) {
    suspend fun login(email: String, password: String): Resource<AuthResponse> {
        val result = safeApiCall { api.login(LoginRequest(email, password)) }
        if (result is Resource.Success) {
            tokenManager.saveAuth(
                token        = result.data.token,
                refreshToken = result.data.refreshToken,
                userId       = result.data.userId,
                fullName     = result.data.fullName
            )
            // После входа (особенно при смене аккаунта) FCM-токен нужно заново
            // привязать к новому userId на сервере — Firebase не вызывает onNewToken
            // повторно, если токен устройства не изменился.
            val fcmToken = tokenManager.fcmToken.first()
            if (fcmToken != null) {
                runCatching { api.saveFcmToken(FcmTokenRequest(fcmToken)) }
            }
        }
        return result
    }

    suspend fun register(
        fullName: String, email: String, phone: String?, password: String
    ): Resource<Map<String, String>> {
        val result = safeApiCall { api.register(RegisterRequest(fullName, email, phone, password)) }
        if (result is Resource.Success) {
            val loginResult = safeApiCall { api.login(LoginRequest(email, password)) }
            if (loginResult is Resource.Success) {
                tokenManager.saveAuth(
                    token        = loginResult.data.token,
                    refreshToken = loginResult.data.refreshToken,
                    userId       = loginResult.data.userId,
                    fullName     = loginResult.data.fullName
                )
            } else {
                return Resource.Error("REGISTERED_LOGIN_FAILED")
            }
        }
        return result
    }

    suspend fun getMe(): Resource<UserProfileResponse> =
        safeApiCall { api.getMe() }

    suspend fun updateProfile(
        fullName: String?,
        phone: String?,
        dateOfBirth: String? = null,
        allergies: String? = null,
        chronicDiseases: String? = null,
        insurancePolicy: String? = null
    ): Resource<UserProfileResponse> {
        val result = safeApiCall {
            api.updateProfile(UpdateProfileRequest(
                fullName        = fullName,
                phone           = phone,
                dateOfBirth     = dateOfBirth,
                allergies       = allergies,
                chronicDiseases = chronicDiseases,
                insurancePolicy = insurancePolicy
            ))
        }
        if (result is Resource.Success && fullName != null) {
            tokenManager.saveUserName(fullName)
        }
        return result
    }

    suspend fun saveFcmToken(token: String): Resource<Unit> {
        val result = safeApiCall { api.saveFcmToken(FcmTokenRequest(token)) }
        return if (result is Resource.Success) Resource.Success(Unit)
        else Resource.Error("Ошибка сохранения токена")
    }

    suspend fun deleteFcmToken(token: String): Resource<Unit> {
        val result = safeApiCall { api.deleteFcmToken(FcmTokenRequest(token)) }
        return if (result is Resource.Success) Resource.Success(Unit)
        else Resource.Error("Ошибка удаления токена")
    }

    suspend fun uploadPhoto(file: java.io.File): Resource<AvatarUrlResponse> {
        val reqFile = file.asRequestBody("image/*".toMediaType())
        val part    = MultipartBody.Part.createFormData("photo", file.name, reqFile)
        return safeApiCall { api.uploadPhoto(part) }
    }

    suspend fun logout() = tokenManager.clearAuth()
}

@Singleton
class DoctorRepository @Inject constructor(
    private val api: EvaApi,
    private val cache: com.eva.app.data.local.room.DoctorCacheDao
) {

    suspend fun getDoctors(
        specId: Int? = null, clinicId: Int? = null, search: String? = null,
        limit: Int = 20, offset: Long = 0
    ): Resource<DoctorListResponse> {
        return try {
            val remote = safeApiCall {
                api.getDoctors(specId?.toShort(), clinicId, search, limit, offset)
            }
            if (remote is Resource.Success && offset == 0L) {
                // Кэшируем только при запросе без фильтров
                if (specId == null && clinicId == null && search == null) {
                    cache.upsertAll(remote.data.doctors.map { it.toCached() })
                }
            }
            if (remote is Resource.Error && offset == 0L && clinicId == null) {
                val cached = if (!search.isNullOrBlank()) cache.search("%$search%") else cache.getAll()
                if (cached.isNotEmpty()) {
                    return Resource.Success(DoctorListResponse(
                        doctors = cached.map { it.toResponse() }, total = cached.size))
                }
            }
            remote
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Ошибка загрузки врачей")
        }
    }

    suspend fun getDoctorById(id: Int): Resource<DoctorResponse> =
        safeApiCall { api.getDoctorById(id) }

    suspend fun getDoctorReviews(id: Int): Resource<List<ReviewResponse>> =
        safeApiCall { api.getDoctorReviews(id) }

    suspend fun canReview(doctorId: Int): Resource<Map<String, Boolean>> =
        safeApiCall { api.canReview(doctorId) }

    suspend fun updateReview(reviewId: String, rating: Int, comment: String?): Resource<Map<String, String>> =
        safeApiCall { api.updateReview(reviewId, UpdateReviewRequest(rating, comment)) }

    suspend fun deleteReview(reviewId: String): Resource<Map<String, String>> =
        safeApiCall { api.deleteReview(reviewId) }

    suspend fun addReview(doctorId: Int, rating: Int, comment: String?): Resource<Map<String, String>> =
        safeApiCall { api.addReview(doctorId, AddReviewRequest(rating, comment)) }
}

private fun DoctorResponse.toCached() = com.eva.app.data.local.room.CachedDoctor(
    doctorId = doctorId, fullName = fullName,
    specializationName = specializationName, clinicName = clinicName,
    clinicAddress = clinicAddress, rating = rating,
    experienceYears = experienceYears, reviewsCount = reviewsCount,
    bio = bio, isActive = true
)

private fun com.eva.app.data.local.room.CachedDoctor.toResponse() = DoctorResponse(
    doctorId = doctorId, fullName = fullName,
    clinicId = 0, clinicName = clinicName,
    clinicAddress = clinicAddress,
    specializationId = 0, specializationName = specializationName,
    rating = rating, experienceYears = experienceYears,
    reviewsCount = reviewsCount, bio = bio, photoUrl = null
)

@Singleton
class ScheduleRepository @Inject constructor(private val api: EvaApi) {
    suspend fun getSchedules(doctorId: Int, date: String? = null, dateTo: String? = null): Resource<List<ScheduleResponse>> =
        safeApiCall { api.getSchedules(doctorId, date, dateTo) }
}

@Singleton
class AppointmentRepository @Inject constructor(private val api: EvaApi) {

    suspend fun createAppointment(doctorId: Int, scheduleId: Long, notes: String?): Resource<AppointmentResponse> =
        safeApiCall { api.createAppointment(CreateAppointmentRequest(doctorId, scheduleId, notes)) }

    suspend fun getMyAppointments(status: String? = null): Resource<List<AppointmentResponse>> =
        safeApiCall { api.getMyAppointments(status) }

    suspend fun cancelAppointment(id: String): Resource<MessageResponse> =
        safeApiCall { api.cancelAppointment(id) }
}

@Singleton
class SymptomsRepository @Inject constructor(private val api: EvaApi) {
    suspend fun analyze(text: String): Resource<AnalyzeSymptomsResponse> =
        safeApiCall { api.analyzeSymptoms(AnalyzeSymptomsRequest(text)) }

    suspend fun getHistory(): Resource<List<SymptomsHistoryResponse>> =
        safeApiCall { api.getSymptomsHistory() }
}

@Singleton
class DocumentRepository @Inject constructor(private val api: EvaApi) {
    suspend fun getDocuments(): Resource<List<DocumentResponse>> = safeApiCall { api.getDocuments() }

    suspend fun uploadDocument(
        file: java.io.File, category: String, description: String?
    ): Resource<Map<String, String>> {
        val mimeType = if (file.name.endsWith(".pdf", true)) "application/pdf" else "image/*"
        val reqFile  = file.asRequestBody(mimeType.toMediaType())
        val part     = MultipartBody.Part.createFormData("file", file.name, reqFile)
        val catBody  = category.toRequestBody("text/plain".toMediaType())
        val descBody = description?.toRequestBody("text/plain".toMediaType())
        return safeApiCall { api.uploadDocument(part, catBody, descBody) }
    }

    suspend fun deleteDocument(id: String): Resource<Map<String, String>> =
        safeApiCall { api.deleteDocument(id) }
}

@Singleton
class ClinicRepository @Inject constructor(private val api: EvaApi) {
    suspend fun getClinics(): Resource<List<ClinicResponse>> =
        safeApiCall { api.getClinics() }
}

@Singleton
class SpecializationRepository @Inject constructor(private val api: EvaApi) {
    @Volatile private var cachedSpecs: List<com.eva.app.data.api.SpecializationResponse>? = null

    suspend fun getSpecializations(): Resource<List<com.eva.app.data.api.SpecializationResponse>> {
        cachedSpecs?.let { return Resource.Success(it) }
        return safeApiCall { api.getSpecializations() }.also { result ->
            if (result is Resource.Success) cachedSpecs = result.data
        }
    }

    fun findIdByName(name: String): Int? {
        cachedSpecs?.find { it.name.equals(name, ignoreCase = true) }
            ?.specializationId?.let { return it }
        return com.eva.app.util.Specializations.findIdByName(name)
    }
}

@Singleton
class NotificationRepository @Inject constructor(private val api: EvaApi) {
    suspend fun getNotifications(): Resource<List<NotificationResponse>> =
        safeApiCall { api.getNotifications() }

    suspend fun markRead(id: String): Resource<MessageResponse> =
        safeApiCall { api.markNotificationRead(id) }

    suspend fun markAllRead(): Resource<MessageResponse> =
        safeApiCall { api.markAllNotificationsRead() }
}