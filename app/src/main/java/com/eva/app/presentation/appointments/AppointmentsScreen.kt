package com.eva.app.presentation.appointments

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.api.AppointmentResponse
import com.eva.app.data.repository.AppointmentRepository
import com.eva.app.util.Resource
import com.eva.app.util.formatDate
import com.eva.app.util.formatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class AppointmentsViewModel @Inject constructor(
    private val repository: AppointmentRepository
) : ViewModel() {
    val upcoming  = MutableStateFlow<List<AppointmentResponse>>(emptyList())
    val past      = MutableStateFlow<List<AppointmentResponse>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _message  = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { loadAppointments() }

    fun loadAppointments() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = repository.getMyAppointments()) {
                is Resource.Success -> {
                    val all   = r.data
                    val today = LocalDate.now()
                    val now   = LocalTime.now()
                    upcoming.value = all.filter { a ->
                        if (a.status == "cancelled") return@filter false
                        val d = runCatching { LocalDate.parse(a.slotDate) }.getOrNull() ?: return@filter false
                        val t = runCatching { LocalTime.parse(a.slotTime) }.getOrNull() ?: return@filter false
                        d > today || (d == today && t > now)
                    }.sortedBy { it.slotDate + it.slotTime }
                    past.value = all.filter { a ->
                        if (a.status == "cancelled") return@filter true
                        val d = runCatching { LocalDate.parse(a.slotDate) }.getOrNull() ?: return@filter false
                        val t = runCatching { LocalTime.parse(a.slotTime) }.getOrNull() ?: return@filter false
                        d < today || (d == today && t <= now)
                    }.sortedByDescending { it.slotDate + it.slotTime }
                }
                is Resource.Error -> _message.value = r.message
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            when (val r = repository.cancelAppointment(id)) {
                is Resource.Success -> loadAppointments()
                is Resource.Error   -> _message.value = r.message
                else -> {}
            }
        }
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(viewModel: AppointmentsViewModel = hiltViewModel()) {
    val upcoming  by viewModel.upcoming.collectAsState()
    val past      by viewModel.past.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message   by viewModel.message.collectAsState()
    var tab       by remember { mutableStateOf(0) }
    val snackbar   = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Предстоящие (${upcoming.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Прошедшие (${past.size})") })
            }
            val list = if (tab == 0) upcoming else past
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (tab == 0) Icons.Default.CalendarToday else Icons.Default.History,
                            null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(if (tab == 0) "Нет предстоящих записей" else "Нет прошедших записей",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list, key = { it.appointmentId }) { a ->
                        AppointmentCard(a) { viewModel.cancel(a.appointmentId) }
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentCard(a: AppointmentResponse, onCancel: () -> Unit) {
    var showDetail  by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    if (showDetail) {
        AlertDialog(
            onDismissRequest = { showDetail = false },
            icon = {
                Surface(shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MedicalServices, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp))
                    }
                }
            },
            title = { Text(a.doctorName, fontWeight = FontWeight.Bold) },
            text  = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow(Icons.Default.LocalHospital, "Специализация", a.specializationName)
                    DetailRow(Icons.Default.Business, "Клиника", a.clinicName)
                    DetailRow(Icons.Default.LocationOn, "Адрес", a.clinicAddress)
                    DetailRow(Icons.Default.CalendarMonth, "Дата",
                        "${formatDate(a.slotDate)}  ${formatTime(a.slotTime)}")
                    DetailRow(Icons.Default.Timer, "Длительность",
                        "${a.durationMinutes} мин.")

                    val statusText = when (a.status) {
                        "scheduled" -> "Активна"
                        "completed" -> "Завершена"
                        "cancelled" -> "Отменена"
                        else        -> a.status
                    }
                    val statusColor = when (a.status) {
                        "scheduled" -> Color(0xFF1565C0)
                        "completed" -> Color(0xFF2E7D32)
                        "cancelled" -> MaterialTheme.colorScheme.error
                        else        -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Статус: ", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(statusText, style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold, color = statusColor)
                    }

                    a.notes?.let {
                        HorizontalDivider()
                        Column {
                            Text("📝 Примечание", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Если статус completed — показать блок диагноза/заключения
                    if (a.status == "completed") {
                        HorizontalDivider()
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🩺 Результат приёма", fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.height(4.dp))
                                Text("Заключение и диагноз доступны у лечащего врача или в медицинской карте.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    Text("Создано: ${formatDate(a.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Row {
                    if (a.status == "scheduled") {
                        TextButton(
                            onClick = { showDetail = false; showConfirm = true },
                            colors  = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Отменить запись") }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { showDetail = false }) { Text("Закрыть") }
                }
            }
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Отменить запись?") },
            text  = { Text("Запись к ${a.doctorName} на ${formatDate(a.slotDate)} будет отменена.") },
            confirmButton = {
                TextButton(
                    onClick = { showConfirm = false; onCancel() },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Отменить запись") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Назад") }
            }
        )
    }

    val statusColor = when (a.status) {
        "scheduled" -> MaterialTheme.colorScheme.primary
        "completed" -> Color(0xFF2E7D32)
        "cancelled" -> MaterialTheme.colorScheme.error
        else        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (a.status) {
        "scheduled" -> "Активна"
        "completed" -> "Завершена"
        "cancelled" -> "Отменена"
        else        -> a.status
    }

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { showDetail = true },
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(a.doctorName, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge)
                    Text(a.specializationName, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium)
                }
                AssistChip(
                    onClick = {},
                    label   = { Text(statusText, style = MaterialTheme.typography.labelSmall) },
                    colors  = AssistChipDefaults.assistChipColors(labelColor = statusColor)
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("${formatDate(a.slotDate)}  ${formatTime(a.slotTime)}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(a.clinicName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            a.notes?.let {
                Spacer(Modifier.height(4.dp))
                Text("📝 $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End) {
                Text("Подробнее", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("$label: ", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}