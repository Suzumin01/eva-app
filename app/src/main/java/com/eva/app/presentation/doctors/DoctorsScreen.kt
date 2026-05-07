package com.eva.app.presentation.doctors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import com.eva.app.presentation.components.AnimatedListItem
import com.eva.app.presentation.components.DoctorCardSkeleton
import com.eva.app.presentation.components.IconCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.api.DoctorResponse
import com.eva.app.data.repository.ClinicRepository
import com.eva.app.data.repository.DoctorRepository
import com.eva.app.data.repository.SpecializationRepository
import com.eva.app.util.Resource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eva.app.BuildConfig
import com.eva.app.presentation.components.EvaGradients
import com.eva.app.presentation.components.EvaType
import kotlin.math.abs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 20

@HiltViewModel
class DoctorsViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val specializationRepository: SpecializationRepository,
    private val clinicRepository: ClinicRepository
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
    private val _clinics = MutableStateFlow(listOf<Pair<Int?, String>>(null to "Все клиники"))
    val clinics = _clinics.asStateFlow()

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
        viewModelScope.launch {
            when (val result = clinicRepository.getClinics()) {
                is Resource.Success -> {
                    _clinics.value = listOf(null to "Все клиники") +
                        result.data.map { it.clinicId to it.clinicName }
                }
                else -> {}
            }
        }
    }

    private var initialized = false

    fun init(
        preselectedSpecId: Int?,
        preselectedClinicId: Int? = null,
        clinicName: String? = null
    ) {
        if (initialized) return
        initialized = true
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
    fun setClinic(clinicId: Int?) {
        _selectedClinicId.value   = clinicId
        _selectedClinicName.value = _clinics.value.firstOrNull { it.first == clinicId }?.second
    }
    fun clearClinicFilter() { setClinic(null) }

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
    val clinics            by viewModel.clinics.collectAsState()
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
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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

            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var specExpanded by remember { mutableStateOf(false) }
                val allLabel = stringResource(R.string.doctors_filter_all)
                val selectedSpecName = if (selectedSpec == null) allLabel
                    else specializations.firstOrNull { it.first == selectedSpec }?.second
                        ?: stringResource(R.string.doctors_filter_all_specs)
                ExposedDropdownMenuBox(
                    expanded         = specExpanded,
                    onExpandedChange = { specExpanded = !specExpanded },
                    modifier         = Modifier.weight(1f)
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
                        expanded         = specExpanded,
                        onDismissRequest = { specExpanded = false }
                    ) {
                        specializations.forEach { (id, name) ->
                            DropdownMenuItem(
                                text    = { Text(if (id == null) allLabel else name) },
                                onClick = { viewModel.setSpec(id); specExpanded = false },
                                trailingIcon = if (selectedSpec == id) ({
                                    Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }) else null
                            )
                        }
                    }
                }

                var clinicExpanded by remember { mutableStateOf(false) }
                val clinicAllLabel = stringResource(R.string.doctors_filter_clinic_all)
                val selectedClinicLabel = if (selectedClinicId == null) clinicAllLabel
                    else clinics.firstOrNull { it.first == selectedClinicId }?.second
                        ?: selectedClinicName ?: clinicAllLabel
                ExposedDropdownMenuBox(
                    expanded         = clinicExpanded,
                    onExpandedChange = { clinicExpanded = !clinicExpanded },
                    modifier         = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value         = selectedClinicLabel,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text(stringResource(R.string.doctors_filter_clinic_label)) },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(clinicExpanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                        shape         = RoundedCornerShape(14.dp),
                        colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded         = clinicExpanded,
                        onDismissRequest = { clinicExpanded = false }
                    ) {
                        clinics.forEach { (id, name) ->
                            DropdownMenuItem(
                                text    = { Text(if (id == null) clinicAllLabel else name) },
                                onClick = { viewModel.setClinic(id); clinicExpanded = false },
                                trailingIcon = if (selectedClinicId == id) ({
                                    Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }) else null
                            )
                        }
                    }
                }
            }

            when {
                isLoading -> LazyColumn(
                    modifier          = Modifier.fillMaxSize(),
                    userScrollEnabled = false
                ) {
                    items(6) { DoctorCardSkeleton() }
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
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(doctors, key = { _, d -> d.doctorId }) { index, doctor ->
                                AnimatedListItem(index = index) {
                                    DoctorCard(doctor = doctor, onClick = { onDoctor(doctor.doctorId) })
                                }
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
                                        style = EvaType.cardMeta,
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
    val context = LocalContext.current
    val avatarColors = listOf(
        Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A),
        Color(0xFF00838F), Color(0xFFE65100), Color(0xFF37474F)
    )
    val bgColor  = avatarColors[abs(doctor.fullName.hashCode()) % avatarColors.size]
    val initials = doctor.fullName.trim().split(" ")
        .filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }
    val photoUrl = doctor.photoUrl?.let {
        BuildConfig.BASE_URL.substringBefore("/api/") + it
    }

    Card(
        onClick    = onClick,
        modifier   = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape      = RoundedCornerShape(16.dp),
        elevation  = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors     = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl != null) {
                    AsyncImage(
                        model        = ImageRequest.Builder(context)
                            .data(photoUrl).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier     = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        initials,
                        color      = Color.White,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = doctor.fullName,
                    style    = EvaType.cardTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text  = doctor.specializationName,
                    style = EvaType.cardSub,
                    color = MaterialTheme.colorScheme.primary
                )
                doctor.rating?.toDoubleOrNull()?.let {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null,
                            tint     = Color(0xFFFFC107),
                            modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text  = doctor.rating,
                            style = EvaType.cardMeta
                        )
                    }
                }
            }
        }
    }
}
