package com.eva.app.presentation.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {
    val consentMedical = tokenManager.consentMedical.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val consentAi      = tokenManager.consentAi.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val consentPrivacy = tokenManager.consentPrivacy.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val darkTheme      = tokenManager.darkTheme.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { tokenManager.setDarkTheme(enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onEditConsents: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val consentMedical by viewModel.consentMedical.collectAsState()
    val consentAi      by viewModel.consentAi.collectAsState()
    val consentPrivacy by viewModel.consentPrivacy.collectAsState()
    val darkTheme      by viewModel.darkTheme.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            SettingsSection(title = "Личные данные") {
                SettingsNavRow(
                    icon  = Icons.Default.Person,
                    label = "Редактировать профиль",
                    subtitle = "Имя, телефон, дата рождения, здоровье",
                    onClick = onEditProfile
                )
            }

            SettingsSection(title = "Конфиденциальность") {
                ConsentSettingsRow(Icons.Default.MedicalServices,
                    "Обработка медицинских данных", consentMedical)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ConsentSettingsRow(Icons.Default.Shield,
                    "Политика конфиденциальности", consentPrivacy)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ConsentSettingsRow(Icons.Default.AutoAwesome,
                    "AI-анализ симптомов", consentAi)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onEditConsents,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Изменить согласия")
                }
            }

            SettingsSection(title = "Внешний вид") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    null, tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Тёмная тема", fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(if (darkTheme) "Включена" else "Выключена",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = { viewModel.setDarkTheme(it) }
                    )
                }
            }

            SettingsSection(title = "О приложении") {
                SettingsInfoRow(Icons.Default.Info,      "Версия",    "1.0.0")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsInfoRow(Icons.Default.Code,      "Разработчик", "ЕВА Team")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsInfoRow(Icons.Default.Description,"Лицензия",  "MIT")
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(14.dp), content = content)
        }
    }
}

@Composable
fun SettingsNavRow(
    icon: ImageVector, label: String,
    subtitle: String? = null, onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConsentSettingsRow(icon: ImageVector, label: String, granted: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, modifier = Modifier.size(15.dp),
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel, null,
            tint = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingsInfoRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium)
    }
}