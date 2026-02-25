package com.eva.app.presentation.medical_card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.api.AppointmentResponse
import com.eva.app.data.api.SymptomsHistoryResponse
import com.eva.app.data.repository.AppointmentRepository
import com.eva.app.data.repository.SymptomsRepository
import com.eva.app.util.Resource
import com.eva.app.util.formatDate
import com.eva.app.util.formatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MedicalCardViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val symptomsRepository: SymptomsRepository
) : ViewModel() {
    private val _appointments = MutableStateFlow<List<AppointmentResponse>>(emptyList())
    val appointments = _appointments.asStateFlow()

    private val _symptomsHistory = MutableStateFlow<List<SymptomsHistoryResponse>>(emptyList())
    val symptomsHistory = _symptomsHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = appointmentRepository.getMyAppointments()) {
                is Resource.Success -> _appointments.value =
                    r.data.filter { it.status == "completed" }
                        .sortedByDescending { it.slotDate + it.slotTime }
                else -> {}
            }
            when (val r = symptomsRepository.getHistory()) {
                is Resource.Success -> _symptomsHistory.value = r.data
                else -> {}
            }
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalCardScreen(
    onBack: () -> Unit,
    viewModel: MedicalCardViewModel = hiltViewModel()
) {
    val appointments    by viewModel.appointments.collectAsState()
    val symptomsHistory by viewModel.symptomsHistory.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()

    var tab by remember { mutableStateOf(0) }
    var selectedAppointment by remember { mutableStateOf<AppointmentResponse?>(null) }
    var selectedSymptom     by remember { mutableStateOf<SymptomsHistoryResponse?>(null) }

    selectedAppointment?.let { a ->
        AlertDialog(
            onDismissRequest = { selectedAppointment = null },
            icon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MedicalServices, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp))
                    }
                }
            },
            title = { Text(a.doctorName, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MedCardDetailRow(Icons.Default.LocalHospital,
                        "Специализация", a.specializationName)
                    MedCardDetailRow(Icons.Default.Business,
                        "Клиника", a.clinicName)
                    MedCardDetailRow(Icons.Default.LocationOn,
                        "Адрес", a.clinicAddress)
                    MedCardDetailRow(Icons.Default.CalendarMonth,
                        "Дата приёма", "${formatDate(a.slotDate)}  ${formatTime(a.slotTime)}")
                    MedCardDetailRow(Icons.Default.Timer,
                        "Длительность", "${a.durationMinutes} мин.")

                    HorizontalDivider()

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🩺 Результат приёма",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            if (a.notes != null) {
                                Text(a.notes,
                                    style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Заключение не заполнено врачом",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Text("Запись создана: ${formatDate(a.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { selectedAppointment = null }) { Text("Закрыть") }
            }
        )
    }

    selectedSymptom?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedSymptom = null },
            icon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Psychology, null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp))
                    }
                }
            },
            title = { Text("AI-анализ симптомов", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Дата: ${formatDate(item.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Описание симптомов",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(item.symptomsText,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    item.aiResponse?.let { ai ->
                        val urgencyColor = when (ai.urgency) {
                            "emergency", "urgent" -> MaterialTheme.colorScheme.error
                            "normal"              -> MaterialTheme.colorScheme.primary
                            else                  -> Color(0xFF2E7D32)
                        }
                        val urgencyText = when (ai.urgency) {
                            "emergency" -> "🚨 Экстренно"
                            "urgent"    -> "⚠️ Срочно"
                            "normal"    -> "📋 Норма"
                            else        -> "✅ Несрочно"
                        }

                        Text(urgencyText, color = urgencyColor, fontWeight = FontWeight.Bold)

                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(10.dp)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Предварительная оценка",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text(ai.diagnosis,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = RoundedCornerShape(10.dp)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Рекомендации",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.height(4.dp))
                                Text(ai.recommendations,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Точность: ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val pct = ((ai.confidence.toFloatOrNull() ?: 0f) * 100).toInt()
                            Text("$pct%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedSymptom = null }) { Text("Закрыть") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Медицинская карта") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Приёмы (${appointments.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("AI-анализы (${symptomsHistory.size})") })
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                tab == 0 -> {
                    if (appointments.isEmpty()) {
                        MedCardEmpty(
                            icon = Icons.Default.EventNote,
                            text = "Нет завершённых приёмов"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(appointments, key = { it.appointmentId }) { a ->
                                AppointmentMedCard(a) { selectedAppointment = a }
                            }
                        }
                    }
                }
                else -> {
                    if (symptomsHistory.isEmpty()) {
                        MedCardEmpty(
                            icon = Icons.Default.Psychology,
                            text = "Нет AI-анализов"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(symptomsHistory, key = { it.requestId }) { item ->
                                SymptomMedCard(item) { selectedSymptom = item }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentMedCard(a: AppointmentResponse, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MedicalServices, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(a.doctorName, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Text(a.specializationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("${formatDate(a.slotDate)}  ${formatTime(a.slotTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(a.clinicName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SymptomMedCard(item: SymptomsHistoryResponse, onClick: () -> Unit) {
    val urgency = item.aiResponse?.urgency
    val urgencyColor = when (urgency) {
        "emergency", "urgent" -> MaterialTheme.colorScheme.error
        "normal"              -> MaterialTheme.colorScheme.primary
        else                  -> Color(0xFF2E7D32)
    }
    val urgencyLabel = when (urgency) {
        "emergency" -> "🚨 Срочно"
        "urgent"    -> "⚠️ Срочно"
        "normal"    -> "📋 Норма"
        else        -> "✅ Несрочно"
    }

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Psychology, null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("AI-анализ", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(urgencyLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = urgencyColor,
                        fontWeight = FontWeight.SemiBold)
                }
                Text(item.symptomsText,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.aiResponse?.let {
                    Text(it.diagnosis,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(2.dp))
                Text(formatDate(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MedCardDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("$label: ", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MedCardEmpty(icon: ImageVector, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)) {
            Icon(icon, null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}