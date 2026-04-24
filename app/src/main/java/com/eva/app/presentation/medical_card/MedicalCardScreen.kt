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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.api.AppointmentResponse
import com.eva.app.data.api.DocumentResponse
import com.eva.app.data.api.SymptomsHistoryResponse
import com.eva.app.data.repository.AppointmentRepository
import com.eva.app.data.repository.DocumentRepository
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
    private val symptomsRepository: SymptomsRepository,
    private val documentRepository: DocumentRepository
) : ViewModel() {
    private val _appointments    = MutableStateFlow<List<AppointmentResponse>>(emptyList())
    val appointments = _appointments.asStateFlow()
    private val _symptomsHistory = MutableStateFlow<List<SymptomsHistoryResponse>>(emptyList())
    val symptomsHistory = _symptomsHistory.asStateFlow()
    private val _documents       = MutableStateFlow<List<DocumentResponse>>(emptyList())
    val documents = _documents.asStateFlow()
    private val _isLoading       = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isRefreshing    = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _uploadError     = MutableStateFlow<String?>(null)
    val uploadError = _uploadError.asStateFlow()
    private val _loadError       = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    init { load() }

    fun load(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _isRefreshing.value = true else _isLoading.value = true
            _loadError.value = null
            var firstError: String? = null
            when (val r = appointmentRepository.getMyAppointments()) {
                is Resource.Success -> _appointments.value =
                    r.data.filter { it.status == "completed" }
                        .sortedByDescending { it.slotDate + it.slotTime }
                is Resource.Error -> if (firstError == null) firstError = r.message
                else -> {}
            }
            when (val r = symptomsRepository.getHistory()) {
                is Resource.Success -> _symptomsHistory.value = r.data
                is Resource.Error -> if (firstError == null) firstError = r.message
                else -> {}
            }
            when (val r = documentRepository.getDocuments()) {
                is Resource.Success -> _documents.value = r.data
                is Resource.Error -> if (firstError == null) firstError = r.message
                else -> {}
            }
            _loadError.value = firstError
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun uploadDocument(file: java.io.File, category: String, description: String) {
        viewModelScope.launch {
            when (val r = documentRepository.uploadDocument(file, category, description.ifBlank { null })) {
                is Resource.Success -> load()
                is Resource.Error   -> _uploadError.value = r.message ?: "Ошибка загрузки"
                else -> {}
            }
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            documentRepository.deleteDocument(id)
            load()
        }
    }

    fun clearUploadError() { _uploadError.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalCardScreen(
    onBack: () -> Unit,
    viewModel: MedicalCardViewModel = hiltViewModel()
) {
    val appointments    by viewModel.appointments.collectAsState()
    val symptomsHistory by viewModel.symptomsHistory.collectAsState()
    val documents       by viewModel.documents.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val isRefreshing    by viewModel.isRefreshing.collectAsState()
    val uploadError     by viewModel.uploadError.collectAsState()
    val loadError       by viewModel.loadError.collectAsState()
    val snackbar         = remember { SnackbarHostState() }
    val context          = LocalContext.current

    var tab                  by remember { mutableStateOf(0) }
    var selectedAppointment  by remember { mutableStateOf<AppointmentResponse?>(null) }
    var selectedSymptom      by remember { mutableStateOf<SymptomsHistoryResponse?>(null) }
    var showDeleteDocDialog  by remember { mutableStateOf<DocumentResponse?>(null) }

    LaunchedEffect(uploadError) {
        uploadError?.let { snackbar.showSnackbar(it); viewModel.clearUploadError() }
    }
    LaunchedEffect(loadError) {
        loadError?.let {
            snackbar.showSnackbar(context.getString(R.string.error_loading_prefix, it))
        }
    }

    // Диалог деталей приёма
    selectedAppointment?.let { a ->
        AlertDialog(
            onDismissRequest = { selectedAppointment = null },
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
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MedCardDetailRow(Icons.Default.LocalHospital,
                        stringResource(R.string.medical_card_appt_specialization), a.specializationName)
                    MedCardDetailRow(Icons.Default.Business,
                        stringResource(R.string.medical_card_appt_clinic), a.clinicName)
                    MedCardDetailRow(Icons.Default.LocationOn,
                        stringResource(R.string.medical_card_appt_address), a.clinicAddress)
                    MedCardDetailRow(Icons.Default.CalendarMonth,
                        stringResource(R.string.medical_card_appt_date),
                        "${formatDate(a.slotDate)}  ${formatTime(a.slotTime)}")
                    MedCardDetailRow(Icons.Default.Timer,
                        stringResource(R.string.medical_card_appt_duration),
                        stringResource(R.string.duration_minutes_dot, a.durationMinutes))
                    HorizontalDivider()
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.medical_card_appt_result),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            if (!a.doctorConclusion.isNullOrBlank()) {
                                Text(a.doctorConclusion, style = MaterialTheme.typography.bodySmall)
                            } else if (a.notes != null) {
                                Text(a.notes, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text(stringResource(R.string.medical_card_conclusion_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(stringResource(R.string.medical_card_created_at, formatDate(a.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { selectedAppointment = null }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    // Диалог деталей AI-анализа
    selectedSymptom?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedSymptom = null },
            icon = {
                Surface(shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Psychology, null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp))
                    }
                }
            },
            title = { Text(stringResource(R.string.medical_card_ai_dialog_title),
                fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.medical_card_ai_date, formatDate(item.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(stringResource(R.string.medical_card_ai_symptoms_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(item.symptomsText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item.aiResponse?.let { ai ->
                        val urgencyColor = when (ai.urgency) {
                            "emergency", "urgent" -> MaterialTheme.colorScheme.error
                            "normal"              -> MaterialTheme.colorScheme.primary
                            else                  -> Color(0xFF2E7D32)
                        }
                        val urgencyText = when (ai.urgency) {
                            "emergency" -> stringResource(R.string.urgency_emergency)
                            "urgent"    -> stringResource(R.string.urgency_urgent)
                            "normal"    -> stringResource(R.string.urgency_normal)
                            else        -> stringResource(R.string.urgency_low)
                        }
                        Text(urgencyText, color = urgencyColor, fontWeight = FontWeight.Bold)
                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(10.dp)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(stringResource(R.string.medical_card_ai_assessment),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text(ai.diagnosis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = RoundedCornerShape(10.dp)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(stringResource(R.string.medical_card_ai_recommendations),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.height(4.dp))
                                Text(ai.recommendations, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        val pct = ((ai.confidence.toFloatOrNull() ?: 0f) * 100).toInt()
                        Text(stringResource(R.string.medical_card_ai_accuracy, pct),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedSymptom = null }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    // Диалог удаления документа
    showDeleteDocDialog?.let { doc ->
        AlertDialog(
            onDismissRequest = { showDeleteDocDialog = null },
            title = { Text(stringResource(R.string.medical_card_delete_dialog_title)) },
            text  = { Text(stringResource(R.string.medical_card_delete_dialog_text, doc.fileName)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteDocument(doc.documentId); showDeleteDocDialog = null },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDocDialog = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.medical_card_screen_title)) },
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { viewModel.load(isRefresh = true) },
            modifier     = Modifier.padding(padding).fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 },
                        text = { Text(stringResource(R.string.medical_tab_appointments,
                            appointments.size)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 },
                        text = { Text(stringResource(R.string.medical_tab_ai,
                            symptomsHistory.size)) })
                    Tab(selected = tab == 2, onClick = { tab = 2 },
                        text = { Text(stringResource(R.string.medical_tab_documents,
                            documents.size)) })
                }

                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    tab == 0 -> {
                        if (appointments.isEmpty()) {
                            MedCardEmpty(Icons.Default.EventNote,
                                stringResource(R.string.medical_card_no_appointments))
                        } else {
                            LazyColumn(contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(appointments, key = { it.appointmentId }) { a ->
                                    AppointmentMedCard(a) { selectedAppointment = a }
                                }
                            }
                        }
                    }
                    tab == 2 -> {
                        DocumentsTab(
                            documents = documents,
                            onDelete  = { doc -> showDeleteDocDialog = doc },
                            onUpload  = { file, cat, desc -> viewModel.uploadDocument(file, cat, desc) }
                        )
                    }
                    else -> {
                        if (symptomsHistory.isEmpty()) {
                            MedCardEmpty(Icons.Default.Psychology,
                                stringResource(R.string.medical_card_no_ai))
                        } else {
                            LazyColumn(contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
}

@Composable
fun AppointmentMedCard(a: AppointmentResponse, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)) {
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
                Text(a.specializationName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("${formatDate(a.slotDate)}  ${formatTime(a.slotTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(a.clinicName, style = MaterialTheme.typography.labelSmall,
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
        "emergency" -> stringResource(R.string.urgency_emergency)
        "urgent"    -> stringResource(R.string.urgency_urgent)
        "normal"    -> stringResource(R.string.urgency_normal)
        else        -> stringResource(R.string.urgency_low)
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)) {
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
                    Text(stringResource(R.string.medical_card_ai_card_title),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(urgencyLabel, style = MaterialTheme.typography.labelSmall,
                        color = urgencyColor, fontWeight = FontWeight.SemiBold)
                }
                Text(item.symptomsText, maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.aiResponse?.let {
                    Text(it.diagnosis, maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(2.dp))
                Text(formatDate(item.createdAt), style = MaterialTheme.typography.labelSmall,
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
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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

@Composable
fun DocumentsTab(
    documents: List<DocumentResponse>,
    onDelete: (DocumentResponse) -> Unit,
    onUpload: (java.io.File, String, String) -> Unit
) {
    var showUploadDialog by remember { mutableStateOf(false) }

    if (showUploadDialog) {
        UploadDocumentDialog(
            onDismiss = { showUploadDialog = false },
            onUpload  = { file, cat, desc -> showUploadDialog = false; onUpload(file, cat, desc) }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedButton(
            onClick  = { showUploadDialog = true },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.btn_upload_document))
        }

        if (documents.isEmpty()) {
            MedCardEmpty(Icons.Default.Description,
                stringResource(R.string.documents_empty))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(documents, key = { it.documentId }) { doc ->
                    DocumentCard(doc = doc, onDelete = { onDelete(doc) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun DocumentCard(doc: DocumentResponse, onDelete: () -> Unit) {
    val (icon, color) = when {
        doc.fileType.contains("pdf", true)   -> Icons.Default.PictureAsPdf to Color(0xFFD32F2F)
        doc.fileType.contains("image", true) -> Icons.Default.Image to Color(0xFF1565C0)
        else                                  -> Icons.Default.Description to Color(0xFF616161)
    }
    val categoryLabel = when (doc.category) {
        "analysis"     -> stringResource(R.string.document_category_analysis)
        "prescription" -> stringResource(R.string.document_category_prescription)
        "xray"         -> stringResource(R.string.document_category_xray)
        else           -> stringResource(R.string.document_category_default)
    }
    val sizeText = when {
        doc.fileSize < 1024        -> "${doc.fileSize} Б"
        doc.fileSize < 1024 * 1024 -> "${doc.fileSize / 1024} КБ"
        else                       -> "${doc.fileSize / (1024 * 1024)} МБ"
    }

    Card(shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.fileName, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {},
                        label = { Text(categoryLabel,
                            style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(22.dp))
                    Text(sizeText, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically))
                }
                doc.description?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, null,
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun UploadDocumentDialog(
    onDismiss: () -> Unit,
    onUpload: (java.io.File, String, String) -> Unit
) {
    val context      = androidx.compose.ui.platform.LocalContext.current
    var fileName     by remember { mutableStateOf("") }
    var pickedUri    by remember { mutableStateOf<android.net.Uri?>(null) }
    var category     by remember { mutableStateOf("analysis") }
    var description  by remember { mutableStateOf("") }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            pickedUri = it
            fileName  = it.lastPathSegment ?: "document"
        }
    }

    val categories = listOf(
        "analysis"     to stringResource(R.string.document_category_analysis),
        "prescription" to stringResource(R.string.document_category_prescription),
        "xray"         to stringResource(R.string.document_category_xray_upload),
        "other"        to stringResource(R.string.document_category_other)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upload_dialog_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { launcher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (fileName.isEmpty()) stringResource(R.string.upload_choose_file)
                        else fileName,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(stringResource(R.string.upload_type_label),
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { (key, label) ->
                        FilterChip(selected = category == key, onClick = { category = key },
                            label = { Text(label,
                                style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text(stringResource(R.string.label_description_optional)) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    pickedUri?.let { uri ->
                        val tmpFile = java.io.File(context.cacheDir, fileName)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tmpFile.outputStream().use { out -> out.write(input.readBytes()) }
                        }
                        onUpload(tmpFile, category, description)
                    }
                },
                enabled = pickedUri != null
            ) { Text(stringResource(R.string.btn_upload)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
