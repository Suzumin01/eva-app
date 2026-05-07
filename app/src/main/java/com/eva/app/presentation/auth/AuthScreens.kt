package com.eva.app.presentation.auth

import android.app.DatePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.repository.AuthRepository
import com.eva.app.util.ErrorMapper
import com.eva.app.util.Resource
import com.eva.app.presentation.components.EvaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _loginState    = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState = _loginState.asStateFlow()
    private val _registerState = MutableStateFlow<AuthState>(AuthState.Idle)
    val registerState = _registerState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = AuthState.Loading
            _loginState.value = when (val r = authRepository.login(email.trim(), password)) {
                is Resource.Success -> AuthState.Success(r.data.fullName)
                is Resource.Error   -> AuthState.Error(ErrorMapper.map(r.message))
                else                -> AuthState.Error("Неизвестная ошибка")
            }
        }
    }

    fun register(fullName: String, email: String, phone: String?, password: String, dob: String? = null) {
        viewModelScope.launch {
            _registerState.value = AuthState.Loading
            val dobIso = dob?.takeIf { it.isNotBlank() }?.let { d ->
                val parts = d.split(".")
                if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else null
            }
            _registerState.value = when (val r = authRepository.register(fullName, email, phone, password, dobIso)) {
                is Resource.Success -> AuthState.Success("Регистрация успешна")
                is Resource.Error   -> if (r.message == "REGISTERED_LOGIN_FAILED")
                    AuthState.Success("Аккаунт создан. Войдите с вашими данными.")
                else
                    AuthState.Error(ErrorMapper.map(r.message))
                else                -> AuthState.Error("Неизвестная ошибка")
            }
        }
    }

    fun resetStates() {
        _loginState.value    = AuthState.Idle
        _registerState.value = AuthState.Idle
    }
}

