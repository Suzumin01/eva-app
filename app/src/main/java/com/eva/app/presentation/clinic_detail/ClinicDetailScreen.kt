package com.eva.app.presentation.clinic_detail

import android.webkit.WebResourceRequest
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.eva.app.data.api.ClinicResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicDetailScreen(clinic: ClinicResponse, onBack: () -> Unit) {
    val hasCoords = clinic.latitude != null && clinic.longitude != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(clinic.clinicName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())) {

            // Карта
            if (hasCoords) {
                val lat = clinic.latitude!!.toDouble()
                val lon = clinic.longitude!!.toDouble()
                // HTML с Leaflet.js — работает без API ключа, не требует cleartext
                val mapHtml = """
                    <!DOCTYPE html>
                    <html><head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                    <style>body{margin:0;padding:0}#map{width:100%;height:100vh}</style>
                    </head><body>
                    <div id="map"></div>
                    <script>
                      var map = L.map('map').setView([$lat, $lon], 15);
                      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: '© OpenStreetMap'
                      }).addTo(map);
                      L.marker([$lat, $lon]).addTo(map)
                        .bindPopup('${clinic.clinicName}').openPopup();
                    </script>
                    </body></html>
                """.trimIndent()

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView, request: WebResourceRequest
                                    ) = false
                                }
                                settings.apply {
                                    javaScriptEnabled    = true
                                    domStorageEnabled    = true
                                    loadWithOverviewMode = true
                                    useWideViewPort      = true
                                    setSupportZoom(true)
                                }
                                loadDataWithBaseURL(
                                    "https://unpkg.com", mapHtml,
                                    "text/html", "UTF-8", null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Map, null, modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Координаты не указаны",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Информация
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Контактная информация", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.LocationOn, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Адрес", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(clinic.address, fontWeight = FontWeight.Medium)
                        }
                    }
                    clinic.phone?.let { phone ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Телефон", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(phone, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}