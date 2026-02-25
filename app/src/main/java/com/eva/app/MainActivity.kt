package com.eva.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.eva.app.data.local.TokenManager
import com.eva.app.presentation.appointments.AppointmentsScreen
import com.eva.app.presentation.auth.LoginScreen
import com.eva.app.presentation.auth.RegisterScreen
import com.eva.app.presentation.booking.BookingScreen
import com.eva.app.presentation.clinic_detail.ClinicDetailScreen
import com.eva.app.presentation.clinics.ClinicsScreen
import com.eva.app.presentation.clinics.ClinicsViewModel
import com.eva.app.presentation.clinics.SpecializationsScreen
import com.eva.app.presentation.consent.AiConsentGate
import com.eva.app.presentation.consent.ConsentScreen
import com.eva.app.presentation.consent.MedicalConsentGate
import com.eva.app.presentation.doctors.DoctorDetailScreen
import com.eva.app.presentation.doctors.DoctorsScreen
import com.eva.app.presentation.home.HomeScreen
import com.eva.app.presentation.navigation.Screen
import com.eva.app.presentation.notifications.NotificationDetailScreen
import com.eva.app.presentation.notifications.NotificationsScreen
import com.eva.app.presentation.notifications.NotificationsViewModel
import com.eva.app.presentation.profile.ProfileScreen
import com.eva.app.presentation.symptoms.SymptomsFormScreen
import com.eva.app.presentation.symptoms.SymptomsResultScreen
import com.eva.app.presentation.symptoms.SymptomsScreen
import com.eva.app.presentation.symptoms.SymptomsViewModel
import com.eva.app.ui.theme.EvaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Стартовый экран определяем только по токену —
        // экран согласий показываем внутри навигации после входа
        val hasToken = runBlocking { tokenManager.token.first() } != null
        val start    = if (hasToken) Screen.Home.route else Screen.Login.route
        setContent { EvaTheme { EvaApp(startDestination = start) } }
    }
}

