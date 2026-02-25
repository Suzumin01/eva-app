package com.eva.app.presentation.doctors

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
import androidx.compose.ui.graphics.Color
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

@HiltViewModel
class DoctorsViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository
) : ViewModel() {
    private val _doctors   = MutableStateFlow<List<DoctorResponse>>(emptyList())
    val doctors = _doctors.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error     = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val searchQuery  = MutableStateFlow("")
    val selectedSpec = MutableStateFlow<Int?>(null)

    val specializations = listOf(
        null to "Все специализации",
        1 to "Терапевт", 2 to "Кардиолог", 3 to "Невролог",
        4 to "Ортопед", 5 to "Психолог", 6 to "ЛОР",
        7 to "Педиатр", 8 to "Дерматолог"
    )

    fun init(preselectedSpecId: Int?) {
        if (preselectedSpecId != null && preselectedSpecId != -1) {
            selectedSpec.value = preselectedSpecId
        }
        setupSearch()
    }

    @OptIn(FlowPreview::class)
    fun setupSearch() {
        viewModelScope.launch {
            combine(searchQuery.debounce(400), selectedSpec) { q, s -> q to s }
                .collect { (q, s) -> loadDoctors(search = q.ifBlank { null }, specId = s) }
        }
    }

    fun loadDoctors(search: String? = null, specId: Int? = null) {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            when (val r = doctorRepository.getDoctors(specId, search)) {
                is Resource.Success -> _doctors.value = r.data.doctors
                is Resource.Error   -> _error.value = r.message
                else -> {}
            }
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorsScreen(
    initialSpecId: Int?,
    onBack: () -> Unit,
    onDoctorClick: (Int) -> Unit,
    viewModel: DoctorsViewModel = hiltViewModel()
) {
    val doctors      by viewModel.doctors.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    val error        by viewModel.error.collectAsState()
    val searchQuery  by viewModel.searchQuery.collectAsState()
    val selectedSpec by viewModel.selectedSpec.collectAsState()
    var specExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.init(initialSpecId) }

    val selectedSpecName = viewModel.specializations.find { it.first == selectedSpec }?.second ?: "Все специализации"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Врачи") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Поиск
                OutlinedTextField(
                    value = searchQuery, onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Поиск врача по имени...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                // Dropdown специализации
                ExposedDropdownMenuBox(expanded = specExpanded, onExpandedChange = { specExpanded = it }) {
                    OutlinedTextField(
                        value = selectedSpecName, onValueChange = {},
                        readOnly = true,
                        leadingIcon = { Icon(Icons.Default.FilterList, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = specExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (selectedSpec != null) OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ) else OutlinedTextFieldDefaults.colors()
                    )
                    ExposedDropdownMenu(expanded = specExpanded, onDismissRequest = { specExpanded = false }) {
                        viewModel.specializations.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name, fontWeight = if (selectedSpec == id) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { viewModel.selectedSpec.value = id; specExpanded = false },
                                leadingIcon = if (selectedSpec == id) {{ Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.primary) }} else null
                            )
                        }
                    }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WifiOff, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadDoctors() }) { Text("Повторить") }
                    }
                }
                doctors.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Врачи не найдены", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(doctors) { doctor -> DoctorCard(doctor = doctor, onClick = { onDoctorClick(doctor.doctorId) }) }
                }
            }
        }
    }
}

@Composable
fun DoctorCard(doctor: DoctorResponse, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doctor.fullName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(doctor.specializationName, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(doctor.clinicName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (doctor.rating != null || doctor.experienceYears != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        doctor.rating?.let {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                            Text(" $it", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFC107))
                            Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        doctor.experienceYears?.let {
                            Text("$it лет опыта", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
