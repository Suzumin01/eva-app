package com.eva.app.presentation.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
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
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val DAYS_PER_PAGE = 14

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val scheduleRepository: ScheduleRepository,
    private val appointmentRepository: AppointmentRepository
) : ViewModel() {

    private val _doctor      = MutableStateFlow<DoctorResponse?>(null)
    val doctor = _doctor.asStateFlow()

    private val _slotsByDate = MutableStateFlow<Map<String, List<ScheduleResponse>>>(emptyMap())
    val slotsByDate = _slotsByDate.asStateFlow()

    private val _availableDates = MutableStateFlow<List<String>>(emptyList())
    val availableDates = _availableDates.asStateFlow()

    private val _isLoading      = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore  = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _isLoadingSlots = MutableStateFlow(false)
    val isLoadingSlots = _isLoadingSlots.asStateFlow()

    private val _bookState      = MutableStateFlow<BookState>(BookState.Idle)
    val bookState = _bookState.asStateFlow()

    private val _hasMoreDates   = MutableStateFlow(true)
    val hasMoreDates = _hasMoreDates.asStateFlow()

    private var doctorId = 0
    private var loadedUntil: LocalDate = LocalDate.now()

    fun load(id: Int) {
        doctorId = id
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = doctorRepository.getDoctorById(id)) {
                is Resource.Success -> _doctor.value = r.data
                else -> {}
            }
            loadDateRange(LocalDate.now(), LocalDate.now().plusDays(DAYS_PER_PAGE.toLong()))

            var attempts = 0
            while (_availableDates.value.isEmpty() && _hasMoreDates.value && attempts < 4) {
                val from = loadedUntil.plusDays(1)
                val to   = from.plusDays(DAYS_PER_PAGE.toLong())
                loadDateRange(from, to)
                attempts++
            }

            _isLoading.value = false
        }
    }

    fun loadMoreDates() {
        viewModelScope.launch {
            _isLoadingMore.value = true
            val from = loadedUntil.plusDays(1)
            val to   = from.plusDays(DAYS_PER_PAGE.toLong())
            loadDateRange(from, to)
            _isLoadingMore.value = false
        }
    }

    fun loadSlotsForDate(date: String) {
        if (_slotsByDate.value.containsKey(date)) return
        viewModelScope.launch {
            _isLoadingSlots.value = true
            when (val r = scheduleRepository.getSchedules(doctorId, date = date)) {
                is Resource.Success -> {
                    val slots = r.data.filter { it.isAvailable }
                    _slotsByDate.value = _slotsByDate.value + (date to slots)
                }
                else -> {
                    _slotsByDate.value = _slotsByDate.value + (date to emptyList())
                }
            }
            _isLoadingSlots.value = false
        }
    }

    private suspend fun loadDateRange(from: LocalDate, to: LocalDate) {
        val maxDate = LocalDate.now().plusDays(90)
        if (from.isAfter(maxDate)) {
            _hasMoreDates.value = false
            return
        }

        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        when (val r = scheduleRepository.getSchedules(
            doctorId = doctorId,
            date     = from.format(fmt),
            dateTo   = to.format(fmt)
        )) {
            is Resource.Success -> {
                val slots = r.data.filter { it.isAvailable }
                loadedUntil = to
                if (slots.isEmpty()) {
                    _hasMoreDates.value = !from.isAfter(maxDate)
                } else {
                    val grouped  = slots.groupBy { it.slotDate }
                    _slotsByDate.value = _slotsByDate.value + grouped
                    val newDates = (_availableDates.value + grouped.keys).distinct().sorted()
                    _availableDates.value = newDates
                    _hasMoreDates.value  = true
                }
            }
            else -> _hasMoreDates.value = false
        }
    }

    fun book(scheduleId: Long, notes: String?) {
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
    data class Error(val msg: String) : BookState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(
    doctorId: Int,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: BookingViewModel = hiltViewModel()
) {
    val doctor         by viewModel.doctor.collectAsState()
    val availableDates by viewModel.availableDates.collectAsState()
    val slotsByDate    by viewModel.slotsByDate.collectAsState()
    val isLoading      by viewModel.isLoading.collectAsState()
    val isLoadingMore  by viewModel.isLoadingMore.collectAsState()
    val isLoadingSlots by viewModel.isLoadingSlots.collectAsState()
    val bookState      by viewModel.bookState.collectAsState()
    val hasMoreDates   by viewModel.hasMoreDates.collectAsState()

    var selectedSlot by remember { mutableStateOf<ScheduleResponse?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var notes        by remember { mutableStateOf("") }
    var showConfirm  by remember { mutableStateOf(false) }

    LaunchedEffect(doctorId) { viewModel.load(doctorId) }
    LaunchedEffect(bookState) { if (bookState is BookState.Success) onSuccess() }

    LaunchedEffect(availableDates) {
        if (selectedDate == null && availableDates.isNotEmpty()) {
            selectedDate = availableDates.first()
            viewModel.loadSlotsForDate(availableDates.first())
        }
    }
    LaunchedEffect(selectedDate) {
        selectedSlot = null
        selectedDate?.let { viewModel.loadSlotsForDate(it) }
    }

    val slotsForDate = remember(slotsByDate, selectedDate) {
        selectedDate?.let { slotsByDate[it] }?.sortedBy { it.slotTime } ?: emptyList()
    }

    if (showConfirm && selectedSlot != null) {
        BookingConfirmDialog(
            doctorName   = doctor?.fullName ?: "",
            slot         = selectedSlot!!,
            notes        = notes,
            onNotesChange = { notes = it },
            bookState    = bookState,
            onConfirm    = {
                showConfirm = false
                viewModel.book(selectedSlot!!.scheduleId, notes.ifBlank { null })
            },
            onDismiss    = { showConfirm = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.booking_screen_title)) },
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
                            val slot = selectedSlot
                            Text(
                                if (slot != null)
                                    stringResource(R.string.booking_btn_confirm_with_time,
                                        formatTime(slot.slotTime), formatDate(slot.slotDate))
                                else
                                    stringResource(R.string.booking_btn_select_slot),
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

        if (availableDates.isEmpty() && !isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.booking_no_slots_title),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.booking_no_slots_hint),
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

            item {
                DateSelectorRow(
                    availableDates = availableDates,
                    selectedDate   = selectedDate,
                    slotsByDate    = slotsByDate,
                    hasMoreDates   = hasMoreDates,
                    isLoadingMore  = isLoadingMore,
                    onSelectDate   = { selectedDate = it },
                    onLoadMore     = { viewModel.loadMoreDates() }
                )
            }

            item {
                selectedDate?.let { date ->
                    SlotGrid(
                        date         = date,
                        slots        = slotsForDate,
                        selectedSlot = selectedSlot,
                        isLoading    = isLoadingSlots && !slotsByDate.containsKey(date),
                        onSelectSlot = { slot ->
                            selectedSlot = if (selectedSlot?.scheduleId == slot.scheduleId) null else slot
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookingConfirmDialog(
    doctorName   : String,
    slot         : ScheduleResponse,
    notes        : String,
    onNotesChange: (String) -> Unit,
    bookState    : BookState,
    onConfirm    : () -> Unit,
    onDismiss    : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.booking_confirm_dialog_title), fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.booking_confirm_doctor, doctorName))
                Text(stringResource(R.string.booking_confirm_date, formatDate(slot.slotDate)))
                Text(stringResource(R.string.booking_confirm_time, formatTime(slot.slotTime)))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value         = notes,
                    onValueChange = onNotesChange,
                    label         = { Text(stringResource(R.string.label_note_optional)) },
                    minLines      = 2,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = bookState !is BookState.Loading) {
                if (bookState is BookState.Loading)
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.btn_book))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
private fun DateSelectorRow(
    availableDates : List<String>,
    selectedDate   : String?,
    slotsByDate    : Map<String, List<ScheduleResponse>>,
    hasMoreDates   : Boolean,
    isLoadingMore  : Boolean,
    onSelectDate   : (String) -> Unit,
    onLoadMore     : () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.booking_select_date_label),
            fontWeight = FontWeight.SemiBold,
            style      = MaterialTheme.typography.titleSmall,
            color      = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableDates.forEach { date ->
                val isSel     = selectedDate == date
                val dateSlots = slotsByDate[date]?.size
                FilterChip(
                    selected = isSel,
                    onClick  = { onSelectDate(date) },
                    label    = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                formatDateLabel(date),
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                style      = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                if (dateSlots != null)
                                    pluralStringResource(R.plurals.booking_slots_count, dateSlots, dateSlots)
                                else
                                    stringResource(R.string.booking_slots_loading),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (hasMoreDates) {
                if (isLoadingMore) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    OutlinedButton(
                        onClick  = onLoadMore,
                        shape    = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.booking_more_dates),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SlotGrid(
    date        : String,
    slots       : List<ScheduleResponse>,
    selectedSlot: ScheduleResponse?,
    isLoading   : Boolean,
    onSelectSlot: (ScheduleResponse) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.booking_available_time, formatDateLabel(date)),
            fontWeight = FontWeight.SemiBold,
            style      = MaterialTheme.typography.titleSmall,
            color      = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))

        when {
            isLoading -> Box(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            slots.isEmpty() -> Text(
                stringResource(R.string.booking_no_slots_for_date),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            else -> slots.chunked(4).forEach { row ->
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                                .clickable { onSelectSlot(slot) }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                formatTime(slot.slotTime),
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color      = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                style      = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
