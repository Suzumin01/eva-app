package com.eva.app.presentation.health

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.AuthRepository
import com.eva.app.presentation.components.EvaType
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthSetupViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _done    = MutableStateFlow(false)
    val done = _done.asStateFlow()

    private val _saving  = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    fun checkAlreadyDone(onDone: () -> Unit) {
        viewModelScope.launch {
            if (tokenManager.healthSetupDone.first()) onDone()
        }
    }

    fun save(allergies: String, chronic: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _saving.value = true
            authRepository.updateProfile(
                fullName        = null,
                phone           = null,
                allergies       = allergies.trim().ifBlank { null },
                chronicDiseases = chronic.trim().ifBlank { null }
            )
            tokenManager.setHealthSetupDone()
            _saving.value = false
            onDone()
        }
    }

    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            tokenManager.setHealthSetupDone()
            onDone()
        }
    }
}

@Composable
fun HealthSetupScreen(
    onDone: () -> Unit,
    viewModel: HealthSetupViewModel = hiltViewModel()
) {
    val saving by viewModel.saving.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkAlreadyDone(onDone) }

    var allergies by remember { mutableStateOf("") }
    var chronic   by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Box(
            modifier         = Modifier
                .size(80.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.HealthAndSafety,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint     = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.health_setup_title),
            style     = EvaType.screenTitle,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.health_setup_subtitle),
            style     = EvaType.bodyText,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value         = allergies,
            onValueChange = { allergies = it },
            label         = { Text(stringResource(R.string.edit_profile_allergies_label)) },
            leadingIcon   = { Icon(Icons.Default.Warning, null, Modifier.size(18.dp)) },
            placeholder   = { Text(stringResource(R.string.edit_profile_allergies_placeholder)) },
            supportingText = { Text(stringResource(R.string.health_setup_optional_hint)) },
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            minLines = 2, maxLines = 4
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value         = chronic,
            onValueChange = { chronic = it },
            label         = { Text(stringResource(R.string.edit_profile_chronic_label)) },
            leadingIcon   = { Icon(Icons.Default.MonitorHeart, null, Modifier.size(18.dp)) },
            placeholder   = { Text(stringResource(R.string.edit_profile_chronic_placeholder)) },
            supportingText = { Text(stringResource(R.string.health_setup_optional_hint)) },
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            minLines = 2, maxLines = 4
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick  = { viewModel.save(allergies, chronic, onDone) },
            enabled  = !saving && (allergies.isNotBlank() || chronic.isNotBlank()),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(50)
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = Color.White)
            } else {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.health_setup_save_btn), fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick  = { viewModel.skip(onDone) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.health_setup_skip_btn),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(24.dp))
    }
}
