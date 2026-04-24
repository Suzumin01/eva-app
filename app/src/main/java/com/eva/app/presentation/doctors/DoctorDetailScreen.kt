package com.eva.app.presentation.doctors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.DoctorRepository
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class DoctorDetailViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _doctor    = MutableStateFlow<DoctorResponse?>(null)
    val doctor = _doctor.asStateFlow()
    private val _reviews   = MutableStateFlow<List<ReviewResponse>>(emptyList())
    val reviews = _reviews.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _canReview = MutableStateFlow(false)
    val canReview = _canReview.asStateFlow()
    private val _error     = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _message   = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    // ID текущего пользователя — для определения "своего" отзыва
    val currentUserId: StateFlow<String?> = tokenManager.userId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Избранное
    private val _favIds = MutableStateFlow<Set<Int>>(emptySet())
    val isFavorite: StateFlow<Boolean> = combine(_favIds, _doctor) { ids, doc ->
        doc != null && doc.doctorId in ids
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            tokenManager.favoriteDoctors.collect { json ->
                val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
                _favIds.value = (0 until arr.length()).map { arr.getInt(it) }.toSet()
            }
        }
    }

    fun load(doctorId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = doctorRepository.getDoctorById(doctorId)) {
                is Resource.Success -> _doctor.value = r.data
                is Resource.Error   -> _error.value = r.message ?: "Не удалось загрузить врача"
                else -> {}
            }
            loadReviews(doctorId)
            when (val r = doctorRepository.canReview(doctorId)) {
                is Resource.Success -> _canReview.value = r.data["canReview"] == true
                else -> {}
            }
            _isLoading.value = false
        }
    }

    private suspend fun loadReviews(doctorId: Int) {
        when (val r = doctorRepository.getDoctorReviews(doctorId)) {
            is Resource.Success -> {
                val uid = tokenManager.userId.first()
                // Свой отзыв — всегда первым
                _reviews.value = r.data.sortedWith(compareByDescending { it.userId == uid })
            }
            else -> {}
        }
    }

    fun toggleFavorite(doctorId: Int) {
        viewModelScope.launch {
            val current = _favIds.value.toMutableSet()
            if (doctorId in current) current.remove(doctorId) else current.add(doctorId)
            tokenManager.saveFavorites(JSONArray().also { current.forEach { id -> it.put(id) } }.toString())
        }
    }

    fun submitReview(doctorId: Int, rating: Int, comment: String) {
        viewModelScope.launch {
            when (val r = doctorRepository.addReview(doctorId, rating, comment.ifBlank { null })) {
                is Resource.Success -> { _canReview.value = false; _message.value = "Отзыв добавлен!"; loadReviews(doctorId) }
                is Resource.Error   -> _error.value = r.message ?: "Ошибка"
                else -> {}
            }
        }
    }

    fun updateReview(doctorId: Int, reviewId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            when (val r = doctorRepository.updateReview(reviewId, rating, comment.ifBlank { null })) {
                is Resource.Success -> { _message.value = "Отзыв обновлён"; loadReviews(doctorId) }
                is Resource.Error   -> _error.value = r.message ?: "Ошибка"
                else -> {}
            }
        }
    }

    fun deleteReview(doctorId: Int, reviewId: String) {
        viewModelScope.launch {
            when (val r = doctorRepository.deleteReview(reviewId)) {
                is Resource.Success -> { _canReview.value = true; _message.value = "Отзыв удалён"; loadReviews(doctorId) }
                is Resource.Error   -> _error.value = r.message ?: "Ошибка"
                else -> {}
            }
        }
    }

    fun clearError()   { _error.value = null }
    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDetailScreen(
    doctorId: Int,
    onBack: () -> Unit,
    onBook: (Int) -> Unit,
    viewModel: DoctorDetailViewModel = hiltViewModel()
) {
    val doctor        by viewModel.doctor.collectAsState()
    val reviews       by viewModel.reviews.collectAsState()
    val isLoading     by viewModel.isLoading.collectAsState()
    val canReview     by viewModel.canReview.collectAsState()
    val isFavorite    by viewModel.isFavorite.collectAsState()
    val error         by viewModel.error.collectAsState()
    val message       by viewModel.message.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val snackbar       = remember { SnackbarHostState() }

    // Диалоги
    var showAddReview    by remember { mutableStateOf(false) }
    var editingReview    by remember { mutableStateOf<ReviewResponse?>(null) }
    var deletingReview   by remember { mutableStateOf<ReviewResponse?>(null) }

    LaunchedEffect(doctorId) { viewModel.load(doctorId) }
    LaunchedEffect(error)   { error?.let   { snackbar.showSnackbar(it); viewModel.clearError() } }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

    // Диалог добавления
    if (showAddReview) {
        ReviewDialog(
            title     = "Оставить отзыв",
            onDismiss = { showAddReview = false },
            onSubmit  = { rating, comment ->
                showAddReview = false
                viewModel.submitReview(doctorId, rating, comment)
            }
        )
    }
    // Диалог редактирования
    editingReview?.let { review ->
        ReviewDialog(
            title          = "Редактировать отзыв",
            initialRating  = review.rating,
            initialComment = review.comment ?: "",
            onDismiss      = { editingReview = null },
            onSubmit       = { rating, comment ->
                editingReview = null
                viewModel.updateReview(doctorId, review.reviewId, rating, comment)
            }
        )
    }
    // Диалог удаления
    deletingReview?.let { review ->
        AlertDialog(
            onDismissRequest = { deletingReview = null },
            title  = { Text("Удалить отзыв?") },
            text   = { Text("Отзыв будет удалён, и вы снова сможете оставить новый.") },
            confirmButton = {
                Button(
                    onClick = { deletingReview = null; viewModel.deleteReview(doctorId, review.reviewId) },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deletingReview = null }) { Text("Отмена") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite(doctorId) }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Избранное",
                            tint = if (isFavorite) Color(0xFFE53935)
                            else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        bottomBar = {
            doctor?.let {
                Surface(shadowElevation = 8.dp) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canReview) {
                            OutlinedButton(
                                onClick  = { showAddReview = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Star, null,
                                    tint = Color(0xFFF57F17), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Оставить отзыв")
                            }
                        }
                        Button(
                            onClick  = { onBook(doctorId) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Записаться на приём", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        doctor?.let { doc ->
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
                // Шапка
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF1565C0), Color(0xFF42A5F5))))) {
                        Column(Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = RoundedCornerShape(28.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(80.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, tint = Color.White,
                                        modifier = Modifier.size(48.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(doc.fullName, color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge)
                            Text(doc.specializationName,
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                // Статистика
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(3.dp)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround) {
                            StatBubble(Icons.Default.MedicalServices,
                                "${doc.experienceYears ?: "—"}", "лет опыта", Color(0xFF1565C0))
                            StatBubble(Icons.Default.Star,
                                doc.rating ?: "—", "рейтинг", Color(0xFFF57F17))
                            StatBubble(Icons.Default.RateReview,
                                "${doc.reviewsCount}", "отзывов", Color(0xFF2E7D32))
                        }
                    }
                }
                // Клиника
                item {
                    InfoSection("Клиника") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(doc.clinicName, fontWeight = FontWeight.SemiBold)
                                Text(doc.clinicAddress, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // Биография
                if (!doc.bio.isNullOrBlank()) {
                    item {
                        InfoSection("О враче") {
                            Text(doc.bio, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                        }
                    }
                }
                // Отзывы
                if (reviews.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Отзывы (${reviews.size})", fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(reviews, key = { it.reviewId }) { review ->
                        val isOwn = review.userId == currentUserId
                        ReviewCard(
                            review   = review,
                            isOwn    = isOwn,
                            onEdit   = { editingReview = review },
                            onDelete = { deletingReview = review }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewCard(
    review: ReviewResponse,
    isOwn: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = if (isOwn)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        else
            CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(review.userFullName, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                        if (isOwn) {
                            Surface(shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary) {
                                Text("Вы", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Row {
                        repeat(5) { i ->
                            Icon(Icons.Default.Star, null,
                                tint = if (i < review.rating) Color(0xFFFFC107)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(14.dp))
                        }
                    }
                }
                // Кнопки редактирования/удаления только для своего отзыва
                if (isOwn) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Edit, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            review.comment?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ReviewDialog(
    title: String,
    initialRating: Int = 5,
    initialComment: String = "",
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var rating  by remember { mutableStateOf(initialRating) }
    var comment by remember { mutableStateOf(initialComment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ваша оценка:", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { i ->
                        IconButton(onClick = { rating = i }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Star, null,
                                tint = if (i <= rating) Color(0xFFFFC107)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(32.dp))
                        }
                    }
                }
                Text(when (rating) {
                    1 -> "Очень плохо"; 2 -> "Плохо"; 3 -> "Удовлетворительно"
                    4 -> "Хорошо"; else -> "Отлично"
                }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value         = comment,
                    onValueChange = { comment = it },
                    label         = { Text("Комментарий (необязательно)") },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    minLines = 2, maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(rating, comment) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun StatBubble(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}