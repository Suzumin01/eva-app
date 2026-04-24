package com.eva.app.presentation.clinic_detail

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.eva.app.R
import com.eva.app.data.api.ClinicResponse

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicDetailScreen(clinic: ClinicResponse, onBack: () -> Unit, onFindDoctors: (Int, String) -> Unit) {
    val context   = LocalContext.current
    val hasCoords = clinic.latitude != null && clinic.longitude != null
    val lat = clinic.latitude?.toDoubleOrNull()
    val lon = clinic.longitude?.toDoubleOrNull()

    val mapUrl = remember(clinic.latitude, clinic.longitude) {
        val lat = clinic.latitude
        val lon = clinic.longitude
        if (lat != null && lon != null) {
            "https://static-maps.yandex.ru/1.x/?lang=ru_RU&ll=$lon,$lat&z=15&l=map&size=600,300" +
                    "&pt=$lon,$lat,pm2rdm"
        } else null
    }

    fun openInYandex() {
        val uri = if (lat != null && lon != null)
            Uri.parse("yandexmaps://maps.yandex.ru/?pt=$lon,$lat&z=17&text=${Uri.encode(clinic.clinicName)}")
        else
            Uri.parse("yandexmaps://maps.yandex.ru/?text=${Uri.encode(clinic.address)}")
        val browser = if (lat != null && lon != null)
            Uri.parse("https://maps.yandex.ru/?pt=$lon,$lat&z=17&text=${Uri.encode(clinic.clinicName)}")
        else
            Uri.parse("https://maps.yandex.ru/?text=${Uri.encode(clinic.address)}")
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (e: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, browser))
        }
    }

    fun callPhone(phone: String) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(clinic.clinicName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(16.dp).height(220.dp),
                shape     = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (mapUrl != null) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewClient = WebViewClient()
                                    settings.javaScriptEnabled    = false
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort      = true
                                    settings.builtInZoomControls  = false
                                    settings.displayZoomControls  = false
                                }
                            },
                            update   = { webView -> webView.loadUrl(mapUrl) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LocationOn, null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(clinic.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Button(
                        onClick  = { openInYandex() },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Map, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.clinic_detail_yandex_maps_btn),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(stringResource(R.string.clinic_detail_contact_info),
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary)
                    HorizontalDivider()

                    Row(verticalAlignment = Alignment.Top) {
                        Surface(shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.LocationOn, null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.label_address),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(clinic.address, fontWeight = FontWeight.Medium)
                        }
                    }

                    clinic.phone?.let { phone ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(36.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Phone, null,
                                            tint     = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.clinic_detail_phone_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(phone, fontWeight = FontWeight.Medium)
                                }
                            }
                            IconButton(onClick = { callPhone(phone) }) {
                                Icon(Icons.Default.Call, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = { onFindDoctors(clinic.clinicId, clinic.clinicName) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.clinic_detail_find_doctors_btn),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
