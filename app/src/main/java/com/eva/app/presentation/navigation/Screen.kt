package com.eva.app.presentation.navigation

sealed class Screen(val route: String) {
    object Onboarding        : Screen("onboarding")
    object ConsentCheck      : Screen("consent_check")
    object Home              : Screen("home")
    object Login             : Screen("login")
    object Register          : Screen("register")
    object Consent           : Screen("consent")
    object Doctors           : Screen("doctors?specId={specId}") {
        fun createRoute(specId: Int? = null) = "doctors?specId=${specId ?: -1}"
    }
    object DoctorDetail      : Screen("doctor_detail/{doctorId}") {
        fun createRoute(id: Int) = "doctor_detail/$id"
    }
    object Booking           : Screen("booking/{doctorId}") {
        fun createRoute(id: Int) = "booking/$id"
    }
    object Clinics           : Screen("clinics")
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
}