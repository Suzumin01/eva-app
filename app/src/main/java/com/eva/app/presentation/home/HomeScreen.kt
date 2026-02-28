package com.eva.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.api.DoctorResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.DoctorRepository
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val doctorRepository: DoctorRepository
) : ViewModel() {
    val userName: StateFlow<String> = tokenManager.userName
        .map { it ?: "Пользователь" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Пользователь")

    private val _favDoctors = MutableStateFlow<List<DoctorResponse>>(emptyList())
    val favDoctors = _favDoctors.asStateFlow()

    init {
        // Следим за списком избранных и загружаем данные врачей
        viewModelScope.launch {
            tokenManager.favoriteDoctors.collect { json ->
                val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
                val ids = (0 until arr.length()).map { arr.getInt(it) }
                if (ids.isEmpty()) { _favDoctors.value = emptyList(); return@collect }
                // Загружаем каждого врача (параллельно через DoctorsScreen кэш нет, делаем запросы)
                val loaded = ids.mapNotNull { id ->
                    (doctorRepository.getDoctorById(id) as? Resource.Success)?.data
                }
                _favDoctors.value = loaded
            }
        }
    }
}

data class HomeItem(
    val icon: ImageVector, val title: String, val subtitle: String,
    val gradient: List<Color>, val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    onDoctors: () -> Unit,
    onClinics: () -> Unit,
    onSpecializations: () -> Unit,
    onSymptoms: () -> Unit,
    onAppointments: () -> Unit,
    onDoctorClick: (Int) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val userName   by viewModel.userName.collectAsState()
    val favDoctors by viewModel.favDoctors.collectAsState()

    val items = listOf(
        HomeItem(Icons.Default.Search, "Поиск врача", "Специалисты по вашему запросу",
            listOf(Color(0xFF1565C0), Color(0xFF42A5F5)), onDoctors),
        HomeItem(Icons.Default.LocalHospital, "Клиники", "Медицинские учреждения рядом",
            listOf(Color(0xFF00838F), Color(0xFF4DD0E1)), onClinics),
        HomeItem(Icons.Default.MedicalServices, "Специализации", "Выберите направление врача",
            listOf(Color(0xFF6A1B9A), Color(0xFFAB47BC)), onSpecializations),
        HomeItem(Icons.Default.Psychology, "AI-анализ симптомов", "Предварительная оценка состояния",
            listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)), onSymptoms),
        HomeItem(Icons.Default.CalendarMonth, "Мои записи", "Предстоящие и прошедшие приёмы",
            listOf(Color(0xFFE65100), Color(0xFFFF8A65)), onAppointments),
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Хедер с избранными внутри градиента
        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(
                        Color(0xFF1565C0), Color(0xFF42A5F5))))
            ) {
                Column {
                    // Приветствие
                    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                        Text("Добро пожаловать!", color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(userName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }

                    // Избранные врачи прямо на градиенте (если есть)
                    if (favDoctors.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Избранные",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 24.dp))
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            items(favDoctors) { doctor ->
                                FavoriteDoctorCard(
                                    doctor  = doctor,
                                    onClick = { onDoctorClick(doctor.doctorId) }
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        items(items) { item ->
            HomeListItem(item = item)
            Spacer(Modifier.height(10.dp))
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun FavoriteDoctorCard(doctor: DoctorResponse, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(68.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Белый кружок с рамкой — на фоне градиента хорошо читается
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
            )
            Surface(
                shape    = CircleShape,
                color    = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null,
                        tint = Color(0xFF1565C0),
                        modifier = Modifier.size(30.dp))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text      = doctor.fullName.split(" ").firstOrNull() ?: doctor.fullName,
            style     = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color     = Color.White,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )
        Text(
            text      = doctor.specializationName,
            style     = MaterialTheme.typography.labelSmall,
            color     = Color.White.copy(alpha = 0.75f),
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun HomeListItem(item: HomeItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(item.gradient)),
                contentAlignment = Alignment.Center) {
                Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}