data class BottomItem(
    val screen: Screen,
    val icon:   androidx.compose.ui.graphics.vector.ImageVector,
    val label:  String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaApp(startDestination: String) {
    val navController = rememberNavController()
    val currentEntry  by navController.currentBackStackEntryAsState()
    val currentRoute  = currentEntry?.destination?.route

    val bottomItems = listOf(
        BottomItem(Screen.Home,         Icons.Default.Home,          "Главная"),
        BottomItem(Screen.Appointments, Icons.Default.CalendarMonth, "Записи"),
        BottomItem(Screen.Symptoms,     Icons.Default.Psychology,    "Симптомы"),
        BottomItem(Screen.Profile,      Icons.Default.Person,        "Профиль")
    )
    val bottomRoutes = bottomItems.map { it.screen.route }
    val showBottom   = currentRoute in bottomRoutes
    val topTitles    = mapOf(
        Screen.Home.route         to "EVA",
        Screen.Appointments.route to "Мои записи",
        Screen.Symptoms.route     to "AI-анализ",
        Screen.Profile.route      to "Профиль"
    )

    // SymptomsViewModel живёт на уровне Activity — Form и Result делят состояние
    val activity   = LocalContext.current as ComponentActivity
    val symptomsVm = androidx.hilt.navigation.compose.hiltViewModel<SymptomsViewModel>(activity)

    Scaffold(
        topBar = {
            if (showBottom) {
                TopAppBar(
                    title   = { Text(topTitles[currentRoute] ?: "EVA") },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                            Icon(Icons.Default.Notifications, null,
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor    = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary)
                )
            }
        },
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val isSelected = currentEntry?.destination?.hierarchy
                            ?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            icon     = { Icon(item.icon, item.label) },
                            label    = { Text(item.label) },
                            selected = isSelected,
                            onClick  = {
                                if (!isSelected) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(Screen.Home.route) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination, Modifier.padding(innerPadding)) {

            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        // После входа — сразу на Home. Согласия покажем при первом
                        // обращении к защищённым функциям (AiConsentGate / MedicalConsentGate)
                        // Но сначала проверяем: если consent ещё ни разу не показывался — ведём на него
                        navController.navigate(Screen.Consent.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = { navController.navigate(Screen.Register.route) }
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegisterSuccess = {
                        // После регистрации — логин уже выполнен в AuthViewModel,
                        // ведём на Consent
                        navController.navigate(Screen.Consent.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // Показывается один раз после входа/регистрации.
            // onDone всегда ведёт на Home — независимо от выбора пользователя.
            composable(Screen.Consent.route) {
                ConsentScreen(
                    onDone = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onDoctors         = { navController.navigate(Screen.Doctors.createRoute()) },
                    onClinics         = { navController.navigate(Screen.Clinics.route) },
                    onSpecializations = { navController.navigate(Screen.Specializations.route) },
                    onSymptoms        = { navController.navigate(Screen.SymptomsForm.route) },
                    onAppointments    = { navController.navigate(Screen.Appointments.route) }
                )
            }
            composable(Screen.Appointments.route) { AppointmentsScreen() }
            composable(Screen.Symptoms.route) {
                // История видна всем — записи фильтруются на бэке по userId токена
                SymptomsScreen(
                    onNewRequest = { navController.navigate(Screen.SymptomsForm.route) },
                    viewModel    = symptomsVm
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onOpenConsent = { navController.navigate(Screen.Consent.route) }
                )
            }

            composable(Screen.Doctors.route,
                listOf(navArgument("specId") { type = NavType.IntType; defaultValue = -1 })
            ) { entry ->
                val specId = entry.arguments?.getInt("specId")?.takeIf { it != -1 }
                DoctorsScreen(
                    initialSpecId = specId,
                    onBack        = { navController.popBackStack() },
                    onDoctorClick = { navController.navigate(Screen.DoctorDetail.createRoute(it)) }
                )
            }
            composable(Screen.DoctorDetail.route,
                listOf(navArgument("doctorId") { type = NavType.IntType })) { entry ->
                val id = entry.arguments?.getInt("doctorId") ?: return@composable
                DoctorDetailScreen(
                    doctorId = id,
                    onBack   = { navController.popBackStack() },
                    onBook   = { navController.navigate(Screen.Booking.createRoute(it)) }
                )
            }
            composable(Screen.Booking.route,
                listOf(navArgument("doctorId") { type = NavType.IntType })) { entry ->
                val id = entry.arguments?.getInt("doctorId") ?: return@composable
                // Запись на приём требует медицинского согласия
                MedicalConsentGate(
                    onRequestConsent = { navController.navigate(Screen.Consent.route) }
                ) {
                    BookingScreen(
                        doctorId  = id,
                        onBack    = { navController.popBackStack() },
                        onSuccess = {
                            navController.navigate(Screen.Appointments.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }

            composable(Screen.Clinics.route) {
                val vm = androidx.hilt.navigation.compose.hiltViewModel<ClinicsViewModel>()
                ClinicsScreen(
                    onBack        = { navController.popBackStack() },
                    onClinicClick = { navController.navigate("clinic_detail/$it") },
                    viewModel     = vm
                )
            }
            composable("clinic_detail/{clinicId}",
                listOf(navArgument("clinicId") { type = NavType.IntType })) { entry ->
                val clinicId    = entry.arguments?.getInt("clinicId") ?: return@composable
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(Screen.Clinics.route)
                }
                val clinicsVm = androidx.hilt.navigation.compose.hiltViewModel<ClinicsViewModel>(parentEntry)
                val clinic    = clinicsVm.getById(clinicId)
                if (clinic != null) {
                    ClinicDetailScreen(clinic = clinic, onBack = { navController.popBackStack() })
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable(Screen.Specializations.route) {
                SpecializationsScreen(
                    onBack      = { navController.popBackStack() },
                    onSpecClick = { navController.navigate(Screen.Doctors.createRoute(it)) }
                )
            }

            composable(Screen.SymptomsForm.route) {
                // AI-анализ требует отдельного согласия
                AiConsentGate(
                    onRequestConsent = { navController.navigate(Screen.Consent.route) }
                ) {
                    SymptomsFormScreen(
                        onBack    = { navController.popBackStack() },
                        onResult  = { navController.navigate(Screen.SymptomsResult.route) },
                        viewModel = symptomsVm
                    )
                }
            }
            composable(Screen.SymptomsResult.route) {
                SymptomsResultScreen(
                    onBack    = {
                        navController.popBackStack(Screen.SymptomsForm.route, inclusive = true)
                    },
                    viewModel = symptomsVm
                )
            }

            composable(Screen.Notifications.route) {
                val notifVm = androidx.hilt.navigation.compose.hiltViewModel<NotificationsViewModel>()
                NotificationsScreen(
                    onBack       = { navController.popBackStack() },
                    onNotifClick = { navController.navigate(Screen.NotificationDetail.createRoute(it)) },
                    viewModel    = notifVm
                )
            }
            composable(Screen.NotificationDetail.route,
                listOf(navArgument("notifId") { type = NavType.StringType })) { entry ->
                val notifId     = entry.arguments?.getString("notifId") ?: return@composable
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(Screen.Notifications.route)
                }
                val notifVm = androidx.hilt.navigation.compose.hiltViewModel<NotificationsViewModel>(parentEntry)
                NotificationDetailScreen(
                    notifId   = notifId,
                    onBack    = { navController.popBackStack() },
                    viewModel = notifVm
                )
            }
        }
    }
}