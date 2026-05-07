package com.eva.app.presentation.appointments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.eva.app.data.api.AppointmentResponse
import com.eva.app.data.repository.AppointmentRepository
import com.eva.app.presentation.components.AnimatedListItem
import com.eva.app.presentation.components.AppointmentCardSkeleton
import com.eva.app.presentation.components.EvaType
import com.eva.app.presentation.components.StatusPill
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
    private val repository: AppointmentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _upcoming     = MutableStateFlow<List<AppointmentResponse>>(emptyList())
    val upcoming = _upcoming.asStateFlow()
    private val _past         = MutableStateFlow<List<AppointmentResponse>>(emptyList())
    val past = _past.asStateFlow()
    private val _isLoading    = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _message      = MutableStateFlow<String?>(null)
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
            _isLoading.value    = false
            _isRefreshing.value = false
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            when (val r = repository.cancelAppointment(id)) {
                is Resource.Success -> { loadAppointments(); _message.value = context.getString(R.string.appointment_cancelled_success) }
                is Resource.Error   -> _message.value = ErrorMapper.map(r.message ?: "")
                else -> {}
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun isWithin24Hours(slotDate: String, slotTime: String): Boolean = runCatching {
        val slotDt = LocalDate.parse(slotDate).atTime(LocalTime.parse(slotTime))
        ChronoUnit.HOURS.between(java.time.LocalDateTime.now(), slotDt) < 24
    }.getOrDefault(false)
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "scheduled" -> MaterialTheme.colorScheme.primary
    "completed" -> MaterialTheme.colorScheme.tertiary
    else        -> MaterialTheme.colorScheme.error
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
                    text = { Text(stringResource(R.string.tab_upcoming, upcoming.size)) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.tab_past, past.size)) })
            }

            val list = if (tab == 0) upcoming else past

            when {
                isLoading -> LazyColumn(
                    contentPadding    = PaddingValues(vertical = 8.dp),
                    modifier          = Modifier.fillMaxSize(),
                    userScrollEnabled = false
                ) {
                    items(5) { AppointmentCardSkeleton() }
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
                                Text(
                                    if (tab == 0) stringResource(R.string.appointments_upcoming_empty)
                                    else stringResource(R.string.appointments_past_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            itemsIndexed(list, key = { _, a -> a.appointmentId }) { index, a ->
                                AnimatedListItem(index = index) {
                                    AppointmentCard(
                                        a           = a,
                                        isWithin24h = viewModel.isWithin24Hours(a.slotDate, a.slotTime),
                                        onCancel    = { viewModel.cancel(a.appointmentId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentCard(a: AppointmentResponse, isWithin24h: Boolean, onCancel: () -> Unit) {
    var showSheet        by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val color = statusColor(a.status)
    val statusLabel = when (a.status) {
        "scheduled" -> stringResource(R.string.appointment_status_scheduled)
        "completed" -> stringResource(R.string.appointment_status_completed)
        else        -> stringResource(R.string.appointment_status_cancelled)
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.appointment_cancel_dialog_title), style = EvaType.sheetTitle) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.appointment_cancel_dialog_text,
                        a.doctorName, formatDate(a.slotDate)), style = EvaType.bodyText)
                    if (isWithin24h) {
                        Row(
                            modifier          = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null,
                                tint     = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.appointment_cancel_within_24h),
                                style = EvaType.cardMeta,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCancelDialog = false; onCancel() },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.btn_cancel_appointment)) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.btn_back))
                }
            }
        )
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState       = sheetState
        ) {
            AppointmentBottomSheet(
                a           = a,
                statusColor = color,
                statusLabel = statusLabel,
                onCancel    = { showSheet = false; showCancelDialog = true }
            )
        }
    }

    Card(
        onClick   = { showSheet = true },
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CalendarMonth, null,
                    tint     = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.doctorName, style = EvaType.cardTitle)
                Spacer(Modifier.height(2.dp))
                Text(a.clinicName,
                    style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("${formatDate(a.slotDate)}, ${formatTime(a.slotTime)}",
                    style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(statusLabel, color)
        }
    }
}

@Composable
fun AppointmentBottomSheet(
    a: AppointmentResponse,
    statusColor: Color,
    statusLabel: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
    ) {
        Text(a.doctorName, style = EvaType.sheetTitle)
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(a.specializationName,
                style = EvaType.cardSub,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusPill(statusLabel, statusColor)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(14.dp))

        SheetInfoRow(Icons.Default.CalendarMonth, stringResource(R.string.label_date),   formatDate(a.slotDate))
        Spacer(Modifier.height(12.dp))
        SheetInfoRow(Icons.Default.AccessTime,    stringResource(R.string.label_time),   formatTime(a.slotTime))
        Spacer(Modifier.height(12.dp))
        SheetInfoRow(Icons.Default.LocalHospital, stringResource(R.string.label_clinic), a.clinicName)
        Spacer(Modifier.height(12.dp))
        SheetInfoRow(Icons.Default.LocationOn,    stringResource(R.string.label_address), a.clinicAddress)

        a.notes?.let {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            SheetInfoRow(Icons.Default.Notes, stringResource(R.string.appointment_detail_note), it)
        }

        if (a.status == "completed") {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.appointment_detail_conclusion),
                style = EvaType.cardSub,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                a.doctorConclusion?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.appointment_conclusion_empty),
                style = EvaType.bodyText
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.appointment_created, formatDate(a.createdAt)),
            style = EvaType.cardMeta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (a.status == "scheduled") {
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick  = onCancel,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.btn_cancel_appointment))
            }
        }
    }
}

@Composable
private fun SheetInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(18.dp).padding(top = 2.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label,
                style = EvaType.cardMeta,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = EvaType.cardSub)
        }
    }
}
