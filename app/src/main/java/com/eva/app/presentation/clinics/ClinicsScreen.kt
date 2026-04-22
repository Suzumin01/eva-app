package com.eva.app.presentation.clinics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.api.ClinicResponse
import com.eva.app.data.api.SpecializationResponse
import com.eva.app.data.repository.SpecializationRepository
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClinicsViewModel @Inject constructor(
    private val clinicRepository: com.eva.app.data.repository.ClinicRepository
) : ViewModel() {
    private val _clinics   = MutableStateFlow<List<ClinicResponse>>(emptyList())
    val clinics = _clinics.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = clinicRepository.getClinics()) {
                is Resource.Success -> _clinics.value = r.data
                else -> {}
            }
            _isLoading.value = false
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
    val clinics   by viewModel.clinics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Клиники") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary))
    }) { padding ->
        if (isLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(clinics) { clinic -> ClinicCard(clinic = clinic, onClick = { onClinicClick(clinic.clinicId) }) }
        }
    }
}

@Composable
fun ClinicCard(clinic: ClinicResponse, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocalHospital, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(clinic.clinicName, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text(clinic.address, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                clinic.phone?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(2.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Рейтинг и количество врачей
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    clinic.rating?.let { rating ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp),
                                tint = androidx.compose.ui.graphics.Color(0xFFFFA000))
                            Spacer(Modifier.width(2.dp))
                            Text(rating,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (clinic.doctorsCount > 0) {
                        Text("${clinic.doctorsCount} врачей",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class SpecItem(val id: Int, val name: String, val desc: String, val icon: ImageVector)

/** Иконки хранятся здесь (Compose-зависимость), данные берём из API или из локального fallback */
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
        TopAppBar(title = { Text("Специализации") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary))
    }) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(specs) { s ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onSpecClick(s.id) },
                        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(s.icon, null, tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.SemiBold)
                                Text(s.desc, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}