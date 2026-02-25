package com.eva.app.presentation.doctors

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.api.DoctorResponse
import com.eva.app.data.api.ReviewResponse
import com.eva.app.data.repository.DoctorRepository
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DoctorDetailViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository
) : ViewModel() {
    private val _doctor    = MutableStateFlow<DoctorResponse?>(null)
    val doctor = _doctor.asStateFlow()
    private val _reviews   = MutableStateFlow<List<ReviewResponse>>(emptyList())
    val reviews = _reviews.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun load(doctorId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = doctorRepository.getDoctorById(doctorId)) { is Resource.Success -> _doctor.value = r.data; else -> {} }
            when (val r = doctorRepository.getDoctorReviews(doctorId)) { is Resource.Success -> _reviews.value = r.data; else -> {} }
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDetailScreen(
    doctorId: Int, onBack: () -> Unit, onBook: (Int) -> Unit,
    viewModel: DoctorDetailViewModel = hiltViewModel()
) {
    val doctor    by viewModel.doctor.collectAsState()
    val reviews   by viewModel.reviews.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    LaunchedEffect(doctorId) { viewModel.load(doctorId) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(doctor?.fullName ?: "Врач") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary))
        },
        bottomBar = {
            doctor?.let {
                Surface(shadowElevation = 8.dp) {
                    Button(onClick = { onBook(doctorId) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                        shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.CalendarMonth, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Записаться на приём", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        doctor?.let { doc ->
            LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF1565C0), Color(0xFF42A5F5))))) {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(80.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(doc.fullName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text(doc.specializationName, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(3.dp)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            StatBubble(Icons.Default.MedicalServices, "${doc.experienceYears ?: "—"}", "лет опыта", Color(0xFF1565C0))
                            StatBubble(Icons.Default.Star, doc.rating ?: "—", "рейтинг", Color(0xFFF57F17))
                            StatBubble(Icons.Default.RateReview, "${doc.reviewsCount}", "отзывов", Color(0xFF2E7D32))
                        }
                    }
                }
                item {
                    InfoSection("Клиника") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(doc.clinicName, fontWeight = FontWeight.SemiBold)
                                Text(doc.clinicAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (!doc.bio.isNullOrBlank()) {
                    item { InfoSection("О враче") { Text(doc.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)) } }
                }
                if (reviews.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)); Text("Отзывы (${reviews.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp)) }
                    items(reviews.take(5)) { review ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(review.userFullName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Row { repeat(review.rating) { Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp)) } }
                                }
                                review.comment?.let { Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatBubble(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp)); content()
        }
    }
}
