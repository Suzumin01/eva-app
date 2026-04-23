package com.eva.app.presentation.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eva.app.BuildConfig
import com.eva.app.data.api.UserProfileResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.AuthRepository
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _profile   = MutableStateFlow<UserProfileResponse?>(null)
    val profile = _profile.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _loggedOut = MutableStateFlow(false)
    val loggedOut = _loggedOut.asStateFlow()
    private val _photoUploading = MutableStateFlow(false)
    val photoUploading = _photoUploading.asStateFlow()

    val cachedName = tokenManager.userName
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
            val file = uriToTempFile(uri, context)
            if (file != null) {
                when (authRepository.uploadPhoto(file)) {
                    is Resource.Success<*> -> loadProfile()
                    else -> {}
                }
                file.delete()
            }
            _photoUploading.value = false
        }
    }

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
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.uploadPhoto(it, context) } }

    LaunchedEffect(loggedOut) { if (loggedOut) onLogout() }

    val displayName  = profile?.fullName ?: cachedName
    val displayEmail = profile?.email
    // BASE_URL = "http://host:port/api/v1/" → нужен только хост, avatarUrl уже содержит /api/v1/...
    val serverRoot   = BuildConfig.BASE_URL.trimEnd('/').removeSuffix("/api/v1")
    val avatarUrl    = profile?.avatarUrl?.let { "$serverRoot$it" }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            text  = { Text("Вы будете перенаправлены на экран входа.") },
            confirmButton = {
                Button(onClick = { showLogoutDialog = false; viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Выйти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape  = CircleShape,
                color  = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Аватар",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            SmallFloatingActionButton(
                onClick = { photoPicker.launch("image/*") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp)
            ) {
                if (photoUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (isLoading && displayName == null) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            displayName?.let {
                Text(it, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            displayEmail?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(28.dp))

        ProfileActionCard(
            icon     = Icons.Default.HealthAndSafety,
            label    = "Медицинская карта",
            subtitle = "История приёмов и AI-анализов",
            color    = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.primary,
            onClick  = onOpenMedicalCard
        )
        Spacer(Modifier.height(10.dp))
        ProfileActionCard(
            icon     = Icons.Default.Settings,
            label    = "Настройки",
            subtitle = "Профиль, согласия, тема оформления",
            color    = MaterialTheme.colorScheme.secondaryContainer,
            iconTint = MaterialTheme.colorScheme.secondary,
            onClick  = onOpenSettings
        )
        Spacer(Modifier.height(10.dp))
        ProfileActionCard(
            icon     = Icons.Default.Logout,
            label    = "Выйти из аккаунта",
            subtitle = "Вернуться на экран входа",
            color    = MaterialTheme.colorScheme.errorContainer,
            iconTint = MaterialTheme.colorScheme.error,
            onClick  = { showLogoutDialog = true }
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ProfileActionCard(
    icon: ImageVector, label: String, subtitle: String,
    color: Color, iconTint: Color, onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = color, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}