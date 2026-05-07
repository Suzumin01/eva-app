package com.eva.app.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.api.NotificationResponse
import com.eva.app.data.repository.NotificationRepository
import com.eva.app.presentation.components.AnimatedListItem
import com.eva.app.presentation.components.EvaType
import com.eva.app.presentation.components.NotifItemSkeleton
import com.eva.app.presentation.components.evaPurple
import com.eva.app.util.Resource
import com.eva.app.util.formatDateFull
import com.eva.app.util.formatNotifDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository
) : ViewModel() {
    private val _notifications = MutableStateFlow<List<NotificationResponse>>(emptyList())
    val notifications = _notifications.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init { load() }

    fun load(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _isRefreshing.value = true else _isLoading.value = true
            _errorMessage.value = null
            when (val r = repository.getNotifications()) {
                is Resource.Success -> _notifications.value = r.data
                is Resource.Error   -> _errorMessage.value = r.message ?: "Ошибка загрузки"
                else -> {}
            }
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            repository.markRead(id)
            _notifications.value = _notifications.value.map {
                if (it.notificationId == id) it.copy(isRead = true) else it
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead(); load(isRefresh = true) }
    }

    fun getById(id: String) = _notifications.value.find { it.notificationId == id }
}

internal fun notifIcon(channel: String): ImageVector = when (channel) {
    "appointment"                              -> Icons.Default.CalendarMonth
    "reminder", "reminder_24h", "reminder_1h" -> Icons.Default.Alarm
    "cancellation"                             -> Icons.Default.CalendarToday
    else                                       -> Icons.Default.Notifications
}

@Composable
internal fun notifIconColor(channel: String): Color = when (channel) {
    "appointment"                              -> MaterialTheme.colorScheme.primary
    "reminder", "reminder_24h", "reminder_1h" -> Color(0xFFF57C00)
    "cancellation"                             -> MaterialTheme.colorScheme.error
    else                                       -> MaterialTheme.colorScheme.evaPurple
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    autoOpenNotifId: String? = null,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsState()
    val isLoading     by viewModel.isLoading.collectAsState()
    val isRefreshing  by viewModel.isRefreshing.collectAsState()
    val errorMessage  by viewModel.errorMessage.collectAsState()
    val hasUnread = notifications.any { !it.isRead }

    var selectedNotifId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(autoOpenNotifId, notifications) {
        if (autoOpenNotifId != null && notifications.isNotEmpty()) {
            viewModel.markRead(autoOpenNotifId)
            selectedNotifId = autoOpenNotifId
        }
    }

    selectedNotifId?.let { id ->
        notifications.find { it.notificationId == id }?.let { notif ->
            NotificationBottomSheet(
                notif     = notif,
                onDismiss = { selectedNotifId = null }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    if (hasUnread) {
                        TextButton(onClick = { viewModel.markAllRead() }) {
                            Text(stringResource(R.string.notifications_mark_all_read))
                        }
                    }
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
                items(7) { NotifItemSkeleton() }
            }
            errorMessage != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null,
                        modifier = Modifier.size(64.dp),
                        tint     = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.load() }) {
                        Text(stringResource(R.string.btn_retry))
                    }
                }
            }
            else -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh    = { viewModel.load(isRefresh = true) },
                modifier     = Modifier.padding(padding).fillMaxSize()
            ) {
                if (notifications.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NotificationsNone, null,
                                modifier = Modifier.size(64.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.notifications_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            notifications,
                            key = { _, n -> n.notificationId }
                        ) { index, notif ->
                            AnimatedListItem(index = index) {
                                NotifCard(
                                    notif   = notif,
                                    onClick = {
                                        viewModel.markRead(notif.notificationId)
                                        selectedNotifId = notif.notificationId
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotifCard(notif: NotificationResponse, onClick: () -> Unit) {
    val icon      = notifIcon(notif.channel)
    val iconColor = notifIconColor(notif.channel)

    Card(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (notif.isRead)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null,
                    tint     = iconColor,
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        notif.title,
                        style      = EvaType.cardTitle,
                        fontWeight = if (notif.isRead) FontWeight.Normal else FontWeight.SemiBold,
                        modifier   = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    if (!notif.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(iconColor)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        formatNotifDate(notif.createdAt),
                        style = EvaType.cardMeta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    notif.body,
                    maxLines = 2,
                    style    = EvaType.cardMeta,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBottomSheet(
    notif: NotificationResponse,
    onDismiss: () -> Unit
) {
    val icon      = notifIcon(notif.channel)
    val iconColor = notifIconColor(notif.channel)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null,
                    tint     = iconColor,
                    modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))

            Text(
                notif.title,
                style     = EvaType.sheetTitle,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))

            Text(
                formatDateFull(notif.createdAt),
                style = EvaType.cardMeta,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                notif.body,
                style    = EvaType.bodyText,
                color    = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
