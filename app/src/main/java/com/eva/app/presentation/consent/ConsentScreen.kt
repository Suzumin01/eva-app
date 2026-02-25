package com.eva.app.presentation.consent

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
class ConsentViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {
    val consentMedical = tokenManager.consentMedical
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val consentAi      = tokenManager.consentAi
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val consentPrivacy = tokenManager.consentPrivacy
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun save(medical: Boolean, ai: Boolean, privacy: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            tokenManager.saveConsents(medical, ai, privacy)
            onDone()
        }
    }
}

@Composable
fun ConsentScreen(
    onDone: () -> Unit,
    viewModel: ConsentViewModel = hiltViewModel()
) {
    val savedMedical by viewModel.consentMedical.collectAsState()
    val savedAi      by viewModel.consentAi.collectAsState()
    val savedPrivacy by viewModel.consentPrivacy.collectAsState()

    var medicalChecked by remember(savedMedical) { mutableStateOf(savedMedical) }
    var aiChecked      by remember(savedAi)      { mutableStateOf(savedAi) }
    var privacyChecked by remember(savedPrivacy) { mutableStateOf(savedPrivacy) }

    val canProceed = medicalChecked && privacyChecked

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF1976D2))))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(80.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PrivacyTip, null, tint = Color.White,
                        modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Соглашения", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Пожалуйста, ознакомьтесь с условиями перед использованием EVA",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {

                    ConsentItem(
                        icon = Icons.Default.MedicalServices,
                        title = "Обработка медицинских данных *",
                        description = "Сбор и обработка медицинских данных для оказания услуг согласно ФЗ № 323-ФЗ.",
                        checked  = medicalChecked,
                        onChecked = { medicalChecked = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    ConsentItem(
                        icon = Icons.Default.Shield,
                        title = "Политика конфиденциальности *",
                        description = "Обработка персональных данных согласно ФЗ № 152-ФЗ «О персональных данных».",
                        checked  = privacyChecked,
                        onChecked = { privacyChecked = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    ConsentItem(
                        icon = Icons.Default.AutoAwesome,
                        title = "AI-анализ симптомов (необязательно)",
                        description = "Обработка описаний симптомов искусственным интеллектом. Можно принять позже при использовании функции.",
                        checked  = aiChecked,
                        onChecked = { aiChecked = it }
                    )

                    Spacer(Modifier.height(6.dp))
                    Text("* Обязательные пункты",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick  = { viewModel.save(medicalChecked, aiChecked, privacyChecked) { onDone() } },
                        enabled  = canProceed,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Продолжить", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick  = { viewModel.save(false, false, false) { onDone() } },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Text("Пропустить — решу позже")
                    }

                    if (!canProceed) {
                        Spacer(Modifier.height(8.dp))
                        Text("Для полного доступа необходимо принять обязательные пункты",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ConsentItem(
    icon: ImageVector, title: String, description: String,
    checked: Boolean, onChecked: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)) {
                Checkbox(checked = checked, onCheckedChange = onChecked)
                Text("Принимаю", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
fun MedicalConsentGate(
    viewModel: ConsentViewModel = hiltViewModel(),
    onRequestConsent: () -> Unit,
    content: @Composable () -> Unit
) {
    val accepted by viewModel.consentMedical.collectAsState()
    ConsentGateContent(
        accepted = accepted,
        title = "Требуется согласие",
        text  = "Для записи на приём необходимо принять согласие на обработку медицинских данных.",
        onRequest = onRequestConsent,
        content = content
    )
}

@Composable
fun AiConsentGate(
    viewModel: ConsentViewModel = hiltViewModel(),
    onRequestConsent: () -> Unit,
    content: @Composable () -> Unit
) {
    val accepted by viewModel.consentAi.collectAsState()
    ConsentGateContent(
        accepted = accepted,
        title = "Требуется согласие на AI-анализ",
        text  = "Для использования AI-анализа симптомов необходимо принять согласие на обработку данных AI-системой.",
        onRequest = onRequestConsent,
        content = content
    )
}

@Composable
private fun ConsentGateContent(
    accepted: Boolean, title: String, text: String,
    onRequest: () -> Unit, content: @Composable () -> Unit
) {
    if (accepted) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(modifier = Modifier.fillMaxWidth().padding(32.dp),
                shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(title, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRequest, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Принять согласие")
                    }
                }
            }
        }
    }
}