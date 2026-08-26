package com.rematerial.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rematerial.app.R
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialDockMetrics
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialListRow
import com.rematerial.app.core.designsystem.RematerialProgress
import com.rematerial.app.feature.production.domain.ArtisanProfile
import com.rematerial.app.feature.production.domain.ProductionRequest

private data class MaterialExample(val label: String, val description: String, val image: Int)

private val materialExamples = listOf(
    MaterialExample("Logam", "Kuat, presisi, dan dapat dibentuk ulang", R.drawable.material_metal),
    MaterialExample("Kayu", "Hangat, serbaguna, dan mudah dikerjakan", R.drawable.material_wood),
    MaterialExample("Tekstil", "Fleksibel untuk fungsi dan ekspresi", R.drawable.material_textile),
)

@Composable
fun UserHomeScreen(
    displayName: String,
    area: String,
    latestRequest: ProductionRequest? = null,
    nearbyArtisan: ArtisanProfile? = null,
    onScan: () -> Unit = {},
    onHistory: () -> Unit = {},
    onProduction: () -> Unit = {},
    onArtisans: () -> Unit = {},
) {
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(RematerialColors.Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = RematerialDockMetrics.contentBottomPadding(navigationInset)),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { ImmersiveHero(displayName, onScan, onHistory) }
        item { ImpactSummary() }
        latestRequest?.let { request -> item { ActiveProduction(request, onProduction) } }
        item {
            SectionHeading("Ruang kerjamu", "Semua langkah dalam satu tempat")
            Spacer(Modifier.height(8.dp))
            Surface(color = RematerialColors.Surface, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    RematerialListRow("Analisis material baru", supportingText = "Ambil 5 foto dan temukan pilihan produk", leadingIcon = RematerialIcons.Camera, onClick = onScan)
                    RematerialListRow("Buka riwayat bahan", supportingText = "Lihat kembali hasil terakhir dan ide tersimpan", leadingIcon = RematerialIcons.History, onClick = onHistory)
                    RematerialListRow("Temukan pengrajin", supportingText = "Cocokkan kebutuhan dengan keahlian terdekat", leadingIcon = RematerialIcons.MapPin, onClick = onArtisans)
                }
            }
        }
        item {
            SectionHeading("Kenali lebih banyak bahan", "Inspirasi untuk analisis berikutnya")
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(materialExamples) { material -> MaterialStory(material, onScan) }
            }
        }
        item {
            SectionHeading("Pengrajin di sekitarmu", area.ifBlank { "Rekomendasi mengikuti kebutuhan produk" })
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onArtisans),
                color = RematerialColors.Surface,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = RematerialColors.BronzeSoft) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            RematerialIcon(RematerialIcons.Hammer, null, Modifier.size(22.dp), RematerialColors.DeepForest)
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(nearbyArtisan?.name ?: "Pengrajin yang sesuai", style = MaterialTheme.typography.titleMedium)
                        Text(
                            nearbyArtisan?.let { "${it.distance} · ${it.capabilities.take(2).joinToString()}" } ?: "Tersedia setelah kamu memilih produk",
                            style = MaterialTheme.typography.bodySmall,
                            color = RematerialColors.Muted,
                        )
                    }
                    RematerialIcon(RematerialIcons.ChevronRight, null, Modifier.size(19.dp), RematerialColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun ImmersiveHero(displayName: String, onScan: () -> Unit, onHistory: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(390.dp).clip(RoundedCornerShape(30.dp))) {
        Image(painterResource(R.drawable.material_wood), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0xFF061C17).copy(alpha = .62f)))
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ReMaterial", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.weight(1f))
                Surface(modifier = Modifier.clickable(role = Role.Button, onClick = onHistory), shape = CircleShape, color = Color.White.copy(alpha = .18f), border = BorderStroke(1.dp, Color.White.copy(alpha = .32f))) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        RematerialIcon(RematerialIcons.History, "Riwayat analisis", Modifier.size(19.dp), Color.White)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Halo, ${displayName.ifBlank { "teman" }}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .74f))
            Spacer(Modifier.height(7.dp))
            Text("Lihat kemungkinan\ndi setiap bahan.", style = MaterialTheme.typography.displayLarge, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text("Foto materialmu, pahami kondisinya, lalu pilih benda yang ingin diwujudkan.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .78f))
            Spacer(Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onScan),
                color = Color.White.copy(alpha = .90f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .85f)),
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mulai analisis baru", style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink)
                        Text("Kamera akan memandu 5 sudut foto", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
                    }
                    Surface(shape = CircleShape, color = RematerialColors.DeepForest) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            RematerialIcon(RematerialIcons.Camera, null, Modifier.size(19.dp), Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpactSummary() {
    Surface(color = RematerialColors.Surface, shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 17.dp)) {
            Text("Dari bahan ke keputusan", style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink)
            Text("Proses singkat, hasil tetap bisa ditelusuri.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
            Spacer(Modifier.height(17.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ImpactMetric("5 foto", "bukti visual", Modifier.weight(1f))
                Box(Modifier.size(width = 1.dp, height = 40.dp).background(RematerialColors.Line))
                ImpactMetric("0–2", "pertanyaan", Modifier.weight(1f))
                Box(Modifier.size(width = 1.dp, height = 40.dp).background(RematerialColors.Line))
                ImpactMetric("3 ide", "produk jelas", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ImpactMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest)
        Text(label, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
    }
}

@Composable
private fun ActiveProduction(request: ProductionRequest, onProduction: () -> Unit) {
    Column {
        SectionHeading("Sedang dikerjakan", "Lanjutkan perjalanan karyamu")
        Spacer(Modifier.height(10.dp))
        Surface(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onProduction), color = RematerialColors.Surface, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(request.draft.title, style = MaterialTheme.typography.titleLarge)
                        Text("${request.status.label} · ${request.artisan.name}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
                    }
                    Text("${(request.status.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = RematerialColors.DeepForest)
                }
                Spacer(Modifier.height(14.dp))
                RematerialProgress(request.status.progress, height = 6.dp)
                Spacer(Modifier.height(12.dp))
                Text("Lihat detail produksi  →", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, supporting: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = RematerialColors.Ink)
        Spacer(Modifier.height(3.dp))
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
    }
}

@Composable
private fun MaterialStory(material: MaterialExample, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(width = 210.dp, height = 240.dp).clickable(role = Role.Button, onClick = onClick), color = RematerialColors.Surface, shape = RoundedCornerShape(24.dp)) {
        Column {
            Image(painterResource(material.image), null, Modifier.fillMaxWidth().height(158.dp), contentScale = ContentScale.Crop)
            Column(Modifier.padding(15.dp)) {
                Text(material.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(material.description, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
            }
        }
    }
}
