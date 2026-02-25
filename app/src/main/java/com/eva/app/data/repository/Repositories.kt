package com.eva.app.data.repository

import com.eva.app.data.api.*
import com.eva.app.data.local.TokenManager
import com.eva.app.util.Resource
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

suspend fun <T> safeApiCall(call: suspend () -> Response<T>): Resource<T> {
    return try {
        val response = call()
        if (response.isSuccessful && response.body() != null) {
            Resource.Success(response.body()!!)
        } else {
            Resource.Error("Ошибка: ${response.code()} ${response.message()}")
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
                token    = result.data.token,
                userId   = result.data.userId,
                fullName = result.data.fullName
            )
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
                    token    = loginResult.data.token,
                    userId   = loginResult.data.userId,
                    fullName = loginResult.data.fullName
                )
            }
        }
        return result
    }

    suspend fun getMe(): Resource<UserProfileResponse> =
        safeApiCall { api.getMe() }

    suspend fun updateProfile(fullName: String?, phone: String?): Resource<UserProfileResponse> {
        val result = safeApiCall { api.updateProfile(UpdateProfileRequest(fullName, phone)) }
        if (result is Resource.Success && fullName != null) {
            tokenManager.saveUserName(fullName)
        }
        return result
    }

    suspend fun logout() = tokenManager.clearAuth()
}

@Singleton
class DoctorRepository @Inject constructor(private val api: EvaApi) {

    suspend fun getDoctors(
        specializationId: Int? = null,
        search: String? = null
    ): Resource<DoctorListResponse> =
        safeApiCall { api.getDoctors(specializationId = specializationId, search = search) }

    suspend fun getDoctorById(id: Int): Resource<DoctorResponse> =
        safeApiCall { api.getDoctorById(id) }

    suspend fun getDoctorReviews(id: Int): Resource<List<ReviewResponse>> =
        safeApiCall { api.getDoctorReviews(id) }

    suspend fun addReview(doctorId: Int, rating: Int, comment: String?): Resource<Map<String, String>> =
        safeApiCall { api.addReview(doctorId, AddReviewRequest(rating, comment)) }
}

@Singleton
class ScheduleRepository @Inject constructor(private val api: EvaApi) {
    suspend fun getSchedules(doctorId: Int, date: String? = null): Resource<List<ScheduleResponse>> =
        safeApiCall { api.getSchedules(doctorId, date) }
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
class NotificationRepository @Inject constructor(private val api: EvaApi) {
    suspend fun getNotifications(): Resource<List<NotificationResponse>> =
        safeApiCall { api.getNotifications() }

    suspend fun markAllRead(): Resource<MessageResponse> =
        safeApiCall { api.markAllNotificationsRead() }
}