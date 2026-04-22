package com.eva.app.presentation.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.local.TokenManager
import com.eva.app.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _startDest = MutableStateFlow<String?>(null)
    val startDest: StateFlow<String?> = _startDest.asStateFlow()

    init {
        viewModelScope.launch {
            val hasToken       = tokenManager.token.first() != null
            val onboardingDone = tokenManager.onboardingDone.first()
            val consentShown   = tokenManager.consentShown.first()

            _startDest.value = when {
                !onboardingDone -> Screen.Onboarding.route
                !hasToken       -> Screen.Login.route
                !consentShown   -> Screen.Consent.route
                else            -> Screen.Home.route
            }
        }
    }
}

@Composable
fun SplashScreen(
    onDestinationReady: (String) -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val startDest by viewModel.startDest.collectAsState()

    LaunchedEffect(startDest) {
        startDest?.let { onDestinationReady(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "ЕВА",
                fontSize   = 52.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
            Text(
                text  = "E-Health Virtual Assistant",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
    }
}
