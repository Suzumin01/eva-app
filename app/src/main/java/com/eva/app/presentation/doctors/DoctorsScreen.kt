package com.eva.app.presentation.doctors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.api.DoctorResponse
import com.eva.app.data.repository.DoctorRepository
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 20

@HiltViewModel
class DoctorsViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository
) : ViewModel() {
    private val _doctors      = MutableStateFlow<List<DoctorResponse>>(emptyList())
    val doctors = _doctors.asStateFlow()
    private val _isLoading    = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _hasMore      = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()
    private val _error        = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val searchQuery   = MutableStateFlow("")
    val selectedSpec  = MutableStateFlow<Int?>(null)

    val specializations = listOf(
        null to "Все", 1 to "Терапевт", 2 to "Кардиолог", 3 to "Невролог",
        4 to "Ортопед", 5 to "Психолог", 6 to "ЛОР", 7 to "Педиатр", 8 to "Дерматолог"
    )

    private var currentOffset = 0L

    fun init(preselectedSpecId: Int?) {
        if (preselectedSpecId != null && preselectedSpecId != -1)
            selectedSpec.value = preselectedSpecId
        setupSearch()
    }

    @OptIn(FlowPreview::class)
    fun setupSearch() {
        viewModelScope.launch {
            combine(searchQuery.debounce(400), selectedSpec) { q, s -> q to s }
                .collect { (q, s) -> loadDoctors(search = q.ifBlank { null }, specId = s) }
        }
    }

    fun loadDoctors(search: String? = null, specId: Int? = null, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _isRefreshing.value = true else _isLoading.value = true
            _error.value = null
            currentOffset = 0L
            _hasMore.value = true
            when (val r = doctorRepository.getDoctors(specId, search, limit = PAGE_SIZE, offset = 0)) {
                is Resource.Success -> {
                    _doctors.value = r.data.doctors
                    _hasMore.value = r.data.doctors.size >= PAGE_SIZE
                }
                is Resource.Error -> _error.value = friendlyError(r.message)
                else -> {}
            }
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            currentOffset += PAGE_SIZE
            val q    = searchQuery.value.ifBlank { null }
            val spec = selectedSpec.value
            when (val r = doctorRepository.getDoctors(spec, q, limit = PAGE_SIZE, offset = currentOffset)) {
                is Resource.Success -> {
                    val newItems = r.data.doctors
                    _doctors.value = _doctors.value + newItems
                    _hasMore.value = newItems.size >= PAGE_SIZE
                }
                else -> {}
            }
            _isLoadingMore.value = false
        }
    }

    fun clearError() { _error.value = null }

    private fun friendlyError(raw: String?): String = when {
        raw == null                        -> "Что-то пошло не так"
        raw.contains("Unable to resolve") -> "Нет подключения к интернету"
        raw.contains("timeout", true)     -> "Сервер не отвечает, попробуйте позже"
        else                               -> raw
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorsScreen(
    initialSpecId: Int?,
    onBack: () -> Unit,
    onDoctor: (Int) -> Unit,
    viewModel: DoctorsViewModel = hiltViewModel()
) {
    val doctors      by viewModel.doctors.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore      by viewModel.hasMore.collectAsState()
    val error        by viewModel.error.collectAsState()
    val searchQuery  by viewModel.searchQuery.collectAsState()
    val selectedSpec by viewModel.selectedSpec.collectAsState()
    val listState     = rememberLazyListState()
    val snackbar      = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.init(initialSpecId) }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }

    // Подгрузка при достижении конца списка
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 3 && !isLoadingMore && hasMore && doctors.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Врачи") },
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Строка поиска
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder   = { Text("Поиск по имени врача") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                trailingIcon  = {
                    if (searchQuery.isNotEmpty())
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                },
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape     = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // Фильтр специализаций — выпадающий список
            var specExpanded by remember { mutableStateOf(false) }
            val selectedSpecName = viewModel.specializations
                .firstOrNull { it.first == selectedSpec }?.second ?: "Все специализации"
            ExposedDropdownMenuBox(
                expanded  = specExpanded,
                onExpandedChange = { specExpanded = !specExpanded },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value         = selectedSpecName,
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Специализация") },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(specExpanded) },
                    modifier      = Modifier.menuAnchor().fillMaxWidth(),
                    shape         = RoundedCornerShape(14.dp),
                    colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = specExpanded,
                    onDismissRequest = { specExpanded = false }
                ) {
                    viewModel.specializations.forEach { (id, name) ->
                        DropdownMenuItem(
                            text    = { Text(name) },
                            onClick = {
                                viewModel.selectedSpec.value = id
                                viewModel.loadDoctors(search = searchQuery.ifBlank { null }, specId = id)
                                specExpanded = false
                            },
                            trailingIcon = if (selectedSpec == id) ({
                                Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }) else null
                        )
                    }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh    = { viewModel.loadDoctors(
                        search = searchQuery.ifBlank { null },
                        specId = selectedSpec, isRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (doctors.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Text("Врачи не найдены",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(doctors, key = { it.doctorId }) { doctor ->
                                DoctorCard(doctor = doctor, onClick = { onDoctor(doctor.doctorId) })
                            }
                            if (isLoadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                            if (!hasMore && doctors.size >= PAGE_SIZE) {
                                item {
                                    Text("Все врачи загружены",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                            .wrapContentWidth())
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
fun DoctorCard(doctor: DoctorResponse, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(doctor.fullName, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge)
                Text(doctor.specializationName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(doctor.clinicName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    doctor.rating?.let {
                        Icon(Icons.Default.Star, null, tint = androidx.compose.ui.graphics.Color(0xFFFFC107),
                            modifier = Modifier.size(12.dp))
                        Text(" $it", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(8.dp))
                    }
                    doctor.experienceYears?.let {
                        Text("$it лет опыта", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}