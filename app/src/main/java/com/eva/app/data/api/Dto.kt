package com.eva.app.data.api

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val phone: String?,
    val password: String,
    val consentMedical: Boolean = true,
    val consentAi: Boolean = true
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val fullName: String,
    val role: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class RefreshResponse(
    val token: String,
    val refreshToken: String
)

data class UserProfileResponse(
    val userId: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val role: String,
    val isActive: Boolean,
    val consentMedical: Boolean,
    val consentAi: Boolean,
    val avatarUrl: String? = null,
    val dateOfBirth: String? = null,
    val allergies: String? = null,
    val chronicDiseases: String? = null,
    val insurancePolicy: String? = null
)

data class AvatarUrlResponse(val avatarUrl: String)

data class ForgotPasswordRequest(val email: String)

data class ForgotPasswordResponse(
    val message: String,
    val resetToken: String? = null
)

data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)

data class UpdateProfileRequest(
    val fullName: String?,
    val phone: String?,
    val dateOfBirth: String? = null,
    val allergies: String? = null,
    val chronicDiseases: String? = null,
    val insurancePolicy: String? = null
)

data class DocumentResponse(
    val documentId:  String,
    val fileName:    String,
    val fileType:    String,
    val fileSize:    Long,
    val category:    String,
    val description: String?,
    val createdAt:   String,
    val downloadUrl: String
)

data class DoctorResponse(
    val doctorId: Int,
    val fullName: String,
    val clinicId: Int,
    val clinicName: String,
    val clinicAddress: String,
    val specializationId: Int,
    val specializationName: String,
    val bio: String?,
    val photoUrl: String?,
    val experienceYears: Int?,
    val rating: String?,
    val reviewsCount: Int
)

data class DoctorListResponse(
    val doctors: List<DoctorResponse>,
    val total: Int
)

data class ReviewResponse(
    val reviewId: String,
    val userId: String,
    val userFullName: String,
    val rating: Int,
    val comment: String?,
    val createdAt: String
)

data class UpdateReviewRequest(
    val rating: Int,
    val comment: String?
)

data class AddReviewRequest(
    val rating: Int,
    val comment: String?
)

data class ClinicResponse(
    val clinicId: Int,
    val clinicName: String,
    val address: String,
    val phone: String?,
    val latitude: String?,
    val longitude: String?,
    val rating: String? = null,
    val doctorsCount: Int = 0
)

data class ScheduleResponse(
    val scheduleId: Long,
    val doctorId: Int,
    val doctorName: String,
    val slotDate: String,
    val slotTime: String,
    val durationMinutes: Int,
    val isAvailable: Boolean
)

data class CreateAppointmentRequest(
    val doctorId: Int,
    val scheduleId: Long,
    val notes: String?
)

data class AppointmentResponse(
    val appointmentId: String,
    val doctorId: Int,
    val doctorName: String,
    val specializationName: String,
    val clinicName: String,
    val clinicAddress: String,
    val slotDate: String,
    val slotTime: String,
    val durationMinutes: Int,
    val status: String,
    val notes: String?,
    val doctorConclusion: String? = null,
    val patientHealthInfo: String? = null,
    val createdAt: String
)

data class FcmTokenRequest(
    val token: String,
    val deviceId: String? = null
)

data class AnalyzeSymptomsRequest(
    val symptomsText: String
)

data class AnalyzeSymptomsResponse(
    val requestId: String,
    val diagnosis: String,
    val recommendations: String,
    val urgency: String,
    val confidence: String,
    val modelVersion: String,
    val processingMs: Int?,
    val isStub: Boolean,
    val specializationName: String? = null,
    val disclaimer: String = "⚠️ Данный анализ является предварительным и не заменяет консультацию врача."
)

data class SymptomsHistoryResponse(
    val requestId: String,
    val symptomsText: String,
    val hasResponse: Boolean,
    val createdAt: String,
    val aiResponse: AiResponseDto?
)

data class AiResponseDto(
    val diagnosis: String,
    val recommendations: String,
    val urgency: String,
    val confidence: String,
    val modelVersion: String
)

data class NotificationResponse(
    val notificationId: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val channel: String,
    val appointmentId: String?,
    val createdAt: String
)

data class SpecializationResponse(
    val specializationId: Int,
    val name: String,
    val description: String?
)

data class MessageResponse(val message: String)
data class ApiError(val code: String, val message: String)