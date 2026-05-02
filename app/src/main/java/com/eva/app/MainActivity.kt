package com.eva.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import com.eva.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.eva.app.presentation.appointments.AppointmentsScreen
import com.eva.app.presentation.auth.ForgotPasswordScreen
import com.eva.app.presentation.auth.LoginScreen
import com.eva.app.presentation.auth.RegisterScreen
import com.eva.app.presentation.auth.ResetPasswordScreen
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
import com.eva.app.presentation.notifications.NotificationsScreen
import com.eva.app.presentation.notifications.NotificationsViewModel
import com.eva.app.presentation.onboarding.OnboardingScreen
import com.eva.app.presentation.profile.ProfileScreen
import com.eva.app.presentation.settings.EditProfileScreen
import com.eva.app.presentation.settings.SettingsScreen
import com.eva.app.presentation.splash.SplashScreen
import com.eva.app.presentation.symptoms.SymptomsFormScreen
import com.eva.app.presentation.symptoms.SymptomsResultScreen
import com.eva.app.presentation.symptoms.SymptomsScreen
import com.eva.app.presentation.symptoms.SymptomsViewModel
import com.eva.app.ui.theme.EvaTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var specializationRepository: com.eva.app.data.repository.SpecializationRepository

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

        // Регистрируем FCM-токен если пользователь авторизован — не блокируем main thread
        lifecycleScope.launch(Dispatchers.IO) {
            val hasToken = tokenManager.token.first() != null
            if (hasToken) {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        tokenManager.saveFcmToken(fcmToken)
                        authRepository.saveFcmToken(fcmToken)
                    }
                }
            }
        }

        setContent {
            val darkTheme by tokenManager.darkTheme.collectAsState(initial = false)
            EvaTheme(darkTheme = darkTheme) {
                EvaApp(
                    startDestination = Screen.Splash.route,
                    tokenManager     = tokenManager,
                    darkTheme        = darkTheme,
                    pendingNotifId   = pendingNotifId.value,
                    onNotifConsumed  = { pendingNotifId.value = null },
                    findSpecId       = specializationRepository::findIdByName
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
    val screen:       Screen,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val defaultIcon:  androidx.compose.ui.graphics.vector.ImageVector,
    val label:        String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaApp(
    startDestination: String,
    tokenManager: TokenManager,
    darkTheme: Boolean = false,
    pendingNotifId: String?  = null,
    onNotifConsumed: () -> Unit = {},
    findSpecId: (String) -> Int? = { com.eva.app.util.Specializations.findIdByName(it) }
) {
    val navController = rememberNavController()
    val currentEntry  by navController.currentBackStackEntryAsState()
    val currentRoute  = currentEntry?.destination?.route

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val activity   = LocalContext.current as ComponentActivity
    val symptomsVm = androidx.hilt.navigation.compose.hiltViewModel<SymptomsViewModel>(activity)
    val notifVm    = androidx.hilt.navigation.compose.hiltViewModel<NotificationsViewModel>(activity)
    val notifications by notifVm.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }

    // Разлогин при истечении JWT — сервер вернул 401, интерсептор вызвал emitUnauthorized.
    // withContext(Main) гарантирует выполнение навигации на main thread даже если
    // emitUnauthorized() вызван из OkHttp-треда (иначе LifecycleRegistry падает).
    LaunchedEffect(Unit) {
        tokenManager.unauthorizedEvent.collect {
            withContext(Dispatchers.Main) {
                navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
            }
        }
    }

    LaunchedEffect(pendingNotifId) {
        val notifId = pendingNotifId ?: return@LaunchedEffect
        notifVm.load()
        navController.navigate(Screen.Notifications.createRoute(notifId)) { launchSingleTop = true }
        onNotifConsumed()
    }

    val bottomItems = listOf(
        BottomItem(Screen.Home,         Icons.Default.Home,          Icons.Outlined.Home,          stringResource(R.string.nav_home)),
        BottomItem(Screen.Appointments, Icons.Default.CalendarMonth, Icons.Outlined.CalendarMonth, stringResource(R.string.nav_appointments)),
        BottomItem(Screen.Symptoms,     Icons.Default.Psychology,    Icons.Outlined.Psychology,    stringResource(R.string.nav_symptoms)),
        BottomItem(Screen.Profile,      Icons.Default.Person,        Icons.Outlined.Person,        stringResource(R.string.nav_profile))
    )
    val bottomRoutes = bottomItems.map { it.screen.route }
    val showBottom   = currentRoute in bottomRoutes
    val topTitles    = mapOf(
        Screen.Home.route         to stringResource(R.string.app_name),
        Screen.Appointments.route to stringResource(R.string.topbar_appointments),
        Screen.Symptoms.route     to stringResource(R.string.topbar_symptoms),
        Screen.Profile.route      to stringResource(R.string.nav_profile)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val isSelected = currentEntry?.destination?.hierarchy
                            ?.any { it.route == item.screen.route } == true
                        val iconScale by animateFloatAsState(
                            targetValue   = if (isSelected) 1.15f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessMedium
                            ),
                            label = "navScale_${item.screen.route}"
                        )
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (isSelected) item.selectedIcon else item.defaultIcon,
                                    item.label,
                                    modifier = Modifier.scale(iconScale)
                                )
                            },
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
        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            enterTransition     = { fadeIn(tween(300)) },
            exitTransition      = { fadeOut(tween(200)) },
            popEnterTransition  = { fadeIn(tween(300)) },
            popExitTransition   = { fadeOut(tween(200)) }
        ) {

            composable(Screen.Splash.route) {
                SplashScreen(onDestinationReady = { dest ->
                    navController.navigate(dest) { popUpTo(Screen.Splash.route) { inclusive = true } }
                })
            }

            composable(Screen.Onboarding.route) {
                OnboardingScreen(onDone = {
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                })
            }

            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.ConsentCheck.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                    onForgotPassword = { navController.navigate(Screen.ForgotPassword.route) }
                )
            }
            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(
                    onBack = { navController.popBackStack() },
                    onTokenReceived = { token ->
                        navController.navigate(Screen.ResetPassword.createRoute(token))
                    }
                )
            }
            composable(Screen.ResetPassword.route) { backStack ->
                val token = backStack.arguments?.getString("token") ?: ""
                ResetPasswordScreen(
                    token = token,
                    onSuccess = {
                        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                    },
                    onBack = { navController.popBackStack() }
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
                    onNotifications   = { notifVm.load(); navController.navigate(Screen.Notifications.createRoute()) },
                    unreadCount       = unreadCount,
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
                listOf(
                    navArgument("specId")   { type = NavType.IntType; defaultValue = -1 },
                    navArgument("clinicId") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { entry ->
                val specId   = entry.arguments?.getInt("specId")?.takeIf   { it != -1 }
                val clinicId = entry.arguments?.getInt("clinicId")?.takeIf { it != -1 }
                DoctorsScreen(
                    initialSpecId     = specId,
                    initialClinicId   = clinicId,
                    initialClinicName = null,
                    onBack   = { navController.popBackStack() },
                    onDoctor = { navController.navigate(Screen.DoctorDetail.createRoute(it)) }
                )
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
                if (clinic != null) ClinicDetailScreen(
                    clinic = clinic,
                    onBack = { navController.popBackStack() },
                    onFindDoctors = { id, _ ->
                        navController.navigate(Screen.Doctors.createRoute(clinicId = id))
                    }
                )
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
                        val specId = findSpecId(specName)
                        navController.navigate(Screen.Doctors.createRoute(specId = specId)) {
                            launchSingleTop = true
                        }
                    },
                    viewModel = symptomsVm)
            }

            composable(
                Screen.Notifications.route,
                listOf(navArgument("notifId") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                val notifId = entry.arguments?.getString("notifId")?.takeIf { it.isNotEmpty() }
                NotificationsScreen(
                    onBack          = { navController.popBackStack() },
                    autoOpenNotifId = notifId,
                    viewModel       = notifVm
                )
            }
        }
    }
}