package com.eva.app.data.api

import retrofit2.Response
import retrofit2.http.*
import retrofit2.http.PATCH

interface EvaApi {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<Map<String, String>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<UserProfileResponse>

    @PATCH("auth/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserProfileResponse>

    @GET("doctors")
    suspend fun getDoctors(
        @Query("specializationId") specializationId: Int? = null,
        @Query("clinicId") clinicId: Int? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<DoctorListResponse>

    @GET("doctors/{id}")
    suspend fun getDoctorById(@Path("id") id: Int): Response<DoctorResponse>

    @GET("doctors/{id}/reviews")
    suspend fun getDoctorReviews(@Path("id") id: Int): Response<List<ReviewResponse>>

    @POST("doctors/{id}/reviews")
    suspend fun addReview(
        @Path("id") id: Int,
        @Body request: AddReviewRequest
    ): Response<Map<String, String>>

    @GET("clinics")
    suspend fun getClinics(): Response<List<ClinicResponse>>

    @GET("schedules")
    suspend fun getSchedules(
        @Query("doctorId") doctorId: Int,
        @Query("date") date: String? = null
    ): Response<List<ScheduleResponse>>

    @POST("appointments")
    suspend fun createAppointment(@Body request: CreateAppointmentRequest): Response<AppointmentResponse>

    @GET("appointments")
    suspend fun getMyAppointments(
        @Query("status") status: String? = null
    ): Response<List<AppointmentResponse>>

    @GET("appointments/{id}")
    suspend fun getAppointmentById(@Path("id") id: String): Response<AppointmentResponse>

    @DELETE("appointments/{id}")
    suspend fun cancelAppointment(@Path("id") id: String): Response<MessageResponse>

    @POST("symptoms/analyze")
    suspend fun analyzeSymptoms(@Body request: AnalyzeSymptomsRequest): Response<AnalyzeSymptomsResponse>

    @GET("symptoms/history")
    suspend fun getSymptomsHistory(): Response<List<SymptomsHistoryResponse>>

    @GET("notifications")
    suspend fun getNotifications(
        @Query("unread") unread: Boolean? = null
    ): Response<List<NotificationResponse>>

    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): Response<MessageResponse>

    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<MessageResponse>
}