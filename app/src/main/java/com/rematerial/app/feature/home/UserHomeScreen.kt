package com.rematerial.app.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rematerial.app.R
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialDockMetrics
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialListRow
import com.rematerial.app.core.designsystem.RematerialTopBar
import com.rematerial.app.feature.production.domain.ArtisanProfile
import com.rematerial.app.feature.production.domain.ProductionRequest

private data class MaterialExample(val label: String, val description: String, val image: Int)
private val materialExamples = listOf(MaterialExample("Logam", "Permukaan logam untuk dianalisis", R.drawable.material_metal), MaterialExample("Kayu", "Serat kayu untuk dianalisis", R.drawable.material_wood), MaterialExample("Tekstil", "Kain dan serat untuk dianalisis", R.drawable.material_textile))

@Composable
fun UserHomeScreen(
    displayName: String,
    area: String,
    latestRequest: ProductionRequest? = null,
    nearbyArtisan: ArtisanProfile? = null,
    onScan: () -> Unit = {},
    onProduction: () -> Unit = {},
    onArtisans: () -> Unit = {},
) {
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp), contentPadding = PaddingValues(bottom = RematerialDockMetrics.contentBottomPadding(inset)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { RematerialTopBar("ReMaterial") }
            item { Spacer(Modifier.height(18.dp)); Text("Selamat datang, ${displayName.ifBlank { "teman" }}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(4.dp)); Text("Bahan ini bisa\njadi apa?", style = MaterialTheme.typography.displayLarge, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Pahami material yang kamu punya, lalu ubah kemungkinan menjadi benda yang berguna.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted) }
            item { Spacer(Modifier.height(8.dp)); Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onScan), color = RematerialColors.DeepForest, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Mulai dari bahanmu", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Surface, modifier = Modifier.weight(1f)); RematerialIcon(RematerialIcons.Camera, "Scan bahan", Modifier.size(25.dp), RematerialColors.BronzeSoft) }; Spacer(Modifier.height(8.dp)); Text("Foto bahan untuk mendapat ide produk, catatan kondisi, dan langkah aman berikutnya.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.BronzeSoft); Spacer(Modifier.height(14.dp)); Text("Scan sekarang  →", style = MaterialTheme.typography.labelLarge, color = RematerialColors.Surface) } } }
            latestRequest?.let { request -> item { Spacer(Modifier.height(12.dp)); Text("Lanjutkan perjalananmu", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onProduction), color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(request.draft.title, style = MaterialTheme.typography.titleMedium); Text("${request.status.label} · ${request.artisan.name}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; Text("${(request.status.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest) }; Spacer(Modifier.height(11.dp)); Canvas(Modifier.fillMaxWidth().height(5.dp)) { drawRoundRect(RematerialColors.Line, cornerRadius = CornerRadius(4f)); drawRoundRect(RematerialColors.DeepForest, size = Size(size.width * request.status.progress, size.height), cornerRadius = CornerRadius(4f)) }; Spacer(Modifier.height(10.dp)); Text("Lihat detail produksi  →", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest) } } } }
            item { Spacer(Modifier.height(10.dp)); Text("Riwayat bahan", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); RematerialListRow(title = "Buka analisis material", supportingText = "Riwayat tersimpan akan muncul setelah bahan dianalisis", leadingIcon = RematerialIcons.History, onClick = onScan) }
            item { Spacer(Modifier.height(10.dp)); Text("Dari satu foto ke langkah nyata", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ImpactCard("Kenali", "kondisi dan potensi bahan", Modifier.weight(1f)); ImpactCard("Wujudkan", "sendiri atau bersama pengrajin", Modifier.weight(1f)) } }
            item { Spacer(Modifier.height(10.dp)); Text("Inspirasi untuk langkah berikutnya", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(materialExamples) { material -> Surface(Modifier.size(width = 150.dp, height = 170.dp).clickable(role = Role.Button, onClick = onScan).semantics { contentDescription = "Pilih material ${material.label.lowercase()}" }, color = RematerialColors.Surface, shape = RoundedCornerShape(14.dp)) { Column { Image(painterResource(material.image), null, Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)), contentScale = ContentScale.Crop); Text(material.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)); Text(material.description, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted, modifier = Modifier.padding(horizontal = 12.dp)) } } } } }
            item { Spacer(Modifier.height(10.dp)); Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onArtisans), color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Text("Pengrajin ${area.ifBlank { "di sekitarmu" }}", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(5.dp)); Text(nearbyArtisan?.let { "${it.name} · ${it.distance}" } ?: "Analisis bahan untuk melihat kecocokan terdekat", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Text(nearbyArtisan?.capabilities?.joinToString(limit = 3).orEmpty().ifBlank { "Rekomendasi mempertimbangkan alat dan kemampuan" }, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Spacer(Modifier.height(8.dp)); Text("Lihat pengrajin  →", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest) } } }
    }
}

@Composable
private fun ImpactCard(value: String, label: String, modifier: Modifier = Modifier) { Surface(modifier, color = RematerialColors.Surface, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(14.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest); Spacer(Modifier.height(4.dp)); Text(label, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) } } }
