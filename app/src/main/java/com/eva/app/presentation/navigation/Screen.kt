package com.eva.app.presentation.navigation

sealed class Screen(val route: String) {
    object Splash            : Screen("splash")
    object Onboarding        : Screen("onboarding")
    object ConsentCheck      : Screen("consent_check")
    object Home              : Screen("home")
    object Login             : Screen("login")
    object Register          : Screen("register")
    object Consent           : Screen("consent")
    object Doctors           : Screen("doctors?specId={specId}&clinicId={clinicId}") {
        fun createRoute(specId: Int? = null, clinicId: Int? = null) =
            "doctors?specId=${specId ?: -1}&clinicId=${clinicId ?: -1}"
    }
    object DoctorDetail      : Screen("doctor_detail/{doctorId}") {
        fun createRoute(id: Int) = "doctor_detail/$id"
    }
    object Booking           : Screen("booking/{doctorId}") {
        fun createRoute(id: Int) = "booking/$id"
    }
    object Clinics           : Screen("clinics")
    object ClinicDetail      : Screen("clinic_detail/{clinicId}") {
        fun createRoute(id: Int) = "clinic_detail/$id"
    }
    object Specializations   : Screen("specializations")
    object Appointments      : Screen("appointments")
    object Symptoms          : Screen("symptoms")
    object SymptomsForm      : Screen("symptoms_form")
    object SymptomsResult    : Screen("symptoms_result")
    object Notifications     : Screen("notifications")
    object NotificationDetail: Screen("notification_detail/{notifId}") {
        fun createRoute(id: String) = "notification_detail/$id"
    }
    object Profile           : Screen("profile")
    object MedicalCard       : Screen("medical_card")
    object Settings          : Screen("settings")
    object EditProfile       : Screen("edit_profile")
    object ForgotPassword    : Screen("forgot_password")
    object ResetPassword     : Screen("reset_password/{token}") {
        fun createRoute(token: String) = "reset_password/$token"
    }
}