sealed class AuthState {
    object Idle    : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String)   : AuthState()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onForgotPassword: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.loginState.collectAsState()
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is AuthState.Success) { viewModel.resetStates(); onLoginSuccess() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to Color(0xFF0D47A1),
                    0.45f to Color(0xFF1976D2),
                    0.75f to Color(0xFF42A5F5),
                    1.0f to Color(0xFF90CAF9)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (com.eva.app.BuildConfig.DEBUG) {
                                email = "anna.k@demo.ru"
                                password = "Demo1234!"
                                viewModel.login("anna.k@demo.ru", "Demo1234!")
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocalHospital, null,
                    tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name),
                color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.app_subtitle),
                color = Color.White.copy(alpha = 0.85f),
                style = EvaType.bodyText)

            Spacer(Modifier.height(48.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.login_title),
                        fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.login_subtitle),
                        style = EvaType.cardMeta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text(stringResource(R.string.label_email)) },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text(stringResource(R.string.label_password)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passVisible = !passVisible }) {
                                Icon(if (passVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility, null)
                            }
                        },
                        visualTransformation = if (passVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp))

                    TextButton(
                        onClick  = onForgotPassword,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(stringResource(R.string.link_forgot_password)) }

                    if (state is AuthState.Error) {
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, null,
                                tint     = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text((state as AuthState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = EvaType.cardMeta)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick  = { viewModel.login(email, password) },
                        enabled  = state !is AuthState.Loading
                                && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape    = RoundedCornerShape(50)
                    ) {
                        if (state is AuthState.Loading)
                            CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                        else
                            Text(stringResource(R.string.btn_login),
                                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick  = onNavigateToRegister,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text(stringResource(R.string.link_register)) }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.registerState.collectAsState()
    var fullName        by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var phone           by remember { mutableStateOf("") }
    var dob             by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passVisible     by remember { mutableStateOf(false) }
    val passwordsMatch  = password == confirmPassword || confirmPassword.isEmpty()
    val context         = LocalContext.current

    fun showDatePicker() {
        val cal = Calendar.getInstance()
        val parts = dob.split(".")
        if (parts.size == 3) {
            cal.set(parts[2].toIntOrNull() ?: cal.get(Calendar.YEAR),
                (parts[1].toIntOrNull() ?: 1) - 1,
                parts[0].toIntOrNull() ?: 1)
        }
        DatePickerDialog(context, { _, y, m, d ->
            dob = "%02d.%02d.%04d".format(d, m + 1, y)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            .also { it.datePicker.maxDate = System.currentTimeMillis() }
            .show()
    }

    LaunchedEffect(state) {
        if (state is AuthState.Success) { viewModel.resetStates(); onRegisterSuccess() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)) {

            Text(stringResource(R.string.register_subtitle),
                style = EvaType.bodyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(value = fullName, onValueChange = { fullName = it },
                label = { Text(stringResource(R.string.label_full_name)) },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = email, onValueChange = { email = it },
                label = { Text(stringResource(R.string.label_email)) },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = phone, onValueChange = { phone = it },
                label = { Text(stringResource(R.string.label_phone_optional)) },
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value         = dob,
                onValueChange = {},
                readOnly      = true,
                label         = { Text(stringResource(R.string.edit_profile_dob_label)) },
                leadingIcon   = { Icon(Icons.Default.CalendarMonth, null) },
                trailingIcon  = {
                    IconButton(onClick = { showDatePicker() }) {
                        Icon(Icons.Default.CalendarMonth, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                placeholder   = { Text(stringResource(R.string.edit_profile_dob_placeholder)) },
                supportingText = { Text(stringResource(R.string.edit_profile_dob_hint)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth().clickable { showDatePicker() },
                shape         = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text(stringResource(R.string.label_password_min)) },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passVisible = !passVisible }) {
                        Icon(if (passVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (passVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.label_confirm_password)) },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = if (passVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = !passwordsMatch,
                supportingText = if (!passwordsMatch) {
                    { Text(stringResource(R.string.error_passwords_mismatch),
                        color = MaterialTheme.colorScheme.error) }
                } else null,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp))

            if (state is AuthState.Error) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null,
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text((state as AuthState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = EvaType.cardMeta)
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick  = {
                    viewModel.register(
                        fullName.trim(),
                        email.trim(),
                        phone.trim().ifBlank { null },
                        password,
                        dob.ifBlank { null }
                    )
                },
                enabled  = state !is AuthState.Loading
                        && fullName.isNotBlank()
                        && email.isNotBlank()
                        && password.length >= 8
                        && confirmPassword == password,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(50)
            ) {
                if (state is AuthState.Loading)
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                else
                    Text(stringResource(R.string.btn_create_account),
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

sealed class ForgotPasswordState {
    object Idle    : ForgotPasswordState()
    object Loading : ForgotPasswordState()
    data class Success(val resetToken: String?) : ForgotPasswordState()
    data class Error(val message: String)        : ForgotPasswordState()
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow<ForgotPasswordState>(ForgotPasswordState.Idle)
    val state = _state.asStateFlow()

    fun requestReset(email: String) {
        viewModelScope.launch {
            _state.value = ForgotPasswordState.Loading
            when (val r = authRepository.forgotPassword(email)) {
                is Resource.Success<*> -> _state.value =
                    ForgotPasswordState.Success((r.data as? com.eva.app.data.api.ForgotPasswordResponse)?.resetToken)
                is Resource.Error      -> _state.value = ForgotPasswordState.Error(r.message)
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onTokenReceived: (String) -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is ForgotPasswordState.Success) {
            val token = (state as ForgotPasswordState.Success).resetToken
            if (token != null) onTokenReceived(token)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Default.LockReset, null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.forgot_password_heading),
                fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.forgot_password_description),
                style = EvaType.bodyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text(stringResource(R.string.label_email)) },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (state is ForgotPasswordState.Error) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null,
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text((state as ForgotPasswordState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = EvaType.cardMeta)
                }
            }

            if (state is ForgotPasswordState.Success &&
                (state as ForgotPasswordState.Success).resetToken == null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.forgot_password_success, email),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = EvaType.cardMeta)
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.requestReset(email.trim().lowercase()) },
                enabled = email.isNotBlank() && state !is ForgotPasswordState.Loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50)
            ) {
                if (state is ForgotPasswordState.Loading)
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else
                    Text(stringResource(R.string.btn_send),
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

sealed class ResetPasswordState {
    object Idle    : ResetPasswordState()
    object Loading : ResetPasswordState()
    object Success : ResetPasswordState()
    data class Error(val message: String) : ResetPasswordState()
}

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow<ResetPasswordState>(ResetPasswordState.Idle)
    val state = _state.asStateFlow()

    fun resetPassword(token: String, newPassword: String) {
        viewModelScope.launch {
            _state.value = ResetPasswordState.Loading
            when (val r = authRepository.resetPassword(token, newPassword)) {
                is Resource.Success<*> -> _state.value = ResetPasswordState.Success
                is Resource.Error      -> _state.value = ResetPasswordState.Error(r.message)
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    token: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: ResetPasswordViewModel = hiltViewModel()
) {
    val state           by viewModel.state.collectAsState()
    var newPassword     by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passVisible     by remember { mutableStateOf(false) }
    val passwordsMatch  = newPassword == confirmPassword || confirmPassword.isEmpty()

    LaunchedEffect(state) {
        if (state is ResetPasswordState.Success) onSuccess()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Default.Lock, null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.reset_password_heading),
                fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = newPassword, onValueChange = { newPassword = it },
                label = { Text(stringResource(R.string.label_new_password)) },
                leadingIcon  = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passVisible = !passVisible }) {
                        Icon(if (passVisible) Icons.Default.VisibilityOff
                             else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (passVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.label_confirm_password)) },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                isError = !passwordsMatch,
                supportingText = if (!passwordsMatch) {
                    { Text(stringResource(R.string.error_passwords_mismatch)) }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (state is ResetPasswordState.Error) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null,
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text((state as ResetPasswordState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = EvaType.cardMeta)
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.resetPassword(token, newPassword) },
                enabled = newPassword.length >= 8 && passwordsMatch
                       && confirmPassword.isNotBlank()
                       && state !is ResetPasswordState.Loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50)
            ) {
                if (state is ResetPasswordState.Loading)
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else
                    Text(stringResource(R.string.btn_save_password),
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}
