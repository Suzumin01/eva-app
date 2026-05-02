package com.eva.app.presentation.doctors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.eva.app.presentation.components.AnimatedListItem
import com.eva.app.presentation.components.DoctorDetailSkeleton
import com.eva.app.presentation.components.EvaGradients
import com.eva.app.presentation.components.GradientIconBox
import com.eva.app.presentation.components.IconCircle
import com.eva.app.presentation.components.SectionHeader
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.api.DoctorResponse
import com.eva.app.data.api.ReviewResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.DoctorRepository
import com.eva.app.util.Resource
import com.eva.app.presentation.components.EvaType
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

    val currentUserId: StateFlow<String?> = tokenManager.userId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
    val snackbar = remember { SnackbarHostState() }

    var showAddReview  by remember { mutableStateOf(false) }
    var showReviews    by remember { mutableStateOf(false) }
    var editingReview  by remember { mutableStateOf<ReviewResponse?>(null) }
    var deletingReview by remember { mutableStateOf<ReviewResponse?>(null) }

    LaunchedEffect(doctorId) { viewModel.load(doctorId) }
    LaunchedEffect(error)    { error?.let   { snackbar.showSnackbar(it); viewModel.clearError() } }
    LaunchedEffect(message)  { message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

    if (showReviews) {
        ReviewsBottomSheet(
            reviews       = reviews,
            currentUserId = currentUserId,
            canReview     = canReview,
            onAddReview   = { showReviews = false; showAddReview = true },
            onEdit        = { editingReview = it; showReviews = false },
            onDelete      = { deletingReview = it; showReviews = false },
            onDismiss     = { showReviews = false }
        )
    }
    if (showAddReview) {
        ReviewBottomSheet(
            title     = stringResource(R.string.doctor_add_review_btn),
            onDismiss = { showAddReview = false },
            onSubmit  = { rating, comment ->
                showAddReview = false
                viewModel.submitReview(doctorId, rating, comment)
            }
        )
    }
    editingReview?.let { review ->
        ReviewBottomSheet(
            title          = stringResource(R.string.doctor_edit_review_title),
            initialRating  = review.rating,
            initialComment = review.comment ?: "",
            onDismiss      = { editingReview = null },
            onSubmit       = { rating, comment ->
                editingReview = null
                viewModel.updateReview(doctorId, review.reviewId, rating, comment)
            }
        )
    }
    deletingReview?.let { review ->
        AlertDialog(
            onDismissRequest = { deletingReview = null },
            title  = { Text(stringResource(R.string.doctor_delete_review_title)) },
            text   = { Text(stringResource(R.string.doctor_delete_review_text)) },
            confirmButton = {
                Button(
                    onClick = { deletingReview = null; viewModel.deleteReview(doctorId, review.reviewId) },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingReview = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            doctor?.let {
                Surface(shadowElevation = 8.dp) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Button(
                            onClick  = { onBook(doctorId) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape    = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.doctor_book_btn), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            DoctorDetailSkeleton()
            return@Scaffold
        }
        doctor?.let { doc ->
            Box(Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                item {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                            .background(Brush.linearGradient(EvaGradients.doctors))
                            .statusBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null,
                                tint     = Color.White,
                                modifier = Modifier.size(54.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(doc.fullName,
                            style     = EvaType.heroTitle,
                            color     = Color.White,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Text(doc.specializationName,
                            style = EvaType.heroSub,
                            color = Color.White.copy(alpha = 0.85f))
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null,
                                tint     = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(doc.clinicName,
                                style = EvaType.heroCaption,
                                color = Color.White.copy(alpha = 0.7f))
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            HeroStat(
                                icon  = Icons.Default.MedicalServices,
                                value = "${doc.experienceYears ?: "—"}",
                                label = stringResource(R.string.doctor_stat_experience),
                                tint  = Color.White
                            )
                            VerticalDivider(
                                modifier = Modifier.height(36.dp),
                                color    = Color.White.copy(alpha = 0.35f)
                            )
                            HeroStat(
                                icon  = Icons.Default.Star,
                                value = doc.rating ?: "—",
                                label = stringResource(R.string.doctor_stat_rating),
                                tint  = Color(0xFFFFC107)
                            )
                            VerticalDivider(
                                modifier = Modifier.height(36.dp),
                                color    = Color.White.copy(alpha = 0.35f)
                            )
                            HeroStat(
                                icon  = Icons.Default.RateReview,
                                value = "${doc.reviewsCount}",
                                label = stringResource(R.string.doctor_stat_reviews),
                                tint  = Color.White
                            )
                        }
                    }
                }
                if (!doc.bio.isNullOrBlank()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        SectionHeader(stringResource(R.string.doctor_about_section),
                            modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(doc.bio,
                            style    = EvaType.bodyText,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f),
                            modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        onClick   = { showReviews = true },
                        modifier  = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2E7D32).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.RateReview, null,
                                    tint     = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                stringResource(R.string.doctor_reviews_count, reviews.size),
                                style    = EvaType.cardTitle,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                IconButton(onClick = { viewModel.toggleFavorite(doctorId) }) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.doctor_favorites_cd),
                        tint = if (isFavorite) Color(0xFFE53935) else Color.White
                    )
                }
            }
            }
        }
    }
}

@Composable
fun ReviewRow(
    review: ReviewResponse,
    isOwn: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        if (isOwn) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(review.userFullName,
                                style = EvaType.cardTitle)
                            if (isOwn) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.doctor_review_yours),
                                        style = EvaType.cardMeta,
                                        color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                        Row {
                            repeat(5) { i ->
                                Icon(Icons.Default.Star, null,
                                    tint     = if (i < review.rating) Color(0xFFFFC107)
                                               else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    if (isOwn) {
                        Row {
                            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Edit, null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, null,
                                    tint     = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                review.comment?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it,
                        style = EvaType.cardMeta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 3.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewBottomSheet(
    title: String,
    initialRating: Int = 5,
    initialComment: String = "",
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var rating  by remember { mutableStateOf(initialRating) }
    var comment by remember { mutableStateOf(initialComment) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val ratingLabel = when (rating) {
        1    -> stringResource(R.string.review_rating_1)
        2    -> stringResource(R.string.review_rating_2)
        3    -> stringResource(R.string.review_rating_3)
        4    -> stringResource(R.string.review_rating_4)
        else -> stringResource(R.string.review_rating_5)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title,
                style = EvaType.sheetTitle)
            HorizontalDivider()
            Text(stringResource(R.string.review_your_rating),
                style = EvaType.cardSub,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { i ->
                    IconButton(onClick = { rating = i }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Star, null,
                            tint     = if (i <= rating) Color(0xFFFFC107)
                                       else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(34.dp))
                    }
                }
            }
            Text(ratingLabel,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                style      = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value         = comment,
                onValueChange = { comment = it },
                label         = { Text(stringResource(R.string.review_comment_label)) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                minLines      = 2, maxLines = 5
            )
            Button(
                onClick  = { onSubmit(rating, comment) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(50)
            ) {
                Text(stringResource(R.string.btn_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun HeroStat(icon: ImageVector, value: String, label: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(value,
                style = EvaType.heroStat,
                color = Color.White)
        }
        Text(label,
            style = EvaType.heroStatLabel,
            color = Color.White.copy(alpha = 0.75f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsBottomSheet(
    reviews: List<ReviewResponse>,
    currentUserId: String?,
    canReview: Boolean,
    onAddReview: () -> Unit,
    onEdit: (ReviewResponse) -> Unit,
    onDelete: (ReviewResponse) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (reviews.isEmpty()) stringResource(R.string.doctor_reviews_title)
                    else stringResource(R.string.doctor_reviews_count, reviews.size),
                    style    = EvaType.sheetTitle,
                    modifier = Modifier.weight(1f)
                )
                if (canReview) {
                    TextButton(onClick = onAddReview) {
                        Icon(Icons.Default.Star, null,
                            tint     = Color(0xFFF57F17),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.doctor_add_review_btn))
                    }
                }
            }
            HorizontalDivider()
            if (reviews.isEmpty()) {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.doctor_no_reviews),
                        style = EvaType.cardSub,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                reviews.forEach { review ->
                    val isOwn = review.userId == currentUserId
                    ReviewRow(
                        review   = review,
                        isOwn    = isOwn,
                        onEdit   = { onEdit(review) },
                        onDelete = { onDelete(review) }
                    )
                }
            }
        }
    }
}
