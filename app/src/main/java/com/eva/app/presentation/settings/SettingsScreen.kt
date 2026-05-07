package com.eva.app.presentation.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.local.TokenManager
import com.eva.app.presentation.components.EvaType
import com.eva.app.presentation.components.SectionHeader
import com.eva.app.presentation.components.evaPurple
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
                title = {
                    Text(stringResource(R.string.profile_menu_settings),
                        style = EvaType.cardTitle)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                windowInsets = WindowInsets(0),
                colors       = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsSection(stringResource(R.string.settings_section_personal)) {
                SettingsNavRow(
                    icon     = Icons.Default.Person,
                    iconTint = MaterialTheme.colorScheme.evaPurple,
                    title    = stringResource(R.string.settings_edit_profile),
                    subtitle = stringResource(R.string.settings_edit_profile_sub),
                    onClick  = onEditProfile
                )
            }

            SettingsSection(stringResource(R.string.settings_section_privacy)) {
                ConsentRow(
                    icon    = Icons.Default.MedicalServices,
                    label   = stringResource(R.string.settings_consent_medical),
                    granted = consentMedical
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                ConsentRow(
                    icon    = Icons.Default.Shield,
                    label   = stringResource(R.string.settings_consent_privacy),
                    granted = consentPrivacy
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                ConsentRow(
                    icon    = Icons.Default.AutoAwesome,
                    label   = stringResource(R.string.settings_consent_ai),
                    granted = consentAi
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                TextButton(
                    onClick  = onEditConsents,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_change_consents))
                }
            }

            SettingsSection(stringResource(R.string.settings_section_appearance)) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            null,
                            tint     = if (darkTheme) Color(0xFF5C6BC0) else Color(0xFFFFA726),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(stringResource(R.string.settings_dark_theme),
                                style = EvaType.cardTitle)
                            Text(
                                if (darkTheme) stringResource(R.string.settings_dark_theme_on)
                                else stringResource(R.string.settings_dark_theme_off),
                                style = EvaType.cardMeta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(checked = darkTheme, onCheckedChange = { viewModel.setDarkTheme(it) })
                }
            }

            SettingsSection(stringResource(R.string.settings_section_about)) {
                AboutRow(
                    icon  = Icons.Default.Info,
                    label = stringResource(R.string.settings_about_version),
                    value = stringResource(R.string.settings_about_version_value)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                AboutRow(
                    icon  = Icons.Default.Code,
                    label = stringResource(R.string.settings_about_developer),
                    value = stringResource(R.string.settings_about_developer_value)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                AboutRow(
                    icon  = Icons.Default.Description,
                    label = stringResource(R.string.settings_about_license),
                    value = stringResource(R.string.settings_about_license_value)
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(title)
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title,
                    style = EvaType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle,
                    style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConsentRow(icon: ImageVector, label: String, granted: Boolean) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.weight(1f)
        ) {
            Icon(icon, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = EvaType.bodyText)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            null,
            tint     = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label,
            style    = EvaType.bodyText,
            modifier = Modifier.weight(1f))
        Text(value,
            style = EvaType.cardMeta,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
