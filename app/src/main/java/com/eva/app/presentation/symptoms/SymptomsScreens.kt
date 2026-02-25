package com.eva.app.presentation.symptoms

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.api.AnalyzeSymptomsResponse
import com.eva.app.data.api.SymptomsHistoryResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.SymptomsRepository
import com.eva.app.util.ErrorMapper
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.eva.app.util.formatDate
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

    val userId = tokenManager.userId

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
        }
    }

    fun analyze(text: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _analyzing.value = true
            _analyzeError.value = null
            _result.value = null
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

@Composable
fun SymptomsScreen(
    onNewRequest: () -> Unit,
    viewModel: SymptomsViewModel = hiltViewModel()
) {
    val history   by viewModel.history.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userId    by viewModel.userId.collectAsState(initial = null)
    var selected  by remember { mutableStateOf<SymptomsHistoryResponse?>(null) }

    LaunchedEffect(userId) {
        viewModel.clearHistory()
        if (userId != null) viewModel.loadHistory()
    }

    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Результат анализа", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Симптомы:", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(item.symptomsText, style = MaterialTheme.typography.bodySmall)
                    item.aiResponse?.let { ai ->
                        Spacer(Modifier.height(12.dp))
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
                        Spacer(Modifier.height(10.dp))
                        Text("Оценка:", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(ai.diagnosis, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        Text("Рекомендации:", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(ai.recommendations, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(formatDate(item.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("Закрыть") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            history.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.Psychology, null, modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("Нет анализов", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium)
                    Text("Нажмите «Новый запрос» чтобы описать симптомы",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp)
            ) {
                items(history) { item ->
                    HistoryItem(item = item, onClick = { selected = item })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick        = onNewRequest,
            icon           = { Icon(Icons.Default.Add, null) },
            text           = { Text("Новый запрос") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = Color.White,
            modifier       = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        )
    }
}

@Composable
fun HistoryItem(item: SymptomsHistoryResponse, onClick: () -> Unit) {
    val urgency = item.aiResponse?.urgency
    val urgencyColor = when (urgency) {
        "emergency", "urgent" -> Color(0xFFE53935)
        "normal"              -> Color(0xFF1565C0)
        else                  -> Color(0xFF2E7D32)
    }
    val urgencyLabel = when (urgency) {
        "emergency" -> "🚨 Срочно"
        "urgent"    -> "⚠️ Срочно"
        "normal"    -> "📋 Норма"
        "low"       -> "✅ Несрочно"
        else        -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(46.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("AI-анализ", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Text(formatDate(item.createdAt), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(item.symptomsText, maxLines = 1, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.aiResponse?.let { ai ->
                Spacer(Modifier.height(3.dp))
                Text(ai.diagnosis, maxLines = 1, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        urgencyLabel?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = urgencyColor, fontWeight = FontWeight.SemiBold)
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
                title = { Text("Описание симптомов") },
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
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)) {

            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(14.dp)) {
                Row(modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("AI проанализирует симптомы и даст предварительную оценку. Это не заменяет консультацию врача.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value       = text,
                onValueChange = { text = it },
                label       = { Text("Опишите симптомы подробно") },
                placeholder = { Text("Например: Болит голова в висках, температура 37.8, кашель сухой 3 дня, слабость...") },
                minLines    = 7,
                maxLines    = 14,
                modifier    = Modifier.fillMaxWidth(),
                shape       = RoundedCornerShape(14.dp),
                supportingText = {
                    val hint = if (text.length < 20) " · минимум 20 символов" else ""
                    Text("${text.length} / 5000$hint",
                        color = if (text.length < 20) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick  = { viewModel.analyze(text) { onResult() } },
                enabled  = text.length >= 20 && !analyzing,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                if (analyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Анализируем...")
                } else {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Анализировать симптомы", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomsResultScreen(
    onBack: () -> Unit,
    viewModel: SymptomsViewModel = hiltViewModel()
) {
    val result by viewModel.result.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Результат анализа") },
                navigationIcon = {
                    // Эта стрелка — единственная кнопка назад (в историю)
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        result?.let { r ->
            val urgencyColor = when (r.urgency) {
                "emergency", "urgent" -> Color(0xFFB71C1C)
                "normal"              -> Color(0xFF1565C0)
                else                  -> Color(0xFF2E7D32)
            }
            val urgencyText = when (r.urgency) {
                "emergency" -> "🚨 Экстренно — немедленно вызовите скорую помощь"
                "urgent"    -> "⚠️ Срочно — обратитесь к врачу сегодня"
                "normal"    -> "📋 Рекомендуется консультация врача"
                else        -> "✅ Несрочно — запись в плановом порядке"
            }

            LazyColumn(
                modifier      = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = urgencyColor.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(14.dp)) {
                        Row(modifier = Modifier.padding(14.dp)) {
                            Text(urgencyText, color = urgencyColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item { ResultCard("🔍 Предварительная оценка", r.diagnosis) }
                item { ResultCard("💊 Рекомендации", r.recommendations) }
                item {
                    Card(shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Точность модели", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(8.dp))
                            val pct = r.confidence.toFloatOrNull() ?: 0f
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier  = Modifier.fillMaxWidth().height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color      = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${(pct * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(14.dp)) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(r.disclaimer, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Результат недоступен", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ResultCard(title: String, content: String) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
        }
    }
}