package com.eva.app.presentation.home

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val tokenManager: TokenManager) : ViewModel() {
    private val _userName = MutableStateFlow("Пользователь")
    val userName = _userName.asStateFlow()
    init { viewModelScope.launch { _userName.value = tokenManager.userName.first() ?: "Пользователь" } }
}

data class HomeItem(val icon: ImageVector, val title: String, val subtitle: String,
                    val gradient: List<Color>, val onClick: () -> Unit)

@Composable
fun HomeScreen(
    onDoctors: () -> Unit, onClinics: () -> Unit,
    onSpecializations: () -> Unit, onSymptoms: () -> Unit,
    onAppointments: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val userName by viewModel.userName.collectAsState()

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
        item {
            // Header
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1565C0), Color(0xFF42A5F5))))
                    .padding(24.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text("Добро пожаловать!", color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(userName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("EVA — E-Health Virtual Assistant",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall)
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
fun HomeListItem(item: HomeItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(item.gradient)),
                contentAlignment = Alignment.Center
            ) {
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
