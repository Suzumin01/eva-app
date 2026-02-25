package com.eva.app.presentation.settings

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.data.api.UserProfileResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.AuthRepository
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(11)
        val formatted = buildString {
            digits.forEachIndexed { i, c ->
                when (i) {
                    0    -> append("+$c (")
                    3    -> append("$c) ")
                    6    -> append("$c-")
                    8    -> append("$c-")
                    else -> append(c)
                }
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (digits.isEmpty()) return 0
                return when {
                    offset <= 0  -> 0
                    offset <= 1  -> offset + 3   // "+7 ("
                    offset <= 4  -> offset + 3
                    offset <= 7  -> offset + 5   // ") "
                    offset <= 9  -> offset + 6   // "-"
                    offset <= 11 -> offset + 7   // "-"
                    else         -> formatted.length
                }
            }
            override fun transformedToOriginal(offset: Int): Int = offset.coerceAtMost(digits.length)
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _profile  = MutableStateFlow<UserProfileResponse?>(null)
    val profile = _profile.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    private val _error    = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved    = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    val allergies       = tokenManager.allergies.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val chronicDiseases = tokenManager.chronicDiseases.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val insurancePolicy = tokenManager.insurancePolicy.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val dateOfBirth     = tokenManager.dateOfBirth.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        viewModelScope.launch {
            when (val r = authRepository.getMe()) {
                is Resource.Success -> _profile.value = r.data
                else -> {}
            }
        }
    }

    fun save(fullName: String, phone: String, allergies: String,
             chronic: String, insurance: String, dob: String) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value    = null
            val phoneDigits = phone.filter { it.isDigit() }
            when (val r = authRepository.updateProfile(
                fullName = fullName.trim().ifBlank { null },
                phone    = phoneDigits.ifBlank { null }
            )) {
                is Resource.Success -> {
                    tokenManager.saveHealthData(allergies.trim(), chronic.trim(),
                        insurance.trim(), dob)
                    _saved.value = true
                }
                is Resource.Error -> _error.value = r.message ?: "Ошибка сохранения"
                else -> {}
            }
            _isSaving.value = false
        }
    }

    fun clearError() { _error.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val profile  by viewModel.profile.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val error    by viewModel.error.collectAsState()
    val saved    by viewModel.saved.collectAsState()
    val snackbar  = remember { SnackbarHostState() }
    val context   = LocalContext.current

    var fullName  by remember { mutableStateOf("") }
    var phone     by remember { mutableStateOf("") }
    var dob       by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var chronic   by remember { mutableStateOf("") }
    var insurance by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(profile, viewModel.allergies.value) {
        if (!initialized && profile != null) {
            fullName  = profile!!.fullName
            phone     = profile!!.phone?.filter { it.isDigit() } ?: ""
            allergies = viewModel.allergies.value
            chronic   = viewModel.chronicDiseases.value
            insurance = viewModel.insurancePolicy.value
            dob       = viewModel.dateOfBirth.value
            initialized = true
        }
    }

    LaunchedEffect(saved)  { if (saved) onSaved() }
    LaunchedEffect(error)  { error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }

    val phoneDigits  = phone.filter { it.isDigit() }
    val phoneValid   = phoneDigits.isEmpty() || phoneDigits.length == 11
    val insurDigits  = insurance.filter { it.isDigit() }
    val insurValid   = insurance.isEmpty() || insurDigits.length in listOf(0, 16, 20)
    val nameValid    = fullName.trim().length >= 2
    val canSave      = nameValid && phoneValid && !isSaving

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактировать профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    TextButton(
                        onClick  = { viewModel.save(fullName, phone, allergies, chronic, insurance, dob) },
                        enabled  = canSave
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Сохранить", color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        if (!initialized && profile == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            EditSection("Личные данные") {
                OutlinedTextField(
                    value         = fullName,
                    onValueChange = { fullName = it },
                    label         = { Text("Полное имя *") },
                    leadingIcon   = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) },
                    isError       = fullName.isNotEmpty() && !nameValid,
                    supportingText = if (fullName.isNotEmpty() && !nameValid)
                    {{ Text("Минимум 2 символа") }} else null,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    singleLine    = true
                )

                OutlinedTextField(
                    value         = phone,
                    onValueChange = { new ->
                        val digits = new.filter { it.isDigit() }.take(11)
                        phone = digits
                    },
                    label         = { Text("Телефон") },
                    leadingIcon   = { Icon(Icons.Default.Phone, null, Modifier.size(18.dp)) },
                    placeholder   = { Text("+7 (___) ___-__-__") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    visualTransformation = PhoneVisualTransformation(),
                    isError       = !phoneValid,
                    supportingText = when {
                        !phoneValid -> {{ Text("Введите 11 цифр (пример: 79001234567)") }}
                        else        -> {{ Text("${phoneDigits.length}/11 цифр") }}
                    },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    singleLine    = true
                )

                OutlinedTextField(
                    value         = dob,
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Дата рождения") },
                    leadingIcon   = { Icon(Icons.Default.Cake, null, Modifier.size(18.dp)) },
                    trailingIcon  = {
                        IconButton(onClick = { showDatePicker() }) {
                            Icon(Icons.Default.CalendarMonth, null)
                        }
                    },
                    placeholder   = { Text("дд.мм.гггг") },
                    modifier      = Modifier.fillMaxWidth().also { /* open picker on tap */ },
                    shape         = RoundedCornerShape(10.dp),
                    singleLine    = true
                )
                TextButton(
                    onClick  = { showDatePicker() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (dob.isEmpty()) "Выбрать дату рождения" else "Изменить дату: $dob")
                }
            }

            EditSection("Страхование") {
                OutlinedTextField(
                    value         = insurance,
                    onValueChange = { new ->
                        val digits = new.filter { it.isDigit() }.take(20)
                        insurance = digits
                    },
                    label         = { Text("Номер полиса ОМС / ДМС") },
                    leadingIcon   = { Icon(Icons.Default.CardMembership, null, Modifier.size(18.dp)) },
                    placeholder   = { Text("16 или 20 цифр") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError       = !insurValid,
                    supportingText = when {
                        !insurValid -> {{ Text("ОМС: 16 цифр, ДМС: 20 цифр") }}
                        insurance.isNotEmpty() -> {{ Text("${insurDigits.length} цифр введено") }}
                        else -> null
                    },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    singleLine    = true
                )
            }

            EditSection("Медицинские данные") {
                Text("Хранятся только на устройстве и помогают врачу при осмотре",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value         = allergies,
                    onValueChange = { allergies = it },
                    label         = { Text("Аллергии") },
                    leadingIcon   = { Icon(Icons.Default.Warning, null, Modifier.size(18.dp)) },
                    placeholder   = { Text("Пенициллин, орехи, пыльца...") },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    minLines = 2, maxLines = 3
                )
                OutlinedTextField(
                    value         = chronic,
                    onValueChange = { chronic = it },
                    label         = { Text("Хронические заболевания") },
                    leadingIcon   = { Icon(Icons.Default.MonitorHeart, null, Modifier.size(18.dp)) },
                    placeholder   = { Text("Диабет 2 типа, гипертония...") },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    minLines = 2, maxLines = 3
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick  = { viewModel.save(fullName, phone, allergies, chronic, insurance, dob) },
                enabled  = canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                else { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp))
                    Text("Сохранить изменения", fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun EditSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}