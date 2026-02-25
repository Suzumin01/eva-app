package com.eva.app.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.api.UserProfileResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.AuthRepository
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _profile   = MutableStateFlow<UserProfileResponse?>(null)
    val profile = _profile.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _loggedOut = MutableStateFlow(false)
    val loggedOut = _loggedOut.asStateFlow()

    // Согласия берём из локального хранилища — единый источник правды
    val consentMedical = tokenManager.consentMedical
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val consentAi      = tokenManager.consentAi
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val consentPrivacy = tokenManager.consentPrivacy
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = authRepository.getMe()) {
                is Resource.Success -> _profile.value = r.data
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _loggedOut.value = true
        }
    }
}

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenConsent: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profile        by viewModel.profile.collectAsState()
    val isLoading      by viewModel.isLoading.collectAsState()
    val loggedOut      by viewModel.loggedOut.collectAsState()
    val consentMedical by viewModel.consentMedical.collectAsState()
    val consentAi      by viewModel.consentAi.collectAsState()
    val consentPrivacy by viewModel.consentPrivacy.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(loggedOut) { if (loggedOut) onLogout() }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            text  = { Text("Вы будете перенаправлены на экран входа.") },
            confirmButton = {
                Button(onClick = { showLogoutDialog = false; viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Выйти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Аватар
        Surface(shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            profile?.let { p ->
                Text(p.fullName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(p.email, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // роль убрана
            }
        }

        Spacer(Modifier.height(24.dp))

        // Личные данные
        profile?.let { p ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Личные данные", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider()
                    ProfileRow(Icons.Default.Person,  "Имя",      p.fullName)
                    ProfileRow(Icons.Default.Email,   "Email",    p.email)
                    p.phone?.let { ProfileRow(Icons.Default.Phone, "Телефон", it) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Блок согласий — из локального TokenManager
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Согласия", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = onOpenConsent) {
                        Text("Изменить", style = MaterialTheme.typography.labelMedium)
                    }
                }
                HorizontalDivider()
                ConsentRow(Icons.Default.MedicalServices,
                    "Обработка медицинских данных", consentMedical)
                ConsentRow(Icons.Default.Shield,
                    "Политика конфиденциальности",  consentPrivacy)
                ConsentRow(Icons.Default.AutoAwesome,
                    "AI-анализ симптомов",           consentAi)
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick  = { showLogoutDialog = true },
            colors   = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Выйти из аккаунта")
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ProfileRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ConsentRow(icon: ImageVector, label: String, granted: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, modifier = Modifier.size(16.dp),
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            null,
            tint = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
    }
}