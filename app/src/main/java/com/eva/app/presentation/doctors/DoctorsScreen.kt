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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.api.DoctorResponse
import com.eva.app.data.repository.DoctorRepository
import com.eva.app.data.repository.SpecializationRepository
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 20

@HiltViewModel
class DoctorsViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val specializationRepository: SpecializationRepository
) : ViewModel() {
    private val _doctors       = MutableStateFlow<List<DoctorResponse>>(emptyList())
    val doctors = _doctors.asStateFlow()
    private val _isLoading     = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isRefreshing  = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _hasMore       = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()
    private val _error         = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _searchQuery   = MutableStateFlow("")
    val searchQuery  = _searchQuery.asStateFlow()
    private val _selectedSpec  = MutableStateFlow<Int?>(null)
    val selectedSpec = _selectedSpec.asStateFlow()
    private val _selectedClinicId   = MutableStateFlow<Int?>(null)
    val selectedClinicId = _selectedClinicId.asStateFlow()
    private val _selectedClinicName = MutableStateFlow<String?>(null)
    val selectedClinicName = _selectedClinicName.asStateFlow()

    private val _specializations = MutableStateFlow(listOf<Pair<Int?, String>>(null to "Все"))
    val specializations = _specializations.asStateFlow()

    private var currentOffset = 0L

    init {
        viewModelScope.launch {
            when (val result = specializationRepository.getSpecializations()) {
                is Resource.Success -> {
                    _specializations.value = listOf(null to "Все") +
                        result.data.map { it.specializationId to it.name }
                }
                else -> {}
            }
        }
    }

    fun init(
        preselectedSpecId: Int?,
        preselectedClinicId: Int? = null,
        clinicName: String? = null
    ) {
        if (preselectedSpecId != null && preselectedSpecId != -1)
            _selectedSpec.value = preselectedSpecId
        if (preselectedClinicId != null && preselectedClinicId != -1) {
            _selectedClinicId.value   = preselectedClinicId
            _selectedClinicName.value = clinicName
        }
        setupSearch()
    }

    fun setSearch(query: String) { _searchQuery.value = query }
    fun setSpec(specId: Int?) { _selectedSpec.value = specId }
    fun clearClinicFilter() {
        _selectedClinicId.value   = null
        _selectedClinicName.value = null
    }

    @OptIn(FlowPreview::class)
    fun setupSearch() {
        viewModelScope.launch {
            combine(
                _searchQuery.debounce(400),
                _selectedSpec,
                _selectedClinicId
            ) { q, s, c -> Triple(q, s, c) }
                .collect { (q, s, c) ->
                    loadDoctors(search = q.ifBlank { null }, specId = s, clinicId = c)
                }
        }
    }

    fun loadDoctors(
        search: String? = null,
        specId: Int? = null,
        clinicId: Int? = null,
        isRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            if (isRefresh) _isRefreshing.value = true else _isLoading.value = true
            _error.value = null
            currentOffset = 0L
            _hasMore.value = true
            when (val r = doctorRepository.getDoctors(
                specId = specId, clinicId = clinicId,
                search = search, limit = PAGE_SIZE, offset = 0
            )) {
                is Resource.Success -> {
                    _doctors.value = r.data.doctors
                    _hasMore.value = r.data.doctors.size >= PAGE_SIZE
                }
                is Resource.Error -> _error.value = com.eva.app.util.ErrorMapper.map(r.message ?: "")
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
            val q      = _searchQuery.value.ifBlank { null }
            val spec   = _selectedSpec.value
            val clinic = _selectedClinicId.value
            when (val r = doctorRepository.getDoctors(
                specId = spec, clinicId = clinic,
                search = q, limit = PAGE_SIZE, offset = currentOffset
            )) {
                is Resource.Success -> {
                    _doctors.value = _doctors.value + r.data.doctors
                    _hasMore.value = r.data.doctors.size >= PAGE_SIZE
                }
                else -> {}
            }
            _isLoadingMore.value = false
        }
    }

    fun clearError() { _error.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorsScreen(
    initialSpecId: Int?,
    initialClinicId: Int? = null,
    initialClinicName: String? = null,
    onBack: () -> Unit,
    onDoctor: (Int) -> Unit,
    viewModel: DoctorsViewModel = hiltViewModel()
) {
    val doctors            by viewModel.doctors.collectAsState()
    val isLoading          by viewModel.isLoading.collectAsState()
    val isRefreshing       by viewModel.isRefreshing.collectAsState()
    val isLoadingMore      by viewModel.isLoadingMore.collectAsState()
    val hasMore            by viewModel.hasMore.collectAsState()
    val error              by viewModel.error.collectAsState()
    val searchQuery        by viewModel.searchQuery.collectAsState()
    val selectedSpec       by viewModel.selectedSpec.collectAsState()
    val selectedClinicId   by viewModel.selectedClinicId.collectAsState()
    val selectedClinicName by viewModel.selectedClinicName.collectAsState()
    val specializations    by viewModel.specializations.collectAsState()
    val listState         = rememberLazyListState()
    val snackbar          = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.init(initialSpecId, initialClinicId, initialClinicName) }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }

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
                title = { Text(stringResource(R.string.doctors_screen_title)) },
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

            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { viewModel.setSearch(it) },
                placeholder   = { Text(stringResource(R.string.doctors_search_hint)) },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                trailingIcon  = {
                    if (searchQuery.isNotEmpty())
                        IconButton(onClick = { viewModel.setSearch("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                },
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape     = RoundedCornerShape(14.dp),
                singleLine = true
            )

            if (selectedClinicId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = true,
                        onClick  = {},
                        label    = {
                            Text(selectedClinicName
                                ?: stringResource(R.string.doctor_clinic_fallback, selectedClinicId!!))
                        },
                        leadingIcon = {
                            Icon(Icons.Default.LocalHospital, null, Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick  = { viewModel.clearClinicFilter() },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close,
                                    stringResource(R.string.doctors_filter_remove),
                                    Modifier.size(14.dp))
                            }
                        }
                    )
                }
            }

            var specExpanded by remember { mutableStateOf(false) }
            val allLabel = stringResource(R.string.doctors_filter_all)
            val selectedSpecName = if (selectedSpec == null) allLabel
                else specializations.firstOrNull { it.first == selectedSpec }?.second
                    ?: stringResource(R.string.doctors_filter_all_specs)
            ExposedDropdownMenuBox(
                expanded  = specExpanded,
                onExpandedChange = { specExpanded = !specExpanded },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value         = selectedSpecName,
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text(stringResource(R.string.doctors_filter_spec_label)) },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(specExpanded) },
                    modifier      = Modifier.menuAnchor().fillMaxWidth(),
                    shape         = RoundedCornerShape(14.dp),
                    colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = specExpanded,
                    onDismissRequest = { specExpanded = false }
                ) {
                    specializations.forEach { (id, name) ->
                        DropdownMenuItem(
                            text    = { Text(if (id == null) allLabel else name) },
                            onClick = {
                                viewModel.setSpec(id)
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
                    onRefresh    = {
                        viewModel.loadDoctors(
                            search   = searchQuery.ifBlank { null },
                            specId   = selectedSpec,
                            clinicId = selectedClinicId,
                            isRefresh = true
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (doctors.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.doctors_empty),
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
                                    Text(stringResource(R.string.doctors_all_loaded),
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
                        Icon(Icons.Default.Star, null,
                            tint = androidx.compose.ui.graphics.Color(0xFFFFC107),
                            modifier = Modifier.size(12.dp))
                        Text(" $it", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(8.dp))
                    }
                    doctor.experienceYears?.let {
                        Text(pluralStringResource(R.plurals.experience_years, it, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}
