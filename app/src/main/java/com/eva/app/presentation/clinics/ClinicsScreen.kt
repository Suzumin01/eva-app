package com.eva.app.presentation.clinics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import coil.compose.SubcomposeAsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.api.ClinicResponse
import com.eva.app.data.api.SpecializationResponse
import com.eva.app.data.repository.SpecializationRepository
import com.eva.app.presentation.components.AnimatedListItem
import com.eva.app.presentation.components.ClinicCardSkeleton
import com.eva.app.presentation.components.IconCircle
import com.eva.app.presentation.components.SpecCardSkeleton
import com.eva.app.util.Resource
import com.eva.app.presentation.components.EvaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClinicsViewModel @Inject constructor(
    private val clinicRepository: com.eva.app.data.repository.ClinicRepository
) : ViewModel() {
    private val _clinics      = MutableStateFlow<List<ClinicResponse>>(emptyList())
    val clinics = _clinics.asStateFlow()
    private val _isLoading    = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _loadError    = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    init { load() }

    fun load(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _isRefreshing.value = true else _isLoading.value = true
            when (val r = clinicRepository.getClinics()) {
                is Resource.Success -> _clinics.value = r.data
                is Resource.Error   -> _loadError.value = r.message ?: "Ошибка загрузки"
                else -> {}
            }
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun getById(id: Int) = _clinics.value.find { it.clinicId == id }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicsScreen(
    onBack: () -> Unit,
    onClinicClick: (Int) -> Unit,
    viewModel: ClinicsViewModel = hiltViewModel()
) {
    val clinics      by viewModel.clinics.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val loadError    by viewModel.loadError.collectAsState()
    val snackbar     = remember { SnackbarHostState() }

    LaunchedEffect(loadError) {
        loadError?.let { snackbar.showSnackbar(it) }
    }

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
        when {
            isLoading -> LazyColumn(
                modifier          = Modifier.padding(padding).fillMaxSize(),
                userScrollEnabled = false
            ) {
                items(6) { ClinicCardSkeleton() }
            }
            else -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh    = { viewModel.load(isRefresh = true) },
                modifier     = Modifier.padding(padding).fillMaxSize()
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(clinics, key = { _, c -> c.clinicId }) { index, clinic ->
                        AnimatedListItem(index = index) {
                            ClinicCard(clinic = clinic, onClick = { onClinicClick(clinic.clinicId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClinicCard(clinic: ClinicResponse, onClick: () -> Unit) {
    Card(
        onClick    = onClick,
        modifier   = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape      = RoundedCornerShape(16.dp),
        elevation  = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors     = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClinicAvatar(logoUrl = clinic.logoUrl, size = 80.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    clinic.clinicName,
                    style = EvaType.cardTitle
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null,
                        modifier = Modifier.size(13.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        clinic.address,
                        style    = EvaType.cardMeta,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                clinic.rating?.let { rating ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null,
                            modifier = Modifier.size(13.dp),
                            tint     = androidx.compose.ui.graphics.Color(0xFFFFC107))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            rating,
                            style = EvaType.cardMeta
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClinicAvatar(logoUrl: String?, size: androidx.compose.ui.unit.Dp = 80.dp) {
    val base = com.eva.app.BuildConfig.BASE_URL.trimEnd('/').removeSuffix("/api/v1")
    Box(
        modifier         = Modifier
            .size(size)
            .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl != null) {
            SubcomposeAsyncImage(
                model   = "$base$logoUrl",
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                loading = { Icon(Icons.Default.LocalHospital, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size((size.value * 0.45f).dp)) },
                error   = { Icon(Icons.Default.LocalHospital, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size((size.value * 0.45f).dp)) }
            )
        } else {
            Icon(Icons.Default.LocalHospital, null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size((size.value * 0.45f).dp))
        }
    }
}

data class SpecItem(val id: Int, val name: String, val desc: String, val icon: ImageVector)

private val specIcons = mapOf(
    1 to Icons.Default.MedicalServices,
    2 to Icons.Default.Favorite,
    3 to Icons.Default.Psychology,
    4 to Icons.Default.AccessibilityNew,
    5 to Icons.Default.SelfImprovement,
    6 to Icons.Default.Hearing,
    7 to Icons.Default.ChildCare,
    8 to Icons.Default.Face
)

@HiltViewModel
class SpecializationsViewModel @Inject constructor(
    private val specializationRepository: SpecializationRepository
) : ViewModel() {
    private val fallback = com.eva.app.util.Specializations.all.map { spec ->
        SpecItem(spec.id, spec.name, spec.description, specIcons[spec.id] ?: Icons.Default.MedicalServices)
    }
    private val _specs = MutableStateFlow(fallback)
    val specs = _specs.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = specializationRepository.getSpecializations()) {
                is Resource.Success -> _specs.value = result.data.map { spec ->
                    SpecItem(spec.specializationId, spec.name, spec.description ?: "",
                        specIcons[spec.specializationId] ?: Icons.Default.MedicalServices)
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecializationsScreen(onBack: () -> Unit, onSpecClick: (Int) -> Unit) {
    val vm: SpecializationsViewModel = hiltViewModel()
    val specs     by vm.specs.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
    }) { padding ->
        if (isLoading) {
            LazyColumn(
                modifier          = Modifier.padding(padding).fillMaxSize(),
                userScrollEnabled = false
            ) {
                items(8) { SpecCardSkeleton() }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                itemsIndexed(specs, key = { _, s -> s.id }) { index, s ->
                    AnimatedListItem(index = index) {
                        Card(
                            onClick   = { onSpecClick(s.id) },
                            modifier  = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape     = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconCircle(s.icon, size = 48.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.name,
                                        style = EvaType.cardTitle)
                                    if (s.desc.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(s.desc,
                                            style    = EvaType.cardMeta,
                                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
