package com.eva.app.presentation.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eva.app.BuildConfig
import com.eva.app.R
import com.eva.app.data.api.UserProfileResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.AuthRepository
import com.eva.app.util.Resource
import com.eva.app.presentation.components.EvaGradients
import com.eva.app.presentation.components.EvaType
import com.eva.app.presentation.components.ProfileNameSkeleton
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _profile        = MutableStateFlow<UserProfileResponse?>(null)
    val profile = _profile.asStateFlow()
    private val _isLoading      = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _loggedOut      = MutableStateFlow(false)
    val loggedOut = _loggedOut.asStateFlow()
    private val _photoUploading = MutableStateFlow(false)
    val photoUploading = _photoUploading.asStateFlow()
    private val _photoError     = MutableStateFlow<String?>(null)
    val photoError = _photoError.asStateFlow()

    val cachedName = tokenManager.userName
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val avatarUrl: StateFlow<String?> = _profile
        .map { p ->
            p?.avatarUrl?.let { path ->
                "${BuildConfig.BASE_URL.trimEnd('/').removeSuffix("/api/v1")}$path"
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = authRepository.getMe()) {
                is Resource.Success -> {
                    _profile.value = r.data
                    tokenManager.saveUserName(r.data.fullName)
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun uploadPhoto(uri: Uri, context: Context) {
        viewModelScope.launch {
            _photoUploading.value = true
            _photoError.value = null
            val file = uriToTempFile(uri, context)
            if (file != null) {
                when (val r = authRepository.uploadPhoto(file)) {
                    is Resource.Success -> loadProfile()
                    is Resource.Error   -> _photoError.value = r.message ?: "Ошибка загрузки фото"
                    else -> {}
                }
                file.delete()
            } else {
                _photoError.value = context.getString(R.string.profile_photo_file_read_error)
            }
            _photoUploading.value = false
        }
    }

    fun clearPhotoError() { _photoError.value = null }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _loggedOut.value = true
        }
    }

    private fun uriToTempFile(uri: Uri, context: Context): File? = runCatching {
        val ext  = context.contentResolver.getType(uri)?.substringAfter("/") ?: "jpg"
        val temp = File.createTempFile("avatar_", ".$ext", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output -> input.copyTo(output) }
        }
        temp
    }.getOrNull()
}

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenMedicalCard: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profile        by viewModel.profile.collectAsState()
    val isLoading      by viewModel.isLoading.collectAsState()
    val loggedOut      by viewModel.loggedOut.collectAsState()
    val cachedName     by viewModel.cachedName.collectAsState()
    val photoUploading by viewModel.photoUploading.collectAsState()
    val photoError     by viewModel.photoError.collectAsState()
    val avatarUrl      by viewModel.avatarUrl.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.uploadPhoto(it, context) } }

    LaunchedEffect(loggedOut) { if (loggedOut) onLogout() }
    LaunchedEffect(photoError) {
        photoError?.let { snackbar.showSnackbar(it); viewModel.clearPhotoError() }
    }

    val displayName  = profile?.fullName ?: cachedName
    val displayEmail = profile?.email

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.profile_logout_dialog_title)) },
            text  = { Text(stringResource(R.string.profile_logout_dialog_text)) },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; viewModel.logout() },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.btn_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.linearGradient(EvaGradients.specs))
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier         = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model              = ImageRequest.Builder(context)
                                    .data(avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(R.string.profile_avatar_cd),
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Icon(Icons.Default.Person, null,
                                modifier = Modifier.size(56.dp),
                                tint     = Color.White)
                        }
                    }
                    SmallFloatingActionButton(
                        onClick        = { photoPicker.launch("image/*") },
                        containerColor = Color.White,
                        contentColor   = Color(0xFF6A1B9A),
                        modifier       = Modifier.size(30.dp)
                    ) {
                        if (photoUploading) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = Color(0xFF6A1B9A)
                            )
                        } else {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (isLoading && displayName == null) {
                    ProfileNameSkeleton()
                } else {
                    displayName?.let {
                        Text(it, style = EvaType.heroTitle, color = Color.White)
                    }
                    displayEmail?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it,
                            style = EvaType.cardSub,
                            color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            ProfileMenuCard(
                icon     = Icons.Default.HealthAndSafety,
                iconTint = Color(0xFF1565C0),
                title    = stringResource(R.string.profile_menu_medical_card),
                subtitle = stringResource(R.string.profile_menu_medical_card_sub),
                onClick  = onOpenMedicalCard
            )
            ProfileMenuCard(
                icon     = Icons.Default.Settings,
                iconTint = Color(0xFF00838F),
                title    = stringResource(R.string.profile_menu_settings),
                subtitle = stringResource(R.string.profile_menu_settings_sub),
                onClick  = onOpenSettings
            )

            Spacer(Modifier.height(8.dp))

            ProfileMenuCard(
                icon       = Icons.Default.Logout,
                iconTint   = MaterialTheme.colorScheme.error,
                title      = stringResource(R.string.profile_menu_logout),
                subtitle   = stringResource(R.string.profile_menu_logout_sub),
                titleColor = MaterialTheme.colorScheme.error,
                onClick    = { showLogoutDialog = true }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileMenuCard(
    icon       : ImageVector,
    iconTint   : Color,
    title      : String,
    subtitle   : String,
    titleColor : Color = Color.Unspecified,
    onClick    : () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null,
                tint     = iconTint,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title,
                    style = EvaType.cardTitle,
                    color = titleColor)
                Spacer(Modifier.height(1.dp))
                Text(subtitle,
                    style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
