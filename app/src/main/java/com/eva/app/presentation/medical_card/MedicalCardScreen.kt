package com.eva.app.presentation.medical_card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.eva.app.presentation.components.AnimatedListItem
import com.eva.app.presentation.components.EvaType
import com.eva.app.presentation.components.MedCardItemSkeleton
import com.eva.app.presentation.components.SectionHeader
import com.eva.app.presentation.components.StatusPill
import com.eva.app.presentation.components.urgencyColor
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

    var tab                 by remember { mutableStateOf(0) }
    var selectedAppointment by remember { mutableStateOf<AppointmentResponse?>(null) }
    var selectedSymptom     by remember { mutableStateOf<SymptomsHistoryResponse?>(null) }
    var showDeleteDocDialog by remember { mutableStateOf<DocumentResponse?>(null) }

    LaunchedEffect(uploadError) {
        uploadError?.let { snackbar.showSnackbar(it); viewModel.clearUploadError() }
    }
    LaunchedEffect(loadError) {
        loadError?.let {
            snackbar.showSnackbar(context.getString(R.string.error_loading_prefix, it))
        }
    }

    selectedAppointment?.let { a ->
        ModalBottomSheet(
            onDismissRequest = { selectedAppointment = null },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                Text(a.specializationName,
                    style = EvaType.cardSub,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        MedSheetInfoRow(stringResource(R.string.label_date),
                            formatDate(a.slotDate))
                        Spacer(Modifier.height(12.dp))
                        MedSheetInfoRow(stringResource(R.string.label_time),
                            formatTime(a.slotTime))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        MedSheetInfoRow(stringResource(R.string.label_clinic), a.clinicName)
                        Spacer(Modifier.height(12.dp))
                        MedSheetInfoRow(stringResource(R.string.label_address), a.clinicAddress)
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                SectionHeader(
                    stringResource(R.string.medical_card_appt_result),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    a.doctorConclusion?.takeIf { it.isNotBlank() }
                        ?: a.notes?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.medical_card_conclusion_empty),
                    style = EvaType.bodyText,
                    color = if (a.doctorConclusion.isNullOrBlank() && a.notes.isNullOrBlank())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.medical_card_created_at, formatDate(a.createdAt)),
                    style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    selectedSymptom?.let { item ->
        val urgency = item.aiResponse?.urgency
        val urgencyColor = urgencyColor(urgency)
        val urgencyText = when (urgency) {
            "emergency" -> stringResource(R.string.urgency_emergency)
            "urgent"    -> stringResource(R.string.urgency_urgent)
            "normal"    -> stringResource(R.string.urgency_normal)
            else        -> stringResource(R.string.urgency_low)
        }

        ModalBottomSheet(
            onDismissRequest = { selectedSymptom = null },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val sheetTitle = item.aiResponse?.title?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.medical_card_ai_dialog_title)
                Text(sheetTitle,
                    style = EvaType.sheetTitle)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDate(item.createdAt),
                        style = EvaType.cardMeta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.aiResponse != null) {
                        Spacer(Modifier.width(8.dp))
                        StatusPill(urgencyText, urgencyColor)
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                SectionHeader(
                    stringResource(R.string.medical_card_ai_symptoms_desc),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(item.symptomsText,
                    style    = EvaType.bodyText,
                    modifier = Modifier.fillMaxWidth())

                item.aiResponse?.let { ai ->
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(14.dp))

                    SectionHeader(
                        stringResource(R.string.medical_card_ai_assessment),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(ai.diagnosis,
                        style    = EvaType.bodyText,
                        modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.height(16.dp))
                    SectionHeader(
                        stringResource(R.string.medical_card_ai_recommendations),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(ai.recommendations,
                        style    = EvaType.bodyText,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    showDeleteDocDialog?.let { doc ->
        AlertDialog(
            onDismissRequest = { showDeleteDocDialog = null },
            title = { Text(stringResource(R.string.medical_card_delete_dialog_title)) },
            text  = { Text(stringResource(R.string.medical_card_delete_dialog_text, doc.fileName)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteDocument(doc.documentId); showDeleteDocDialog = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.btn_delete)) }
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
                title = {
                    Text(stringResource(R.string.profile_menu_medical_card),
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
                    isLoading -> LazyColumn(userScrollEnabled = false) {
                        items(5) { MedCardItemSkeleton() }
                    }
                    tab == 0 -> {
                        if (appointments.isEmpty()) {
                            MedCardEmpty(Icons.Default.EventNote,
                                stringResource(R.string.medical_card_no_appointments))
                        } else {
                            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                                itemsIndexed(appointments, key = { _, a -> a.appointmentId }) { index, a ->
                                    AnimatedListItem(index = index) {
                                        AppointmentMedCard(a) { selectedAppointment = a }
                                    }
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
                            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                                itemsIndexed(symptomsHistory, key = { _, it -> it.requestId }) { index, item ->
                                    AnimatedListItem(index = index) {
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
}

@Composable
fun AppointmentMedCard(a: AppointmentResponse, onClick: () -> Unit) {
    Card(
        onClick   = onClick,
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
                modifier         = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1565C0).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MedicalServices, null,
                    tint     = Color(0xFF1565C0),
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(a.doctorName, style = EvaType.cardTitle)
                Spacer(Modifier.height(2.dp))
                Text(a.specializationName,
                    style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(2.dp))
                Text("${formatDate(a.slotDate)}, ${formatTime(a.slotTime)}",
                    style = EvaType.cardMeta,
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
    val color = urgencyColor(urgency)
    val urgencyLabel = when (urgency) {
        "emergency" -> stringResource(R.string.urgency_emergency)
        "urgent"    -> stringResource(R.string.urgency_urgent)
        "normal"    -> stringResource(R.string.urgency_normal)
        "low"       -> stringResource(R.string.urgency_low)
        else        -> null
    }

    Card(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            val cardTitle = item.aiResponse?.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.symptoms_item_title)
            Text(cardTitle, style = EvaType.cardTitle, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(item.createdAt),
                    style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                urgencyLabel?.let {
                    Spacer(Modifier.width(8.dp))
                    StatusPill(it, color)
                }
            }
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
        FilledTonalButton(
            onClick  = { showUploadDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.btn_upload_document))
        }

        if (documents.isEmpty()) {
            MedCardEmpty(Icons.Default.Description, stringResource(R.string.documents_empty))
        } else {
            LazyColumn {
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
    val formattedSize = when {
        doc.fileSize < 1024        -> stringResource(R.string.file_size_bytes, doc.fileSize)
        doc.fileSize < 1024 * 1024 -> stringResource(R.string.file_size_kb, doc.fileSize / 1024)
        else                        -> stringResource(R.string.file_size_mb, doc.fileSize / (1024 * 1024))
    }
    val icon = when {
        doc.fileType.contains("pdf", true)   -> Icons.Default.PictureAsPdf
        doc.fileType.contains("image", true) -> Icons.Default.Image
        else                                  -> Icons.Default.Description
    }
    val iconColor = when {
        doc.fileType.contains("pdf", true)   -> Color(0xFFB71C1C)
        doc.fileType.contains("image", true) -> Color(0xFF1565C0)
        else                                  -> Color(0xFF00838F)
    }
    val categoryLabel = when (doc.category) {
        "analysis"     -> stringResource(R.string.document_category_analysis)
        "prescription" -> stringResource(R.string.document_category_prescription)
        "xray"         -> stringResource(R.string.document_category_xray)
        else           -> stringResource(R.string.document_category_default)
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null,
                    tint     = iconColor,
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.fileName,
                    style    = EvaType.cardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(categoryLabel,
                        style = EvaType.cardMeta,
                        color = MaterialTheme.colorScheme.primary)
                    Text("  ·  ",
                        style = EvaType.cardMeta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formattedSize,
                        style = EvaType.cardMeta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                doc.description?.let {
                    Text(it,
                        style    = EvaType.cardMeta,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, null,
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadDocumentDialog(
    onDismiss: () -> Unit,
    onUpload: (java.io.File, String, String) -> Unit
) {
    val context     = LocalContext.current
    var fileName    by remember { mutableStateOf("") }
    var pickedUri   by remember { mutableStateOf<android.net.Uri?>(null) }
    var category    by remember { mutableStateOf("analysis") }
    var description by remember { mutableStateOf("") }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { pickedUri = it; fileName = it.lastPathSegment ?: "document" }
    }

    val categories = listOf(
        "analysis"     to stringResource(R.string.document_category_analysis),
        "prescription" to stringResource(R.string.document_category_prescription),
        "xray"         to stringResource(R.string.document_category_xray_upload),
        "other"        to stringResource(R.string.document_category_other)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.upload_dialog_title), style = EvaType.sheetTitle)
            HorizontalDivider()
            OutlinedButton(
                onClick  = { launcher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (fileName.isEmpty()) stringResource(R.string.upload_choose_file) else fileName,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(stringResource(R.string.upload_type_label),
                style = EvaType.cardMeta,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier              = Modifier.horizontalScroll(rememberScrollState())
            ) {
                categories.forEach { (key, label) ->
                    FilterChip(
                        selected = category == key,
                        onClick  = { category = key },
                        label    = { Text(label, style = EvaType.cardMeta) }
                    )
                }
            }
            OutlinedTextField(
                value         = description,
                onValueChange = { description = it },
                label         = { Text(stringResource(R.string.label_description_optional)) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                singleLine    = true
            )
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
                enabled  = pickedUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape    = RoundedCornerShape(50)
            ) {
                Text(stringResource(R.string.btn_upload))
            }
        }
    }
}

@Composable
fun MedCardEmpty(icon: ImageVector, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp)
        ) {
            Icon(icon, null,
                modifier = Modifier.size(64.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = EvaType.cardMeta)
        }
    }
}

@Composable
private fun MedSheetInfoRow(label: String, value: String) {
    Column {
        Text(label,
            style = EvaType.bodyText,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = EvaType.bodyText)
    }
}
