package com.eva.app.presentation.clinic_detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.eva.app.BuildConfig
import com.eva.app.R
import com.eva.app.data.api.ClinicResponse
import com.eva.app.presentation.doctors.HeroStat
import com.eva.app.presentation.components.EvaGradients
import com.eva.app.presentation.components.EvaType
import com.eva.app.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicDetailScreen(
    clinic: ClinicResponse,
    onBack: () -> Unit,
    onFindDoctors: (Int, String) -> Unit
) {
    val context = LocalContext.current
    val base    = remember { BuildConfig.BASE_URL.trimEnd('/').removeSuffix("/api/v1") }

    fun openInMaps() {
        val lat = clinic.latitude?.toDoubleOrNull()
        val lon = clinic.longitude?.toDoubleOrNull()
        val uri = if (lat != null && lon != null)
            Uri.parse("yandexmaps://maps.yandex.ru/?pt=$lon,$lat&z=17&text=${Uri.encode(clinic.clinicName)}")
        else
            Uri.parse("yandexmaps://maps.yandex.ru/?text=${Uri.encode(clinic.address)}")
        val browser = if (lat != null && lon != null)
            Uri.parse("https://maps.yandex.ru/?pt=$lon,$lat&z=17&text=${Uri.encode(clinic.clinicName)}")
        else
            Uri.parse("https://maps.yandex.ru/?text=${Uri.encode(clinic.address)}")
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, browser))
        }
    }

    fun callPhone(phone: String) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    }

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(
                        onClick  = { onFindDoctors(clinic.clinicId, clinic.clinicName) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape    = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.PersonSearch, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.clinic_detail_find_doctors_btn),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
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
                        if (clinic.logoUrl != null) {
                            SubcomposeAsyncImage(
                                model              = "$base${clinic.logoUrl}",
                                contentDescription = null,
                                modifier           = Modifier.fillMaxSize().clip(CircleShape),
                                loading            = {
                                    Icon(Icons.Default.LocalHospital, null,
                                        tint     = Color.White,
                                        modifier = Modifier.size(54.dp))
                                },
                                error = {
                                    Icon(Icons.Default.LocalHospital, null,
                                        tint     = Color.White,
                                        modifier = Modifier.size(54.dp))
                                }
                            )
                        } else {
                            Icon(Icons.Default.LocalHospital, null,
                                tint     = Color.White,
                                modifier = Modifier.size(54.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        clinic.clinicName,
                        style     = EvaType.heroTitle,
                        color     = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        HeroStat(
                            icon  = Icons.Default.Star,
                            value = clinic.rating ?: "—",
                            label = stringResource(R.string.doctor_stat_rating),
                            tint  = Color(0xFFFFC107)
                        )
                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            color    = Color.White.copy(alpha = 0.35f)
                        )
                        HeroStat(
                            icon  = Icons.Default.People,
                            value = "${clinic.doctorsCount}",
                            label = stringResource(R.string.clinic_stat_doctors),
                            tint  = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                SectionHeader(
                    stringResource(R.string.clinic_detail_contact_info),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    shape     = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.label_address),
                                    style = EvaType.cardSub,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(clinic.address, style = EvaType.bodyText)
                            }
                            TextButton(onClick = { openInMaps() }) {
                                Text(
                                    stringResource(R.string.clinic_on_map_btn),
                                    style = EvaType.menuLabel
                                )
                            }
                        }

                        clinic.phone?.let { phone ->
                            HorizontalDivider()
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.clinic_detail_phone_label),
                                        style = EvaType.cardSub,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(phone, style = EvaType.bodyText)
                                }
                                IconButton(onClick = { callPhone(phone) }) {
                                    Icon(Icons.Default.Call, null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
            }
        }
    }
}
