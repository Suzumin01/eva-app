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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.eva.app.util.ErrorMapper
import com.eva.app.util.Resource
import com.eva.app.util.formatDate
import com.eva.app.util.formatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class AppointmentsViewModel @Inject constructor(
    private val repository: AppointmentRepository
) : ViewModel() {
    private val _upcoming = MutableStateFlow<List<AppointmentResponse>>(emptyList())
    val upcoming = _upcoming.asStateFlow()
    private val _past     = MutableStateFlow<List<AppointmentResponse>>(emptyList())
    val past = _past.asStateFlow()
    private val _isLoading  = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _message  = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { loadAppointments() }

    fun loadAppointments(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _isRefreshing.value = true else _isLoading.value = true
            when (val r = repository.getMyAppointments()) {
                is Resource.Success -> {
                    val all   = r.data
                    val today = LocalDate.now()
                    val now   = LocalTime.now()
                    _upcoming.value = all.filter { a ->
                        if (a.status == "cancelled") return@filter false
                        val d = runCatching { LocalDate.parse(a.slotDate) }.getOrNull() ?: return@filter false
                        val t = runCatching { LocalTime.parse(a.slotTime) }.getOrNull() ?: return@filter false
                        d > today || (d == today && t > now)
                    }.sortedBy { it.slotDate + it.slotTime }
                    _past.value = all.filter { a ->
                        if (a.status == "cancelled") return@filter true
                        val d = runCatching { LocalDate.parse(a.slotDate) }.getOrNull() ?: return@filter false
                        val t = runCatching { LocalTime.parse(a.slotTime) }.getOrNull() ?: return@filter false
                        d < today || (d == today && t <= now)
                    }.sortedByDescending { it.slotDate + it.slotTime }
                }
                is Resource.Error -> _message.value = ErrorMapper.map(r.message ?: "")
                else -> {}
            }
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            when (val r = repository.cancelAppointment(id)) {
                is Resource.Success -> { loadAppointments(); _message.value = "Запись отменена" }
                is Resource.Error   -> _message.value = ErrorMapper.map(r.message ?: "")
                else -> {}
            }
        }
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(viewModel: AppointmentsViewModel = hiltViewModel()) {
    val upcoming     by viewModel.upcoming.collectAsState()
    val past         by viewModel.past.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val message      by viewModel.message.collectAsState()
    var tab          by remember { mutableStateOf(0) }
    val snackbar      = remember { SnackbarHostState() }

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
                else -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh    = { viewModel.loadAppointments(isRefresh = true) },
                    modifier     = Modifier.fillMaxSize()
                ) {
                    if (list.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (tab == 0) Icons.Default.CalendarToday else Icons.Default.History,
                                    null, modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Text(if (tab == 0) "Нет предстоящих записей" else "Нет прошедших записей",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(list, key = { it.appointmentId }) { a ->
                                AppointmentCard(a = a, onCancel = { viewModel.cancel(a.appointmentId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentCard(a: AppointmentResponse, onCancel: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    // Проверка — менее 24 часов до приёма?
    val isWithin24h = remember(a.slotDate, a.slotTime) {
        runCatching {
            val slotDt = LocalDate.parse(a.slotDate).atTime(LocalTime.parse(a.slotTime))
            ChronoUnit.HOURS.between(java.time.LocalDateTime.now(), slotDt) < 24
        }.getOrDefault(false)
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Отменить запись?") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Запись к ${a.doctorName} на ${formatDate(a.slotDate)} будет отменена.")
                    if (isWithin24h) {
                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("До приёма менее 24 часов. Возможен отказ в отмене.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showCancelDialog = false; onCancel() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Отменить запись")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Назад") }
            }
        )
    }

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Шапка карточки
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.doctorName, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge)
                    Text(a.specializationName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(a.status)
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DetailRow(Icons.Default.CalendarMonth, "Дата", formatDate(a.slotDate))
                DetailRow(Icons.Default.Schedule, "Время", formatTime(a.slotTime))
                DetailRow(Icons.Default.Timer, "Длит.", "${a.durationMinutes} мин")
            }

            // Раскрытая часть
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                DetailRow(Icons.Default.LocalHospital, "Клиника", a.clinicName)
                Spacer(Modifier.height(6.dp))
                DetailRow(Icons.Default.LocationOn, "Адрес", a.clinicAddress)

                a.notes?.let {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Column {
                        Text("📝 Примечание", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Заключение врача
                if (a.status == "completed") {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MedicalServices, null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Заключение врача", fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(Modifier.height(6.dp))
                            if (!a.doctorConclusion.isNullOrBlank()) {
                                Text(a.doctorConclusion,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            } else {
                                Text("Заключение ещё не добавлено",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                            }
                        }
                    }
                }

                Text("Создано: ${formatDate(a.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))

                // Кнопка отмены
                if (a.status == "scheduled") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Отменить запись")
                    }
                }
            }

            // Индикатор раскрытия
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (color, label) = when (status) {
        "scheduled"  -> Color(0xFF1565C0) to "Запланирован"
        "completed"  -> Color(0xFF2E7D32) to "Завершена"
        "cancelled"  -> Color(0xFFB71C1C) to "Отменена"
        else         -> MaterialTheme.colorScheme.onSurfaceVariant to status
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.12f)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}