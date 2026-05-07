package com.eva.app.presentation.symptoms

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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.api.AnalyzeSymptomsResponse
import com.eva.app.data.api.SymptomsHistoryResponse
import com.eva.app.data.api.SymptomsQuotaResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.SymptomsRepository
import com.eva.app.presentation.components.AnimatedListItem
import com.eva.app.presentation.components.EvaGradients
import com.eva.app.presentation.components.EvaType
import com.eva.app.presentation.components.MedCardItemSkeleton
import com.eva.app.presentation.components.SectionHeader
import com.eva.app.presentation.components.StatusPill
import com.eva.app.presentation.components.urgencyColor
import com.eva.app.util.ErrorMapper
import com.eva.app.util.Resource
import com.eva.app.util.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SymptomsViewModel @Inject constructor(
    private val repository: SymptomsRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _history      = MutableStateFlow<List<SymptomsHistoryResponse>>(emptyList())
    val history = _history.asStateFlow()
    private val _isLoading    = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _result       = MutableStateFlow<AnalyzeSymptomsResponse?>(null)
    val result = _result.asStateFlow()
    private val _analyzing    = MutableStateFlow(false)
    val analyzing = _analyzing.asStateFlow()
    private val _analyzeError = MutableStateFlow<String?>(null)
    val analyzeError = _analyzeError.asStateFlow()
    private val _quota        = MutableStateFlow<SymptomsQuotaResponse?>(null)
    val quota = _quota.asStateFlow()

    val userId = tokenManager.userId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { loadHistory() }

    fun clearHistory() { _history.value = emptyList() }

    fun loadHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = repository.getHistory()) {
                is Resource.Success -> _history.value = r.data
                else -> {}
            }
            _isLoading.value = false
            loadQuota()
        }
    }

    private fun loadQuota() {
        viewModelScope.launch {
            when (val r = repository.getQuota()) {
                is Resource.Success -> _quota.value = r.data
                else -> {}
            }
        }
    }

    fun analyze(text: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _analyzing.value    = true
            _analyzeError.value = null
            _result.value       = null
            when (val r = repository.analyze(text)) {
                is Resource.Success -> { _result.value = r.data; loadHistory(); onDone() }
                is Resource.Error   -> _analyzeError.value = ErrorMapper.map(r.message)
                else -> {}
            }
            _analyzing.value = false
        }
    }

    fun clearError() { _analyzeError.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomsScreen(
    onNewRequest: () -> Unit,
    viewModel: SymptomsViewModel = hiltViewModel()
) {
    val history   by viewModel.history.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userId    by viewModel.userId.collectAsState(initial = null)
    val quota     by viewModel.quota.collectAsState()
    var selected  by remember { mutableStateOf<SymptomsHistoryResponse?>(null) }

    LaunchedEffect(userId) {
        viewModel.clearHistory()
        if (userId != null) viewModel.loadHistory()
    }

    selected?.let { item ->
        HistoryBottomSheet(item = item, onDismiss = { selected = null })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.symptoms_history_title),
                style = EvaType.screenTitle
            )
            quota?.let { q ->
                Text(
                    stringResource(R.string.symptoms_quota, q.used, q.limit),
                    style = EvaType.cardMeta,
                    color = if (q.remaining == 0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
        when {
            isLoading -> LazyColumn(
                modifier          = Modifier.fillMaxSize(),
                contentPadding    = PaddingValues(bottom = 100.dp),
                userScrollEnabled = false
            ) {
                items(6) { MedCardItemSkeleton() }
            }
            history.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(32.dp)
                ) {
                    Icon(Icons.Default.Psychology, null,
                        modifier = Modifier.size(72.dp),
                        tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.symptoms_empty_title),
                        style = EvaType.cardTitle)
                    Text(stringResource(R.string.symptoms_empty_hint),
                        style    = EvaType.cardMeta,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            else -> LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
            ) {
                itemsIndexed(history, key = { _, it -> it.requestId }) { index, item ->
                    AnimatedListItem(index = index) {
                        HistoryCard(item = item, onClick = { selected = item })
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick        = onNewRequest,
            icon           = { Icon(Icons.Default.Add, null) },
            text           = { Text(stringResource(R.string.symptoms_new_request_btn)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = Color.White,
            modifier       = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        )
        } // Box
    } // Column
}

@Composable
fun HistoryCard(item: SymptomsHistoryResponse, onClick: () -> Unit) {
    val urgency = item.aiResponse?.urgency
    val color   = urgencyColor(urgency)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(item: SymptomsHistoryResponse, onDismiss: () -> Unit) {
    val urgency    = item.aiResponse?.urgency
    val color      = urgencyColor(urgency)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val urgencyText = when (urgency) {
        "emergency" -> stringResource(R.string.urgency_emergency)
        "urgent"    -> stringResource(R.string.urgency_urgent)
        "normal"    -> stringResource(R.string.urgency_normal)
        else        -> stringResource(R.string.urgency_low)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
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
                ?: stringResource(R.string.symptoms_result_dialog_title)
            Text(sheetTitle,
                style     = EvaType.sheetTitle,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            StatusPill(urgencyText, color)

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionHeader(
                stringResource(R.string.symptoms_dialog_symptoms_label),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(item.symptomsText,
                style    = EvaType.bodyText,
                modifier = Modifier.fillMaxWidth())

            item.aiResponse?.let { ai ->
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

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

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text(formatDate(item.createdAt),
                style    = EvaType.cardMeta,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomsFormScreen(
    onBack: () -> Unit,
    onResult: () -> Unit,
    viewModel: SymptomsViewModel = hiltViewModel()
) {
    var text      by remember { mutableStateOf("") }
    val analyzing by viewModel.analyzing.collectAsState()
    val error     by viewModel.analyzeError.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.AutoAwesome, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).padding(top = 1.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.symptoms_form_disclaimer),
                    style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }

            Spacer(Modifier.height(20.dp))

            val minCharsHint = stringResource(R.string.symptoms_form_min_chars_hint)
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                label         = { Text(stringResource(R.string.symptoms_form_field_label)) },
                placeholder   = { Text(stringResource(R.string.symptoms_form_placeholder)) },
                minLines      = 7,
                maxLines      = 14,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(14.dp),
                supportingText = {
                    val hint = if (text.length < 20) minCharsHint else ""
                    Text("${text.length} / 5000$hint",
                        color = if (text.length < 20) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, null,
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(it,
                        color = MaterialTheme.colorScheme.error,
                        style = EvaType.cardMeta)
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick  = { viewModel.analyze(text) { onResult() } },
                enabled  = text.length >= 20 && !analyzing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(50)
            ) {
                if (analyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.symptoms_btn_analyzing))
                } else {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.symptoms_btn_analyze),
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomsResultScreen(
    onBack: () -> Unit,
    onFindDoctor: (specializationName: String) -> Unit = {},
    viewModel: SymptomsViewModel = hiltViewModel()
) {
    val result by viewModel.result.collectAsState()

    Scaffold(
        bottomBar = {
            result?.specializationName?.let { specName ->
                Surface(shadowElevation = 4.dp) {
                    Box(Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                    ) {
                        Button(
                            onClick  = { onFindDoctor(specName) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape    = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.symptoms_result_find_doctor_btn, specName),
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        result?.let { r ->
            val color = urgencyColor(r.urgency)
            val urgencyText = when (r.urgency) {
                "emergency" -> stringResource(R.string.symptoms_urgency_emergency_full)
                "urgent"    -> stringResource(R.string.symptoms_urgency_urgent_full)
                "normal"    -> stringResource(R.string.symptoms_urgency_normal_full)
                else        -> stringResource(R.string.symptoms_urgency_low_full)
            }
            val heroGradient = when (r.urgency) {
                "emergency", "urgent" -> EvaGradients.danger
                "normal"              -> EvaGradients.doctors
                else                  -> EvaGradients.ai
            }

            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding())
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                            .background(Brush.linearGradient(heroGradient))
                            .statusBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(urgencyText,
                            style     = EvaType.heroTitle,
                            color     = Color.White,
                            textAlign = TextAlign.Center)
                        r.specializationName?.let { specName ->
                            Spacer(Modifier.height(6.dp))
                            Text(specName,
                                style = EvaType.heroSub,
                                color = Color.White.copy(alpha = 0.85f))
                        }
                    }

                    Column(modifier = Modifier.padding(20.dp)) {
                        SectionHeader(stringResource(R.string.symptoms_result_diagnosis_title))
                        Spacer(Modifier.height(8.dp))
                        Text(r.diagnosis, style = EvaType.bodyText)

                        Spacer(Modifier.height(20.dp))
                        SectionHeader(stringResource(R.string.symptoms_result_recommendations_title))
                        Spacer(Modifier.height(8.dp))
                        Text(r.recommendations, style = EvaType.bodyText)

                        Spacer(Modifier.height(24.dp))
                        val pct = r.confidence.toFloatOrNull() ?: 0f
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.symptoms_result_accuracy_title),
                                style = EvaType.cardSub,
                                fontWeight = FontWeight.SemiBold)
                            Text("${(pct * 100).toInt()}%",
                                style      = EvaType.cardSub,
                                color      = color,
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress   = { pct },
                            modifier   = Modifier.fillMaxWidth().height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color      = color,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )

                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, null,
                                modifier = Modifier.size(14.dp).padding(top = 2.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(r.disclaimer,
                                style = EvaType.cardMeta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                IconButton(
                    onClick  = onBack,
                    modifier = Modifier.statusBarsPadding().padding(4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
            }
        } ?: Box(
            modifier         = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.symptoms_result_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
