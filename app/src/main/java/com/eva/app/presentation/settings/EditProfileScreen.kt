package com.eva.app.presentation.settings

import android.app.DatePickerDialog
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.eva.app.R
import com.eva.app.presentation.components.EvaType
import com.eva.app.data.api.UserProfileResponse
import com.eva.app.data.local.TokenManager
import com.eva.app.data.repository.AuthRepository
import com.eva.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
                    offset <= 1  -> offset + 3
                    offset <= 4  -> offset + 3
                    offset <= 7  -> offset + 5
                    offset <= 9  -> offset + 6
                    offset <= 11 -> offset + 7
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
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _profile        = MutableStateFlow<UserProfileResponse?>(null)
    val profile = _profile.asStateFlow()
    private val _isSaving       = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    private val _error          = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved          = MutableStateFlow(false)
    val saved = _saved.asStateFlow()
    private val _profileLoading = MutableStateFlow(true)
    val profileLoading = _profileLoading.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = authRepository.getMe()) {
                is Resource.Success -> _profile.value = r.data
                is Resource.Error   -> _error.value = r.message
                else -> {}
            }
            _profileLoading.value = false
        }
    }

    fun save(
        fullName: String, phone: String,
        allergies: String, chronic: String, dob: String
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value    = null
            val phoneDigits = phone.filter { it.isDigit() }
            val dobForServer = dob.takeIf { it.isNotBlank() }?.let { d ->
                val parts = d.split(".")
                if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else null
            }
            when (val r = authRepository.updateProfile(
                fullName        = fullName.trim().ifBlank { null },
                phone           = phoneDigits.ifBlank { null },
                dateOfBirth     = dobForServer,
                allergies       = allergies.trim(),
                chronicDiseases = chronic.trim()
            )) {
                is Resource.Success -> {
                    r.data.fullName?.let { tokenManager.saveUserName(it) }
                    _saved.value = true
                }
                is Resource.Error -> _error.value = r.message
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
    val profile        by viewModel.profile.collectAsState()
    val isSaving       by viewModel.isSaving.collectAsState()
    val error          by viewModel.error.collectAsState()
    val saved          by viewModel.saved.collectAsState()
    val profileLoading by viewModel.profileLoading.collectAsState()
    val snackbar  = remember { SnackbarHostState() }
    val context   = LocalContext.current

    var fullName    by remember { mutableStateOf("") }
    var phone       by remember { mutableStateOf("") }
    var dob         by remember { mutableStateOf("") }
    var allergies   by remember { mutableStateOf("") }
    var chronic     by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        val p = profile ?: return@LaunchedEffect
        if (!initialized) {
            fullName  = p.fullName
            phone     = p.phone?.filter { it.isDigit() } ?: ""
            allergies = p.allergies ?: ""
            chronic   = p.chronicDiseases ?: ""
            dob = p.dateOfBirth?.let { iso ->
                val parts = iso.split("-")
                if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else ""
            } ?: ""
            initialized = true
        }
    }

    LaunchedEffect(saved)  { if (saved) onSaved() }
    LaunchedEffect(error)  { error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }

    val phoneDigits = phone.filter { it.isDigit() }
    val phoneValid  = phoneDigits.isEmpty() || phoneDigits.length == 11
    val nameValid   = fullName.trim().length >= 2
    val canSave     = nameValid && phoneValid && !isSaving

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
                title = {
                    Text(stringResource(R.string.settings_edit_profile),
                        style = EvaType.cardTitle)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    TextButton(
                        onClick  = { viewModel.save(fullName, phone, allergies, chronic, dob) },
                        enabled  = canSave
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.btn_save), fontWeight = FontWeight.SemiBold)
                    }
                },
                windowInsets = WindowInsets(0),
                colors       = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        if (profileLoading) {
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
            EditSection(stringResource(R.string.settings_section_personal)) {
                OutlinedTextField(
                    value          = fullName,
                    onValueChange  = { fullName = it },
                    label          = { Text(stringResource(R.string.edit_profile_full_name_label)) },
                    leadingIcon    = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) },
                    isError        = fullName.isNotEmpty() && !nameValid,
                    supportingText = if (fullName.isNotEmpty() && !nameValid)
                    {{ Text(stringResource(R.string.edit_profile_name_min_error)) }} else null,
                    modifier       = Modifier.fillMaxWidth(),
                    shape          = RoundedCornerShape(10.dp),
                    singleLine     = true
                )

                OutlinedTextField(
                    value         = phone,
                    onValueChange = { new ->
                        val digits = new.filter { it.isDigit() }.take(11)
                        phone = digits
                    },
                    label         = { Text(stringResource(R.string.label_phone)) },
                    leadingIcon   = { Icon(Icons.Default.Phone, null, Modifier.size(18.dp)) },
                    placeholder   = { Text("+7 (___) ___-__-__") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    visualTransformation = remember { PhoneVisualTransformation() },
                    isError       = !phoneValid,
                    supportingText = when {
                        !phoneValid -> {{ Text(stringResource(R.string.edit_profile_phone_error)) }}
                        else        -> {{ Text(stringResource(R.string.edit_profile_phone_count, phoneDigits.length)) }}
                    },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    singleLine    = true
                )

                val dobDigits = dob.replace(".", "").filter { it.isDigit() }
                val dobValid  = dob.isEmpty() || (dobDigits.length == 8 && run {
                    val d = dobDigits.take(2).toIntOrNull() ?: 0
                    val m = dobDigits.drop(2).take(2).toIntOrNull() ?: 0
                    val y = dobDigits.drop(4).toIntOrNull() ?: 0
                    d in 1..31 && m in 1..12 && y in 1900..java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                })
                OutlinedTextField(
                    value         = dob,
                    onValueChange = { raw ->
                        val digits = raw.replace(".", "").filter { it.isDigit() }.take(8)
                        dob = buildString {
                            digits.forEachIndexed { i, c ->
                                if (i == 2 || i == 4) append(".")
                                append(c)
                            }
                        }
                    },
                    label         = { Text(stringResource(R.string.edit_profile_dob_label)) },
                    leadingIcon   = { Icon(Icons.Default.Cake, null, Modifier.size(18.dp)) },
                    trailingIcon  = {
                        IconButton(onClick = { showDatePicker() }) {
                            Icon(Icons.Default.CalendarMonth, null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    placeholder   = { Text(stringResource(R.string.edit_profile_dob_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError       = dob.isNotEmpty() && !dobValid,
                    supportingText = when {
                        dob.isNotEmpty() && !dobValid ->
                            {{ Text(stringResource(R.string.edit_profile_dob_error)) }}
                        else ->
                            {{ Text(stringResource(R.string.edit_profile_dob_hint)) }}
                    },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    singleLine    = true
                )
            }

            EditSection(stringResource(R.string.edit_profile_section_medical)) {
                Text(stringResource(R.string.edit_profile_medical_hint),
                    style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value         = allergies,
                    onValueChange = { allergies = it },
                    label         = { Text(stringResource(R.string.edit_profile_allergies_label)) },
                    leadingIcon   = { Icon(Icons.Default.Warning, null, Modifier.size(18.dp)) },
                    placeholder   = { Text(stringResource(R.string.edit_profile_allergies_placeholder)) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    minLines = 2, maxLines = 3
                )
                OutlinedTextField(
                    value         = chronic,
                    onValueChange = { chronic = it },
                    label         = { Text(stringResource(R.string.edit_profile_chronic_label)) },
                    leadingIcon   = { Icon(Icons.Default.MonitorHeart, null, Modifier.size(18.dp)) },
                    placeholder   = { Text(stringResource(R.string.edit_profile_chronic_placeholder)) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    minLines = 2, maxLines = 3
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick  = { viewModel.save(fullName, phone, allergies, chronic, dob) },
                enabled  = canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(50)
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                else {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_save_changes), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun EditSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title,
            style    = EvaType.sectionTitle,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 2.dp))
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier            = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content             = content
            )
        }
    }
}
