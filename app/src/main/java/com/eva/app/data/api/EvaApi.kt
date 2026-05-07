package com.eva.app.data.api

import retrofit2.Response
import retrofit2.http.*
import okhttp3.MultipartBody
import okhttp3.RequestBody

interface EvaApi {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<Map<String, String>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<RefreshResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<UserProfileResponse>

    @PATCH("auth/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserProfileResponse>

    @Multipart
    @POST("auth/photo")
    suspend fun uploadPhoto(@Part photo: MultipartBody.Part): Response<AvatarUrlResponse>

    @DELETE("auth/photo")
    suspend fun deletePhoto(): Response<Map<String, String>>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ForgotPasswordResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<Map<String, String>>

    @GET("doctors")
    suspend fun getDoctors(
        @Query("specializationId") specializationId: Short? = null,
        @Query("clinicId")         clinicId: Int? = null,
        @Query("search")           search: String? = null,
        @Query("limit")            limit: Int = 20,
        @Query("offset")           offset: Long = 0
    ): Response<DoctorListResponse>

    @GET("doctors/{id}")
    suspend fun getDoctorById(@Path("id") id: Int): Response<DoctorResponse>

    @GET("doctors/{id}/reviews")
    suspend fun getDoctorReviews(@Path("id") id: Int): Response<List<ReviewResponse>>

    @POST("doctors/{id}/reviews")
    suspend fun addReview(
        @Path("id") id: Int,
        @Body request: ReviewRequest
    ): Response<Map<String, String>>

    @GET("specializations")
    suspend fun getSpecializations(): Response<List<SpecializationResponse>>

    @GET("clinics")
    suspend fun getClinics(): Response<List<ClinicResponse>>

    @GET("schedules")
    suspend fun getSchedules(
        @Query("doctorId") doctorId: Int,
        @Query("date")     date: String? = null,
        @Query("dateTo")   dateTo: String? = null
    ): Response<List<ScheduleResponse>>

    @POST("appointments")
    suspend fun createAppointment(@Body request: CreateAppointmentRequest): Response<AppointmentResponse>

    @POST("auth/fcm-token")
    suspend fun saveFcmToken(@Body request: FcmTokenRequest): Response<Map<String, String>>

    @HTTP(method = "DELETE", path = "auth/fcm-token", hasBody = true)
    suspend fun deleteFcmToken(@Body request: FcmTokenRequest): Response<Map<String, String>>

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

    @GET("symptoms/quota")
    suspend fun getSymptomsQuota(): Response<SymptomsQuotaResponse>

    @GET("doctors/{id}/can-review")
    suspend fun canReview(@Path("id") id: Int): Response<Map<String, Boolean>>

    @PATCH("doctors/reviews/{reviewId}")
    suspend fun updateReview(
        @Path("reviewId") reviewId: String,
        @Body request: ReviewRequest
    ): Response<Map<String, String>>

    @DELETE("doctors/reviews/{reviewId}")
    suspend fun deleteReview(@Path("reviewId") reviewId: String): Response<Map<String, String>>

    @GET("documents")
    suspend fun getDocuments(): Response<List<DocumentResponse>>

    @Multipart
    @POST("documents")
    suspend fun uploadDocument(
        @Part file: okhttp3.MultipartBody.Part,
        @Part("category") category: okhttp3.RequestBody,
        @Part("description") description: okhttp3.RequestBody?
    ): Response<Map<String, String>>

    @DELETE("documents/{id}")
    suspend fun deleteDocument(@Path("id") id: String): Response<Map<String, String>>

    @PATCH("documents/{id}")
    suspend fun updateDocument(
        @Path("id") id: String,
        @Body body: UpdateDocumentRequest
    ): Response<Map<String, String>>

    @GET("notifications")
    suspend fun getNotifications(
        @Query("unread") unread: Boolean? = null
    ): Response<List<NotificationResponse>>

    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): Response<MessageResponse>

    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<MessageResponse>
}