package com.eva.app.presentation.medical_card

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.compose.AsyncImage
import okhttp3.OkHttpClient
import com.eva.app.BuildConfig
import com.eva.app.R
import com.eva.app.data.api.DocumentResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.DocumentRepository
import com.eva.app.util.Resource
import com.eva.app.presentation.components.AnimatedListItem
import com.eva.app.presentation.components.EvaType
import com.eva.app.presentation.components.MedCardItemSkeleton
import com.eva.app.util.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MedicalCardViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val tokenManager: TokenManager,
    private val okHttpClient: OkHttpClient
) : ViewModel() {
    private val _documents    = MutableStateFlow<List<DocumentResponse>>(emptyList())
    val documents = _documents.asStateFlow()
    private val _isLoading    = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _uploadError  = MutableStateFlow<String?>(null)
    val uploadError = _uploadError.asStateFlow()
    private val _loadError    = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    val authToken = tokenManager.token

    init { load() }

    fun load(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _isRefreshing.value = true else _isLoading.value = true
            _loadError.value = null
            when (val r = documentRepository.getDocuments()) {
                is Resource.Success -> _documents.value = r.data
                is Resource.Error   -> _loadError.value = r.message
                else -> {}
            }
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun uploadDocument(file: File, category: String, description: String) {
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

    fun updateDocument(id: String, description: String, category: String) {
        viewModelScope.launch {
            documentRepository.updateDocument(id, description.ifBlank { null }, category)
            load()
        }
    }

    fun clearUploadError() { _uploadError.value = null }

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError = _downloadError.asStateFlow()
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()
    private val _openFileEvent = MutableStateFlow<File?>(null)
    val openFileEvent = _openFileEvent.asStateFlow()

    fun downloadAndOpen(context: android.content.Context, doc: DocumentResponse) {
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadError.value = null
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val token = tokenManager.cachedToken ?: return@runCatching null
                    val url = BuildConfig.BASE_URL.substringBefore("/api/") + doc.downloadUrl
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) return@runCatching null
                    val dir = File(context.cacheDir, "documents").also { it.mkdirs() }
                    val file = File(dir, doc.documentId)
                    response.body?.byteStream()?.use { input ->
                        file.outputStream().use { it.write(input.readBytes()) }
                    }
                    file
                }.getOrNull()
            }
            _isDownloading.value = false
            if (file != null) _openFileEvent.value = file
            else _downloadError.value = "Не удалось открыть файл"
        }
    }

    fun clearOpenFileEvent() { _openFileEvent.value = null }
    fun clearDownloadError() { _downloadError.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalCardScreen(
    onBack: () -> Unit,
    viewModel: MedicalCardViewModel = hiltViewModel()
) {
    val documents    by viewModel.documents.collectAsState()
    val isLoading     by viewModel.isLoading.collectAsState()
    val isRefreshing  by viewModel.isRefreshing.collectAsState()
    val uploadError   by viewModel.uploadError.collectAsState()
    val loadError     by viewModel.loadError.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val openFileEvent by viewModel.openFileEvent.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val authToken     by viewModel.authToken.collectAsState(initial = null)
    val snackbar       = remember { SnackbarHostState() }
    val context        = LocalContext.current

    var showUploadDialog by remember { mutableStateOf(false) }
    var contextMenuDoc   by remember { mutableStateOf<DocumentResponse?>(null) }
    var deleteConfirmDoc by remember { mutableStateOf<DocumentResponse?>(null) }
    var editingDoc       by remember { mutableStateOf<DocumentResponse?>(null) }
    var galleryStartDoc  by remember { mutableStateOf<DocumentResponse?>(null) }

    val imageDocuments = remember(documents) { documents.filter { it.fileType == "image" } }

    val authImageLoader = remember(authToken) {
        ImageLoader.Builder(context)
            .okHttpClient(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val req = if (authToken != null)
                            chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer $authToken")
                                .build()
                        else chain.request()
                        chain.proceed(req)
                    }
                    .build()
            )
            .build()
    }

    LaunchedEffect(uploadError) {
        uploadError?.let { snackbar.showSnackbar(it); viewModel.clearUploadError() }
    }
    LaunchedEffect(loadError) {
        loadError?.let { snackbar.showSnackbar(context.getString(R.string.error_loading_prefix, it)) }
    }
    LaunchedEffect(downloadError) {
        downloadError?.let { snackbar.showSnackbar(it); viewModel.clearDownloadError() }
    }
    LaunchedEffect(openFileEvent) {
        val file = openFileEvent ?: return@LaunchedEffect
        viewModel.clearOpenFileEvent()
        // Передаём текущий doc из contextMenuDoc уже закрыт — берём fileType из имени файла
        val ext = file.name.substringAfterLast('.', "")
        val mime = when (ext.lowercase()) { "pdf" -> "application/pdf"; "jpg", "jpeg", "png" -> "image/*"; else -> "*/*" }
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    galleryStartDoc?.let { startDoc ->
        val startIndex = imageDocuments.indexOfFirst { it.documentId == startDoc.documentId }.coerceAtLeast(0)
        ImageGallery(
            images      = imageDocuments,
            initialIndex = startIndex,
            imageLoader = authImageLoader,
            onDismiss   = { galleryStartDoc = null }
        )
    }

    contextMenuDoc?.let { doc ->
        val catLabel = categoryLabel(doc.category)
        val title = doc.description?.takeIf { it.isNotBlank() } ?: catLabel
        ModalBottomSheet(
            onDismissRequest = { contextMenuDoc = null },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    title,
                    style    = EvaType.sheetTitle,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider()
                DocMenuAction(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.doc_action_open)) {
                    contextMenuDoc = null
                    when (doc.fileType) {
                        "image" -> galleryStartDoc = doc
                        else    -> viewModel.downloadAndOpen(context, doc)
                    }
                }
                DocMenuAction(Icons.Default.Edit, stringResource(R.string.doc_action_edit)) {
                    contextMenuDoc = null
                    editingDoc = doc
                }
                DocMenuAction(Icons.Default.DeleteOutline, stringResource(R.string.doc_action_delete),
                    tint = MaterialTheme.colorScheme.error) {
                    contextMenuDoc = null
                    deleteConfirmDoc = doc
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    editingDoc?.let { doc ->
        EditDocumentDialog(
            doc      = doc,
            onSave   = { desc, cat -> viewModel.updateDocument(doc.documentId, desc, cat); editingDoc = null },
            onDismiss = { editingDoc = null }
        )
    }

    deleteConfirmDoc?.let { doc ->
        val docDisplayName = doc.description?.takeIf { it.isNotBlank() } ?: categoryLabel(doc.category)
        AlertDialog(
            onDismissRequest = { deleteConfirmDoc = null },
            title = { Text(stringResource(R.string.medical_card_delete_dialog_title), style = EvaType.sheetTitle) },
            text  = { Text(stringResource(R.string.medical_card_delete_dialog_text, docDisplayName), style = EvaType.bodyText) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteDocument(doc.documentId); deleteConfirmDoc = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmDoc = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showUploadDialog) {
        UploadDocumentDialog(
            onDismiss = { showUploadDialog = false },
            onUpload  = { file, cat, desc -> showUploadDialog = false; viewModel.uploadDocument(file, cat, desc) }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_menu_medical_card), style = EvaType.cardTitle) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { viewModel.load(isRefresh = true) },
            modifier     = Modifier.padding(padding).fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FilledTonalButton(
                    onClick  = { showUploadDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape    = RoundedCornerShape(12.dp),
                    enabled  = !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_upload_document))
                    }
                }

                when {
                    isLoading -> LazyColumn(userScrollEnabled = false) {
                        items(4) { MedCardItemSkeleton() }
                    }
                    documents.isEmpty() -> MedCardEmpty(
                        Icons.Default.Description,
                        stringResource(R.string.documents_empty)
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        itemsIndexed(documents, key = { _, doc -> doc.documentId }) { index, doc ->
                            AnimatedListItem(index = index) {
                                DocumentCard(
                                    doc         = doc,
                                    imageLoader = authImageLoader,
                                    authToken   = authToken,
                                    onTap       = {
                                        when (doc.fileType) {
                                            "image" -> galleryStartDoc = doc
                                            else    -> viewModel.downloadAndOpen(context, doc)
                                        }
                                    },
                                    onMenuClick = { contextMenuDoc = doc }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: String?): String = when (category) {
    "analysis"     -> stringResource(R.string.document_category_analysis)
    "prescription" -> stringResource(R.string.document_category_prescription)
    "xray"         -> stringResource(R.string.document_category_xray)
    else           -> stringResource(R.string.document_category_other)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGallery(
    images: List<DocumentResponse>,
    initialIndex: Int,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    val current = images.getOrNull(pagerState.currentPage)

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black)
        ) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val doc = images[page]
                val url = remember(doc.documentId) {
                    BuildConfig.BASE_URL.substringBefore("/api/") + doc.downloadUrl
                }
                AsyncImage(
                    model              = url,
                    contentDescription = null,
                    imageLoader        = imageLoader,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
            }

            current?.let { doc ->
                val catLabel = categoryLabel(doc.category)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        doc.description?.takeIf { it.isNotBlank() } ?: catLabel,
                        style = EvaType.cardTitle, color = Color.White
                    )
                    Text(
                        "${pagerState.currentPage + 1} / ${images.size}  ·  ${formatDate(doc.createdAt)}",
                        style = EvaType.cardMeta, color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            IconButton(
                onClick  = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun DocumentCard(
    doc: DocumentResponse,
    imageLoader: ImageLoader,
    authToken: String?,
    onTap: () -> Unit,
    onMenuClick: () -> Unit
) {
    val isPdf   = doc.fileType == "pdf"
    val isImage = doc.fileType == "image"

    val iconColor = when {
        isPdf   -> MaterialTheme.colorScheme.error
        isImage -> MaterialTheme.colorScheme.primary
        else    -> MaterialTheme.colorScheme.secondary
    }
    val icon = when {
        isPdf   -> Icons.Default.PictureAsPdf
        isImage -> Icons.Default.Image
        else    -> Icons.Default.Description
    }
    val catLabel = categoryLabel(doc.category)
    val fileTypeLabel = when {
        isPdf   -> "PDF"
        isImage -> doc.fileName.substringAfterLast('.', "").uppercase()
            .ifBlank { stringResource(R.string.document_category_other) }
        else    -> stringResource(R.string.file_type_other)
    }
    val formattedSize = when {
        doc.fileSize < 1024        -> stringResource(R.string.file_size_bytes, doc.fileSize)
        doc.fileSize < 1024 * 1024 -> stringResource(R.string.file_size_kb, doc.fileSize / 1024)
        else                        -> stringResource(R.string.file_size_mb, doc.fileSize / (1024 * 1024))
    }
    val fullImageUrl = remember(doc.documentId) {
        BuildConfig.BASE_URL.substringBefore("/api/") + doc.downloadUrl
    }

    Card(
        onClick   = onTap,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(iconColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(28.dp))
                if (isImage && authToken != null) {
                    AsyncImage(
                        model              = fullImageUrl,
                        contentDescription = null,
                        imageLoader        = imageLoader,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 0.dp)
            ) {
                Text(catLabel, style = EvaType.cardTitle)
                if (!doc.description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(doc.description,
                        style    = EvaType.cardMeta,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fileTypeLabel, style = EvaType.cardMeta, color = iconColor)
                    Text("  ·  ", style = EvaType.cardMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formattedSize, style = EvaType.cardMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(2.dp))
                Text(formatDate(doc.createdAt), style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(
                onClick  = onMenuClick,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Default.MoreVert, null,
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDocumentDialog(
    doc: DocumentResponse,
    onSave: (description: String, category: String) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf(doc.description ?: "") }
    var category    by remember { mutableStateOf(doc.category) }

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
            Text(stringResource(R.string.doc_edit_title), style = EvaType.sheetTitle)
            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.InsertDriveFile, null,
                    modifier = Modifier.size(16.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    doc.fileName,
                    style    = EvaType.cardMeta,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(stringResource(R.string.upload_type_label),
                style = EvaType.cardMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onClick  = { onSave(description, category) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(50)
            ) {
                Text(stringResource(R.string.btn_save))
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
        uri?.let {
            val displayName = context.contentResolver.query(
                it, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            pickedUri = it
            fileName = displayName ?: it.lastPathSegment ?: "document"
        }
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
                style = EvaType.cardMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
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
            Icon(icon, null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = EvaType.cardMeta, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DocMenuAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, style = EvaType.bodyText, color = tint)
        }
    }
}
