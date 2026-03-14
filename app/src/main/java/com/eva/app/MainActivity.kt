package com.eva.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
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
import com.eva.app.presentation.medical_card.MedicalCardScreen
import com.eva.app.presentation.navigation.Screen
import com.eva.app.presentation.notifications.NotificationDetailScreen
import com.eva.app.presentation.notifications.NotificationsScreen
import com.eva.app.presentation.notifications.NotificationsViewModel
import com.eva.app.presentation.onboarding.OnboardingScreen
import com.eva.app.presentation.profile.ProfileScreen
import com.eva.app.presentation.settings.EditProfileScreen
import com.eva.app.presentation.settings.SettingsScreen
import com.eva.app.presentation.symptoms.SymptomsFormScreen
import com.eva.app.presentation.symptoms.SymptomsResultScreen
import com.eva.app.presentation.symptoms.SymptomsScreen
import com.eva.app.presentation.symptoms.SymptomsViewModel
import com.eva.app.ui.theme.EvaTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var authRepository: AuthRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val pendingNotifId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Читаем notifId из пуша (если запустились через клик на уведомление)
        pendingNotifId.value = intent.getStringExtra("notifId")
        val hasToken       = runBlocking { tokenManager.token.first() } != null
        val onboardingDone = runBlocking { tokenManager.onboardingDone.first() }
        val consentShown   = runBlocking { tokenManager.consentShown.first() }

        val start = when {
            !onboardingDone -> Screen.Onboarding.route
            !hasToken       -> Screen.Login.route
            !consentShown   -> Screen.Consent.route
            else            -> Screen.Home.route
        }

        // Регистрируем FCM-токен если пользователь авторизован
        if (hasToken) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
                lifecycleScope.launch(Dispatchers.IO) {
                    tokenManager.saveFcmToken(fcmToken)
                    authRepository.saveFcmToken(fcmToken)
                }
            }
        }

        setContent {
            val darkTheme by tokenManager.darkTheme.collectAsState(initial = false)
            EvaTheme(darkTheme = darkTheme) {
                EvaApp(
                    startDestination = start,
                    pendingNotifId   = pendingNotifId.value,
                    onNotifConsumed  = { pendingNotifId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotifId.value = intent.getStringExtra("notifId")
    }
}

@HiltViewModel
class ConsentCheckViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {
    val consentShown = tokenManager.consentShown
}

data class BottomItem(
    val screen: Screen,
    val icon:   androidx.compose.ui.graphics.vector.ImageVector,
    val label:  String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaApp(
    startDestination: String,
    pendingNotifId: String?  = null,
    onNotifConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentEntry  by navController.currentBackStackEntryAsState()
    val currentRoute  = currentEntry?.destination?.route

    LaunchedEffect(pendingNotifId) {
        val notifId = pendingNotifId ?: return@LaunchedEffect
        // Сначала кладём Notifications в стек, затем открываем Detail
        navController.navigate(Screen.Notifications.route) { launchSingleTop = true }
        navController.navigate(Screen.NotificationDetail.createRoute(notifId)) { launchSingleTop = true }
        onNotifConsumed()
    }

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

            composable(Screen.Onboarding.route) {
                OnboardingScreen(onDone = {
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                })
            }

            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        // Промежуточный роут проверит consentShown из DataStore
                        navController.navigate(Screen.ConsentCheck.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = { navController.navigate(Screen.Register.route) }
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(Screen.Consent.route) { popUpTo(0) { inclusive = true } }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ConsentCheck.route) {
                val vm = androidx.hilt.navigation.compose.hiltViewModel<ConsentCheckViewModel>()
                val consentShown by vm.consentShown.collectAsState(initial = null)
                LaunchedEffect(consentShown) {
                    val shown = consentShown ?: return@LaunchedEffect
                    val dest  = if (shown) Screen.Home.route else Screen.Consent.route
                    navController.navigate(dest) { popUpTo(0) { inclusive = true } }
                }
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                }
            }

            composable(Screen.Consent.route) {
                ConsentScreen(onDone = {
                    navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true } }
                })
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onDoctors         = { navController.navigate(Screen.Doctors.createRoute()) },
                    onClinics         = { navController.navigate(Screen.Clinics.route) },
                    onSpecializations = { navController.navigate(Screen.Specializations.route) },
                    onSymptoms        = { navController.navigate(Screen.SymptomsForm.route) },
                    onAppointments    = { navController.navigate(Screen.Appointments.route) },
                    onDoctorClick     = { navController.navigate(Screen.DoctorDetail.createRoute(it)) }
                )
            }
            composable(Screen.Appointments.route) { AppointmentsScreen() }
            composable(Screen.Symptoms.route) {
                SymptomsScreen(
                    onNewRequest = { navController.navigate(Screen.SymptomsForm.route) },
                    viewModel    = symptomsVm
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                    },
                    onOpenMedicalCard = { navController.navigate(Screen.MedicalCard.route) },
                    onOpenSettings    = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.MedicalCard.route) {
                MedicalCardScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack         = { navController.popBackStack() },
                    onEditProfile  = { navController.navigate(Screen.EditProfile.route) },
                    onEditConsents = { navController.navigate(Screen.Consent.route) }
                )
            }
            composable(Screen.EditProfile.route) {
                EditProfileScreen(
                    onBack  = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(Screen.Doctors.route,
                listOf(navArgument("specId") { type = NavType.IntType; defaultValue = -1 })
            ) { entry ->
                val specId = entry.arguments?.getInt("specId")?.takeIf { it != -1 }
                DoctorsScreen(initialSpecId = specId,
                    onBack    = { navController.popBackStack() },
                    onDoctor  = { navController.navigate(Screen.DoctorDetail.createRoute(it)) })
            }
            composable(Screen.DoctorDetail.route,
                listOf(navArgument("doctorId") { type = NavType.IntType })) { entry ->
                val id = entry.arguments?.getInt("doctorId") ?: return@composable
                DoctorDetailScreen(doctorId = id,
                    onBack = { navController.popBackStack() },
                    onBook = { navController.navigate(Screen.Booking.createRoute(it)) })
            }
            composable(Screen.Booking.route,
                listOf(navArgument("doctorId") { type = NavType.IntType })) { entry ->
                val id = entry.arguments?.getInt("doctorId") ?: return@composable
                MedicalConsentGate(onRequestConsent = { navController.navigate(Screen.Consent.route) }) {
                    BookingScreen(doctorId = id,
                        onBack    = { navController.popBackStack() },
                        onSuccess = { navController.navigate(Screen.Appointments.route) { popUpTo(0) { inclusive = true } } })
                }
            }

            composable(Screen.Clinics.route) {
                val vm = androidx.hilt.navigation.compose.hiltViewModel<ClinicsViewModel>()
                ClinicsScreen(onBack = { navController.popBackStack() },
                    onClinicClick = { navController.navigate(Screen.ClinicDetail.createRoute(it)) }, viewModel = vm)
            }
            composable(Screen.ClinicDetail.route,
                listOf(navArgument("clinicId") { type = NavType.IntType })) { entry ->
                val clinicId    = entry.arguments?.getInt("clinicId") ?: return@composable
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Clinics.route) }
                val clinicsVm   = androidx.hilt.navigation.compose.hiltViewModel<ClinicsViewModel>(parentEntry)
                val clinic      = clinicsVm.getById(clinicId)
                if (clinic != null) ClinicDetailScreen(clinic = clinic, onBack = { navController.popBackStack() })
                else LaunchedEffect(Unit) { navController.popBackStack() }
            }
            composable(Screen.Specializations.route) {
                SpecializationsScreen(onBack = { navController.popBackStack() },
                    onSpecClick = { navController.navigate(Screen.Doctors.createRoute(it)) })
            }

            composable(Screen.SymptomsForm.route) {
                AiConsentGate(onRequestConsent = { navController.navigate(Screen.Consent.route) }) {
                    SymptomsFormScreen(onBack = { navController.popBackStack() },
                        onResult = { navController.navigate(Screen.SymptomsResult.route) },
                        viewModel = symptomsVm)
                }
            }
            composable(Screen.SymptomsResult.route) {
                SymptomsResultScreen(
                    onBack       = { navController.popBackStack(Screen.SymptomsForm.route, inclusive = true) },
                    onFindDoctor = { specName ->
                        // Маппинг названия специализации из AI → id фильтра врачей
                        val specId = com.eva.app.util.Specializations.findIdByName(specName)
                        navController.navigate(Screen.Doctors.createRoute(specId = specId)) {
                            launchSingleTop = true
                        }
                    },
                    viewModel = symptomsVm)
            }

            composable(Screen.Notifications.route) {
                val notifVm = androidx.hilt.navigation.compose.hiltViewModel<NotificationsViewModel>()
                NotificationsScreen(onBack = { navController.popBackStack() },
                    onNotifClick = { navController.navigate(Screen.NotificationDetail.createRoute(it)) },
                    viewModel = notifVm)
            }
            composable(Screen.NotificationDetail.route,
                listOf(navArgument("notifId") { type = NavType.StringType })) { entry ->
                val notifId     = entry.arguments?.getString("notifId") ?: return@composable
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Notifications.route) }
                val notifVm     = androidx.hilt.navigation.compose.hiltViewModel<NotificationsViewModel>(parentEntry)
                NotificationDetailScreen(notifId = notifId,
                    onBack = { navController.popBackStack() }, viewModel = notifVm)
            }
        }
    }
}