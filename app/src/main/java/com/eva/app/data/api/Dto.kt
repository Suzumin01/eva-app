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
    val userId: String,
    val fullName: String,
    val role: String
)

data class UserProfileResponse(
    val userId: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val role: String,
    val isActive: Boolean,
    val consentMedical: Boolean,
    val consentAi: Boolean
)

data class UpdateProfileRequest(
    val fullName: String?,
    val phone: String?
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
    val userFullName: String,
    val rating: Int,
    val comment: String?,
    val createdAt: String
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
    val longitude: String?
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
    val createdAt: String
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
    val disclaimer: String
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

data class MessageResponse(val message: String)
data class ApiError(val code: String, val message: String)