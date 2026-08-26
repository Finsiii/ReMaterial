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
import com.rematerial.app.core.designsystem.CompactCard
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialDockMetrics
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialProgress
import com.rematerial.app.core.designsystem.SectionHeader
import com.rematerial.app.feature.production.domain.ArtisanProfile
import com.rematerial.app.feature.production.domain.ProductionRequest

private data class MaterialExample(val label: String, val description: String, val image: Int)

private val materialExamples = listOf(
    MaterialExample("Logam", "Kuat dan dapat dibentuk ulang", R.drawable.material_metal),
    MaterialExample("Kayu", "Mudah diolah menjadi furnitur", R.drawable.material_wood),
    MaterialExample("Tekstil", "Fleksibel untuk berbagai produk", R.drawable.material_textile),
)

@Composable
fun UserHomeScreen(
    displayName: String,
    area: String,
    latestRequest: ProductionRequest? = null,
    nearbyArtisan: ArtisanProfile? = null,
    recentMaterial: String? = null,
    recentConfidence: Int? = null,
    onScan: () -> Unit = {},
    onHistory: () -> Unit = {},
    onProduction: () -> Unit = {},
    onArtisans: () -> Unit = {},
) {
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(RematerialColors.Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = RematerialDockMetrics.contentBottomPadding(navigationInset)),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { HomeHeader(displayName) }
        item { AnalysisAction(onScan) }
        item { QuickActions(onHistory, onProduction) }
        if (recentMaterial != null) item { RecentAnalysis(recentMaterial, recentConfidence, onHistory) }
        latestRequest?.let { request -> item { ActiveProduction(request, onProduction) } }
        item {
            SectionHeader("Inspirasi material", actionLabel = "Lihat semua", onAction = onScan)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(materialExamples) { material -> MaterialCard(material, onScan) }
            }
        }
        item {
            SectionHeader("Pengrajin terdekat", supportingText = area.ifBlank { null }, actionLabel = "Cari", onAction = onArtisans)
            Spacer(Modifier.height(10.dp))
            ArtisanPreview(nearbyArtisan, onArtisans)
        }
    }
}

@Composable
private fun HomeHeader(displayName: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("ReMaterial", style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest)
            Text("Halo, ${displayName.ifBlank { "Pengguna" }}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
        }
        Surface(shape = RoundedCornerShape(12.dp), color = RematerialColors.BronzeSoft) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                RematerialIcon(RematerialIcons.UserRound, "Akun pengguna", Modifier.size(20.dp), RematerialColors.DeepForest)
            }
        }
    }
}

@Composable
private fun AnalysisAction(onScan: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onScan),
        color = RematerialColors.DeepForest,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Analisis bahanmu", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text("Foto bahan untuk mendapatkan rekomendasi.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .76f))
                Spacer(Modifier.height(14.dp))
                Text("Analisis bahan  →", style = MaterialTheme.typography.labelLarge, color = RematerialColors.BronzeSoft)
            }
            Image(
                painterResource(R.drawable.material_wood),
                contentDescription = null,
                modifier = Modifier.size(width = 104.dp, height = 118.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun QuickActions(onHistory: () -> Unit, onProduction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction("Riwayat bahan", RematerialIcons.History, Modifier.weight(1f), onHistory)
        QuickAction("Produksi", RematerialIcons.Hammer, Modifier.weight(1f), onProduction)
    }
}

@Composable
private fun QuickAction(title: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        color = RematerialColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, RematerialColors.Line),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            RematerialIcon(icon, null, Modifier.size(20.dp), RematerialColors.DeepForest)
            Spacer(Modifier.size(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink)
        }
    }
}

@Composable
private fun RecentAnalysis(material: String, confidence: Int?, onClick: () -> Unit) {
    Column {
        SectionHeader("Analisis terakhir", actionLabel = "Detail", onAction = onClick)
        Spacer(Modifier.height(10.dp))
        CompactCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(materialImage(material)), null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(material, style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink)
                    Text(confidence?.let { "Kecocokan $it%" } ?: "Hasil tersimpan", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
                }
                RematerialIcon(RematerialIcons.ChevronRight, null, Modifier.size(18.dp), RematerialColors.Muted)
            }
        }
    }
}

@Composable
private fun ActiveProduction(request: ProductionRequest, onProduction: () -> Unit) {
    Column {
        SectionHeader("Produksi berjalan", actionLabel = "Detail", onAction = onProduction)
        Spacer(Modifier.height(10.dp))
        CompactCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onProduction)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(request.draft.title, style = MaterialTheme.typography.titleMedium)
                    Text("${request.status.label} · ${request.artisan.name}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
                }
                Text("${(request.status.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = RematerialColors.DeepForest)
            }
            Spacer(Modifier.height(11.dp))
            RematerialProgress(request.status.progress)
        }
    }
}

@Composable
private fun MaterialCard(material: MaterialExample, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(width = 154.dp, height = 174.dp).clickable(role = Role.Button, onClick = onClick),
        color = RematerialColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, RematerialColors.Line),
    ) {
        Column {
            Image(painterResource(material.image), null, Modifier.fillMaxWidth().height(105.dp), contentScale = ContentScale.Crop)
            Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(material.label, style = MaterialTheme.typography.titleMedium)
                Text(material.description, style = MaterialTheme.typography.labelSmall, color = RematerialColors.Muted, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ArtisanPreview(artisan: ArtisanProfile?, onClick: () -> Unit) {
    CompactCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(RematerialColors.BronzeSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                RematerialIcon(RematerialIcons.Hammer, null, Modifier.size(20.dp), RematerialColors.DeepForest)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(artisan?.name ?: "Cari pengrajin", style = MaterialTheme.typography.titleMedium)
                Text(
                    artisan?.let { "${it.distance} · ★ ${it.rating} · ${it.capabilities.take(2).joinToString()}" } ?: "Pilih produk untuk melihat kecocokan",
                    style = MaterialTheme.typography.bodySmall,
                    color = RematerialColors.Muted,
                )
            }
            RematerialIcon(RematerialIcons.ChevronRight, null, Modifier.size(18.dp), RematerialColors.Muted)
        }
    }
}

private fun materialImage(name: String): Int = when {
    name.contains("Kayu", ignoreCase = true) -> R.drawable.material_wood
    name.contains("Tekstil", ignoreCase = true) -> R.drawable.material_textile
    else -> R.drawable.material_metal
}
