package com.eva.app.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.api.AppointmentResponse
import com.eva.app.data.api.DoctorResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.AppointmentRepository
import com.eva.app.data.repository.DoctorRepository
import com.eva.app.util.Resource
import com.eva.app.util.formatDate
import com.eva.app.util.formatTime
import com.eva.app.presentation.components.EvaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val doctorRepository: DoctorRepository,
    private val appointmentRepository: AppointmentRepository
) : ViewModel() {
    val userName: StateFlow<String?> = tokenManager.userName
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _favDoctors = MutableStateFlow<List<DoctorResponse>>(emptyList())
    val favDoctors = _favDoctors.asStateFlow()

    private val _nextAppointment = MutableStateFlow<AppointmentResponse?>(null)
    val nextAppointment = _nextAppointment.asStateFlow()

    init {
        viewModelScope.launch {
            tokenManager.favoriteDoctors.collect { json ->
                val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
                val ids = (0 until arr.length()).map { arr.getInt(it) }
                if (ids.isEmpty()) { _favDoctors.value = emptyList(); return@collect }
                val loaded = kotlinx.coroutines.coroutineScope {
                    ids.map { id ->
                        async { (doctorRepository.getDoctorById(id) as? Resource.Success)?.data }
                    }.mapNotNull { it.await() }
                }
                _favDoctors.value = loaded
            }
        }
        viewModelScope.launch {
            val r = appointmentRepository.getMyAppointments()
            if (r is Resource.Success) {
                val today = LocalDate.now()
                val now   = LocalTime.now()
                _nextAppointment.value = r.data
                    .filter { a ->
                        if (a.status == "cancelled") return@filter false
                        val d = runCatching { LocalDate.parse(a.slotDate) }.getOrNull() ?: return@filter false
                        val t = runCatching { LocalTime.parse(a.slotTime) }.getOrNull() ?: return@filter false
                        d > today || (d == today && t > now)
                    }
                    .minByOrNull { it.slotDate + it.slotTime }
            }
        }
    }
}

data class HomeItem(
    val icon: ImageVector, val title: String, val subtitle: String,
    val gradient: List<Color>, val onClick: () -> Unit
)

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11  -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else      -> "Доброй ночи"
    }
}

@Composable
fun HomeScreen(
    onDoctors: () -> Unit,
    onClinics: () -> Unit,
    onSpecializations: () -> Unit,
    onNotifications: () -> Unit = {},
    unreadCount: Int = 0,
    onSymptoms: () -> Unit,
    onAppointments: () -> Unit,
    onDoctorClick: (Int) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val userName        by viewModel.userName.collectAsState()
    val favDoctors      by viewModel.favDoctors.collectAsState()
    val nextAppointment by viewModel.nextAppointment.collectAsState()

    val items = listOf(
        HomeItem(Icons.Default.Search,
            stringResource(R.string.menu_search_doctor),
            stringResource(R.string.menu_search_doctor_sub),
            listOf(Color(0xFF1565C0), Color(0xFF42A5F5)), onDoctors),
        HomeItem(Icons.Default.LocalHospital,
            stringResource(R.string.menu_clinics),
            stringResource(R.string.menu_clinics_sub),
            listOf(Color(0xFF00838F), Color(0xFF4DD0E1)), onClinics),
        HomeItem(Icons.Default.MedicalServices,
            stringResource(R.string.menu_specializations),
            stringResource(R.string.menu_specializations_sub),
            listOf(Color(0xFF6A1B9A), Color(0xFFAB47BC)), onSpecializations),
        HomeItem(Icons.Default.Psychology,
            stringResource(R.string.menu_ai_analysis),
            stringResource(R.string.menu_ai_analysis_sub),
            listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)), onSymptoms),
        HomeItem(Icons.Default.CalendarMonth,
            stringResource(R.string.menu_my_appointments),
            stringResource(R.string.menu_my_appointments_sub),
            listOf(Color(0xFFE65100), Color(0xFFFF8A65)), onAppointments),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1565C0), Color(0xFF1976D2), Color(0xFF42A5F5))))
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 4.dp, top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text  = greeting(),
                            color = Color.White.copy(alpha = 0.8f),
                            style = EvaType.heroGreeting
                        )
                        Text(
                            text  = userName ?: stringResource(R.string.home_fallback_user_name),
                            color = Color.White,
                            style = EvaType.heroTitle
                        )
                    }
                    IconButton(onClick = onNotifications) {
                        BadgedBox(badge = {
                            if (unreadCount > 0) Badge { Text(if (unreadCount > 9) "9+" else "$unreadCount") }
                        }) {
                            Icon(Icons.Default.Notifications, null, tint = Color.White)
                        }
                    }
                }

                if (favDoctors.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier              = Modifier.padding(bottom = 28.dp)
                    ) {
                        items(favDoctors) { doctor ->
                            FavoriteDoctorCard(
                                doctor  = doctor,
                                onClick = { onDoctorClick(doctor.doctorId) }
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(28.dp))
                }
            }
        }

        nextAppointment?.let { appt ->
            Spacer(Modifier.height(16.dp))
            UpcomingAppointmentCard(
                appointment   = appt,
                modifier      = Modifier.padding(horizontal = 16.dp),
                onClick       = onAppointments
            )
        }

        Spacer(Modifier.height(20.dp))
        val rows = items.chunked(3)
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    val index = rowIndex * 3 + colIndex
                    AnimatedMenuCard(item = item, index = index, modifier = Modifier.weight(1f))
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun AnimatedMenuCard(item: HomeItem, index: Int, modifier: Modifier = Modifier) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 60L)
        visible = true
    }
    AnimatedVisibility(
        visible  = visible,
        enter    = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 },
        modifier = modifier
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = item.onClick)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier         = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(item.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text      = item.title,
                style     = EvaType.menuLabel,
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                color     = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun FavoriteDoctorCard(doctor: DoctorResponse, onClick: () -> Unit) {
    val displayName = run {
        val parts = doctor.fullName.trim().split(" ")
        if (parts.size >= 2) {
            val lastName = parts[0]
            val initials = parts.drop(1).joinToString("") { "${it.firstOrNull() ?: ""}." }
            "$lastName $initials"
        } else doctor.fullName
    }
    Column(
        modifier            = Modifier.width(72.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier
                .size(60.dp)
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null,
                tint     = Color.White,
                modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text      = displayName,
            style     = EvaType.cardMeta,
            color     = Color.White,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )
        Text(
            text      = doctor.specializationName,
            style     = EvaType.heroStatLabel,
            color     = Color.White.copy(alpha = 0.75f),
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun UpcomingAppointmentCard(
    appointment: AppointmentResponse,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CalendarMonth, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = stringResource(R.string.home_next_appointment),
                    style      = EvaType.cardMeta,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = appointment.doctorName,
                    style = EvaType.cardTitle,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text  = "${formatDate(appointment.slotDate)}, ${formatTime(appointment.slotTime)}",
                    style = EvaType.cardSub,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}
