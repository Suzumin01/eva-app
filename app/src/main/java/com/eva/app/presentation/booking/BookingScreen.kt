package com.eva.app.presentation.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.api.AppointmentResponse
import com.eva.app.data.api.DoctorResponse
import com.eva.app.data.api.ScheduleResponse
import com.eva.app.data.repository.AppointmentRepository
import com.eva.app.data.repository.DoctorRepository
import com.eva.app.data.repository.ScheduleRepository
import com.eva.app.util.ErrorMapper
import com.eva.app.util.Resource
import com.eva.app.util.formatDate
import com.eva.app.util.formatDateLabel
import com.eva.app.util.formatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val scheduleRepository: ScheduleRepository,
    private val appointmentRepository: AppointmentRepository
) : ViewModel() {
    private val _doctor    = MutableStateFlow<DoctorResponse?>(null)
    val doctor = _doctor.asStateFlow()
    private val _schedules = MutableStateFlow<List<ScheduleResponse>>(emptyList())
    val schedules = _schedules.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _bookState = MutableStateFlow<BookState>(BookState.Idle)
    val bookState = _bookState.asStateFlow()

    fun load(doctorId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = doctorRepository.getDoctorById(doctorId)) {
                is Resource.Success -> _doctor.value = r.data
                else -> {}
            }
            when (val r = scheduleRepository.getSchedules(doctorId)) {
                is Resource.Success -> {
                    val today = LocalDate.now()
                    val now   = LocalTime.now()
                    _schedules.value = r.data.filter { slot ->
                        if (!slot.isAvailable) return@filter false
                        val d = runCatching { LocalDate.parse(slot.slotDate) }.getOrNull()
                            ?: return@filter false
                        val t = runCatching { LocalTime.parse(slot.slotTime) }.getOrNull()
                            ?: return@filter false
                        d > today || (d == today && t > now)
                    }
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun book(doctorId: Int, scheduleId: Long, notes: String?) {
        viewModelScope.launch {
            _bookState.value = BookState.Loading
            _bookState.value = when (val r =
                appointmentRepository.createAppointment(doctorId, scheduleId, notes)) {
                is Resource.Success -> BookState.Success(r.data)
                is Resource.Error   -> BookState.Error(ErrorMapper.map(r.message))
                else -> BookState.Error("Ошибка при записи")
            }
        }
    }

    fun resetBook() { _bookState.value = BookState.Idle }
}

sealed class BookState {
    object Idle    : BookState()
    object Loading : BookState()
    data class Success(val appt: AppointmentResponse) : BookState()
    data class Error(val msg: String)   : BookState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(
    doctorId: Int,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: BookingViewModel = hiltViewModel()
) {
    val doctor    by viewModel.doctor.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val bookState by viewModel.bookState.collectAsState()

    var selectedSlot by remember { mutableStateOf<ScheduleResponse?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var notes        by remember { mutableStateOf("") }
    var showConfirm  by remember { mutableStateOf(false) }

    LaunchedEffect(doctorId) { viewModel.load(doctorId) }
    LaunchedEffect(bookState) { if (bookState is BookState.Success) onSuccess() }

    // Все доступные даты из слотов
    val availableDates = remember(schedules) {
        schedules.map { it.slotDate }.distinct().sorted()
    }

    // Автовыбор первой даты
    LaunchedEffect(availableDates) {
        if (selectedDate == null && availableDates.isNotEmpty()) {
            selectedDate = availableDates.first()
        }
    }

    // Слоты для выбранной даты
    val slotsForDate = remember(schedules, selectedDate) {
        schedules.filter { it.slotDate == selectedDate }.sortedBy { it.slotTime }
    }

    // Сброс слота при смене даты
    LaunchedEffect(selectedDate) { selectedSlot = null }

    // Диалог подтверждения
    if (showConfirm && selectedSlot != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Подтвердить запись", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Врач: ${doctor?.fullName}")
                    Text("Дата: ${formatDate(selectedSlot!!.slotDate)}")
                    Text("Время: ${formatTime(selectedSlot!!.slotTime)}")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        label = { Text("Примечание (необязательно)") },
                        minLines = 2, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick  = { showConfirm = false; viewModel.book(doctorId, selectedSlot!!.scheduleId, notes.ifBlank { null }) },
                    enabled  = bookState !is BookState.Loading
                ) {
                    if (bookState is BookState.Loading)
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Записаться")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Запись на приём") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column {
                    if (bookState is BookState.Error) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text((bookState as BookState.Error).msg,
                                modifier = Modifier.padding(10.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(
                        onClick  = { showConfirm = true },
                        enabled  = selectedSlot != null && bookState !is BookState.Loading,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        if (bookState is BookState.Loading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                color = Color.White)
                        } else {
                            Text(
                                if (selectedSlot != null)
                                    "Подтвердить: ${formatTime(selectedSlot!!.slotTime)}  ${formatDate(selectedSlot!!.slotDate)}"
                                else "Выберите удобный слот",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (availableDates.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("Нет доступных слотов",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Выберите другого врача или попробуйте позже",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {

            // Шапка врача
            doctor?.let { doc ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(doc.fullName, fontWeight = FontWeight.SemiBold)
                                Text(doc.specializationName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(doc.clinicName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Фильтр по дате — горизонтальный скролл
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Выберите дату",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableDates.forEach { date ->
                            val isSel = selectedDate == date
                            val dateSlots = schedules.count { it.slotDate == date }
                            FilterChip(
                                selected = isSel,
                                onClick  = { selectedDate = date },
                                label    = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(formatDateLabel(date),
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.labelMedium)
                                        Text("$dateSlots слотов",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Сетка слотов выбранной даты
            item {
                selectedDate?.let { date ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Доступное время — ${formatDateLabel(date)}",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        if (slotsForDate.isEmpty()) {
                            Text("Нет слотов на эту дату",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            slotsForDate.chunked(4).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { slot ->
                                        val isSel = selectedSlot?.scheduleId == slot.scheduleId
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSel) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .border(
                                                    if (isSel) 0.dp else 1.dp,
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    selectedSlot = if (isSel) null else slot
                                                }
                                                .padding(vertical = 11.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                formatTime(slot.slotTime),
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) Color.White
                                                else MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}