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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.rematerial.app.core.designsystem.DockDestination
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialDock
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialListRow
import com.rematerial.app.core.designsystem.RematerialTopBar

private data class MaterialExample(val label: String, val description: String, val image: Int)
private val materialExamples = listOf(MaterialExample("Logam", "Permukaan logam untuk dianalisis", R.drawable.material_metal), MaterialExample("Kayu", "Serat kayu untuk dianalisis", R.drawable.material_wood), MaterialExample("Tekstil", "Kain dan serat untuk dianalisis", R.drawable.material_textile))

@Composable
fun UserHomeScreen(
    onScan: () -> Unit = {},
    onProduction: () -> Unit = {},
    onArtisans: () -> Unit = {},
    onDestinationSelected: (DockDestination) -> Unit = {},
) {
    var selectedName by rememberSaveable { mutableStateOf(DockDestination.Beranda.name) }
    val selected = DockDestination.valueOf(selectedName)
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp), contentPadding = PaddingValues(bottom = 82.dp + inset), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { RematerialTopBar("ReMaterial") }
            item { Spacer(Modifier.height(18.dp)); Text("Selamat datang, Dika", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(4.dp)); Text("Bahan ini bisa\njadi apa?", style = MaterialTheme.typography.displayLarge, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Pahami material yang kamu punya, lalu ubah kemungkinan menjadi benda yang berguna.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted) }
            item { Spacer(Modifier.height(8.dp)); Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onScan), color = RematerialColors.DeepForest, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Mulai dari bahanmu", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Surface, modifier = Modifier.weight(1f)); RematerialIcon(RematerialIcons.Camera, "Scan bahan", Modifier.size(25.dp), RematerialColors.BronzeSoft) }; Spacer(Modifier.height(8.dp)); Text("Foto bahan untuk mendapat ide produk, catatan kondisi, dan langkah aman berikutnya.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.BronzeSoft); Spacer(Modifier.height(14.dp)); Text("Scan sekarang  →", style = MaterialTheme.typography.labelLarge, color = RematerialColors.Surface) } } }
            item { Spacer(Modifier.height(12.dp)); Text("Lanjutkan perjalananmu", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onProduction), color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Lampu meja kabel tembaga", style = MaterialTheme.typography.titleMedium); Text("Sedang dikerjakan oleh Bima", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; Text("70%", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest) }; Spacer(Modifier.height(11.dp)); Canvas(Modifier.fillMaxWidth().height(5.dp)) { drawRoundRect(RematerialColors.Line, cornerRadius = CornerRadius(4f)); drawRoundRect(RematerialColors.DeepForest, size = Size(size.width * .7f, size.height), cornerRadius = CornerRadius(4f)) }; Spacer(Modifier.height(10.dp)); Text("Lihat detail produksi  →", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest) } } }
            item { Spacer(Modifier.height(10.dp)); Text("Bahan terakhirmu", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); RematerialListRow(title = "Kabel tembaga", supportingText = "Dianalisis hari ini · 2,45 kg", leadingIcon = RematerialIcons.History, onClick = onScan) }
            item { Spacer(Modifier.height(10.dp)); Text("Bahan memberi dampak", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ImpactCard("2,45 kg", "dialihkan dari limbah", Modifier.weight(1f)); ImpactCard("3 ide", "siap dieksplorasi", Modifier.weight(1f)) } }
            item { Spacer(Modifier.height(10.dp)); Text("Inspirasi untuk langkah berikutnya", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(materialExamples) { material -> Surface(Modifier.size(width = 150.dp, height = 170.dp).clickable(role = Role.Button, onClick = onScan).semantics { contentDescription = "Pilih material ${material.label.lowercase()}" }, color = RematerialColors.Surface, shape = RoundedCornerShape(14.dp)) { Column { Image(painterResource(material.image), null, Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)), contentScale = ContentScale.Crop); Text(material.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)); Text(material.description, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted, modifier = Modifier.padding(horizontal = 12.dp)) } } } } }
            item { Spacer(Modifier.height(10.dp)); Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onArtisans), color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Text("Di sekitar Bandung", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(5.dp)); Text("Bima Pratama · 3,2 km", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Text("Logam bekas, lampu kecil, dan las ringan", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Spacer(Modifier.height(8.dp)); Text("Lihat pengrajin  →", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest) } } }
        }
        RematerialDock(selected, { selectedName = it.name; onDestinationSelected(it) }, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ImpactCard(value: String, label: String, modifier: Modifier = Modifier) { Surface(modifier, color = RematerialColors.Surface, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(14.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest); Spacer(Modifier.height(4.dp)); Text(label, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) } } }
