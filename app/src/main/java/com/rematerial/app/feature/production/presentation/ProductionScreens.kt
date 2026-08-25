package com.rematerial.app.feature.production.presentation

import android.content.Intent
import android.net.Uri

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rematerial.app.core.designsystem.RematerialButton
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.HorizontalPageMotion
import com.rematerial.app.core.designsystem.RematerialDockMetrics
import com.rematerial.app.core.designsystem.RematerialField
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialProgress
import com.rematerial.app.core.designsystem.RematerialTopBar
import com.rematerial.app.R
import com.rematerial.app.feature.production.domain.ArtisanProfile
import com.rematerial.app.feature.production.domain.ProductionRequest
import com.rematerial.app.feature.production.domain.isReadyForProduction
import com.rematerial.app.feature.production.domain.ProductionStatus

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ProductionRoute(
    onBack: () -> Unit,
    viewModel: ProductionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back: () -> Unit = {
        if (!viewModel.back()) onBack()
    }
    LaunchedEffect(state.page, state.selectedArtisan, state.submitted) { viewModel.normalizePage() }
    BackHandler(onBack = back)
    Box(Modifier.fillMaxSize().background(RematerialColors.Canvas)) {
        AnimatedContent(
            targetState = state.page,
            transitionSpec = {
                val motion = if (productionBackTarget(initialState) == targetState) HorizontalPageMotion.BACKWARD else HorizontalPageMotion.FORWARD
                if (motion == HorizontalPageMotion.FORWARD) slideInHorizontally(tween(210)) { it } togetherWith slideOutHorizontally(tween(210)) { -it }
                else slideInHorizontally(tween(210)) { -it } togetherWith slideOutHorizontally(tween(210)) { it }
            },
            label = "production-page-transition",
        ) { currentPage ->
            when (currentPage) {
                ProductionPage.DISCOVERY -> DiscoveryScreen(state, back, viewModel::setArea, viewModel::search, viewModel::openDetail, viewModel::openHistory)
                ProductionPage.DETAIL -> ArtisanDetailScreen(state, back, viewModel::openForm)
                ProductionPage.FORM -> ProductionFormScreen(state, back, viewModel::setQuantity, viewModel::setNotes, viewModel::setAddress, viewModel::setTargetDate, viewModel::setPhone, viewModel::setWhatsapp, viewModel::setPreferredContact, viewModel::submit)
                ProductionPage.CONFIRMED -> ConfirmationScreen(state.submitted, viewModel::openHistory, back)
                ProductionPage.HISTORY -> ProductionHistoryScreen(state.requests, back, viewModel::openRequest)
                ProductionPage.REQUEST -> state.submitted?.let { RequestDetailScreen(it, back, viewModel::backToDiscovery) }
            }
        }
    }
}

@Composable
private fun DiscoveryScreen(
    state: ProductionState,
    onBack: () -> Unit,
    onAreaChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onArtisanSelected: (ArtisanProfile) -> Unit,
    onHistory: () -> Unit,
) {
    var mapMode by rememberSaveable { mutableStateOf(true) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp),
        contentPadding = PaddingValues(bottom = RematerialDockMetrics.contentBottomPadding(bottom)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { RematerialTopBar("Buat di Pengrajin", onBack = onBack, actionIcon = RematerialIcons.History, actionDescription = "Riwayat produksi", onAction = onHistory) }
        item { Text("Temukan tangan yang tepat.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink) }
        item { Text(if (state.draft.title.isBlank()) "Produksi dimulai dari analisis bahan. Pilih rekomendasi agar kami bisa mencarikan pengrajin yang sesuai." else "Kami mencarikan pengrajin di sekitar area pilihanmu untuk mewujudkan ${state.draft.title.lowercase()}.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted) }
        if (!state.draft.isReadyForProduction()) {
            item { Surface(color = RematerialColors.Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, RematerialColors.Line)) { Column(Modifier.padding(18.dp)) { Text("Analisis bahan dulu", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Spacer(Modifier.height(6.dp)); Text("Pilih satu rekomendasi produk dari hasil analisis untuk membuka daftar pengrajin dan permintaan produksi.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(12.dp)); Text("Riwayat produksi tetap bisa dilihat dari ikon di atas.", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest) } } }
        } else {
            item { RematerialField(state.area, onAreaChanged, "Area pencarian", placeholder = "Contoh: Cicendo atau Bandung", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)) }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Gunakan area manual untuk hasil yang lebih relevan.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted, modifier = Modifier.weight(1f)); Text("Cari", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onSearch).padding(8.dp)) } }
            item { Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(18.dp)) { Text("Peta", style = MaterialTheme.typography.labelLarge, color = if (mapMode) RematerialColors.DeepForest else RematerialColors.Muted, modifier = Modifier.sizeIn(minHeight = 48.dp).selectable(selected = mapMode, role = Role.Tab) { mapMode = true }.padding(vertical = 8.dp)); Text("Daftar", style = MaterialTheme.typography.labelLarge, color = if (!mapMode) RematerialColors.DeepForest else RematerialColors.Muted, modifier = Modifier.sizeIn(minHeight = 48.dp).selectable(selected = !mapMode, role = Role.Tab) { mapMode = false }.padding(vertical = 8.dp)) } }
            if (mapMode) item { MapCanvas(state.artisans, Modifier.fillMaxWidth().height(190.dp)) }
            item { Text("Pengrajin yang tersedia", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink) }
            if (state.artisans.isEmpty()) item { Text("Belum ada pengrajin di area ini. Coba area lain.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted) }
            else items(state.artisans, key = { it.id }) { artisan -> ArtisanListRow(artisan, onArtisanSelected) }
        }
    }
}

@Composable
private fun MapCanvas(artisans: List<ArtisanProfile>, modifier: Modifier) {
    Surface(modifier, color = Color(0xFFD8DED5), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Peta ilustrasi area pengrajin" }) {
            val roads = listOf(
                listOf(Offset(0f, size.height * .72f), Offset(size.width * .22f, size.height * .55f), Offset(size.width * .5f, size.height * .6f), Offset(size.width, size.height * .28f)),
                listOf(Offset(size.width * .08f, 0f), Offset(size.width * .35f, size.height * .34f), Offset(size.width * .56f, size.height), Offset(size.width * .82f, size.height * .7f)),
                listOf(Offset(0f, size.height * .16f), Offset(size.width * .28f, size.height * .2f), Offset(size.width * .74f, size.height * .1f), Offset(size.width, size.height * .48f)),
            )
            roads.forEach { points ->
                val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, Color(0xFFAAB6A8), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = StrokeCap.Round))
                drawPath(path, Color(0xFFE8ECE4), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, cap = StrokeCap.Round))
            }
            artisans.take(3).forEachIndexed { index, artisan ->
                val point = Offset(size.width * (.26f + index * .27f), size.height * (.43f + (index % 2) * .22f))
                drawCircle(RematerialColors.DeepForest, 10f, point)
                drawCircle(RematerialColors.Surface, 4f, point)
            }
        }
    }
}

@Composable
private fun ArtisanListRow(artisan: ArtisanProfile, onClick: (ArtisanProfile) -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onClick(artisan) }, color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(artisan.name, style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink, modifier = Modifier.weight(1f))
                Text(artisan.distance, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
            }
            Spacer(Modifier.height(6.dp))
            Text(artisan.area, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
            Spacer(Modifier.height(4.dp))
            Text("Bisa ${artisan.capabilities.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
            Spacer(Modifier.height(4.dp))
            Text("Perkiraan ${artisan.eta} · ${artisan.priceRange} · ${artisan.availability}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.DeepForest)
        }
    }
}

@Composable
private fun ArtisanDetailScreen(state: ProductionState, onBack: () -> Unit, onContinue: () -> Unit) {
    val artisan = state.selectedArtisan ?: return
    val context = LocalContext.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp), contentPadding = PaddingValues(bottom = RematerialDockMetrics.contentBottomPadding(bottom))) {
        item { RematerialTopBar("Profil pengrajin", onBack = onBack) }
        item { Spacer(Modifier.height(18.dp)); Text(artisan.name, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(6.dp)); Text("${artisan.area} · ${artisan.distance}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(20.dp)) }
        item { DetailSection("Tentang pengrajin", artisan.about) }
        item { DetailSection("Kemampuan", artisan.capabilities.joinToString("\n")) }
        item { DetailSection("Kecocokan untuk produkmu", artisan.matchReason) }
        item { DetailSection("Perkiraan proses", "${artisan.eta}\n${artisan.priceRange}\n${artisan.availability}") }
        item { Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) { Column(Modifier.weight(1f)) { Text("${artisan.rating} / 5", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Text("Penilaian", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; Column(Modifier.weight(1f)) { Text(artisan.completedJobs, style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink); Text(artisan.responseTime, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) } } }
        item { Text("Portofolio pilihan", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(artisan.portfolioImageKeys.ifEmpty { listOf("material_metal", "material_wood") }) { key -> Image(painterResource(portfolioResource(key)), "Contoh karya ${artisan.name}", Modifier.size(146.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop) } }; Spacer(Modifier.height(18.dp)) }
        item { Text("Lokasi kerja", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); MapCanvas(listOf(artisan), Modifier.fillMaxWidth().height(170.dp)); Spacer(Modifier.height(7.dp)); Text("${artisan.area}\n${artisan.workingHours}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Text("Buka rute  →", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.sizeIn(minHeight = 48.dp).clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${artisan.latitude},${artisan.longitude}?q=${artisan.latitude},${artisan.longitude}"))) } }.padding(vertical = 8.dp)) }
        item { DetailSection("Kontak", "WhatsApp ${artisan.whatsapp}\n${artisan.verifiedState}") }
        item { Spacer(Modifier.height(14.dp)); Text("Ringkasan pilihan", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Text("${state.draft.title}\n${state.draft.materialSummary}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(22.dp)); RematerialButton("Lanjutkan permintaan", onContinue, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowRight) }
    }
}

private fun portfolioResource(key: String): Int = when (key) {
    "material_wood" -> R.drawable.material_wood
    "material_textile" -> R.drawable.material_textile
    else -> R.drawable.material_metal
}

@Composable
private fun DetailSection(title: String, body: String) {
    Column(Modifier.padding(bottom = 18.dp)) { Text(title, style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest); Spacer(Modifier.height(6.dp)); Text(body, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted) }
}

@Composable
private fun ProductionFormScreen(
    state: ProductionState,
    onBack: () -> Unit,
    onQuantity: (String) -> Unit,
    onNotes: (String) -> Unit,
    onAddress: (String) -> Unit,
    onTargetDate: (String) -> Unit,
    onPhone: (String) -> Unit,
    onWhatsapp: (String) -> Unit,
    onPreferredContact: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 14.dp), contentPadding = PaddingValues(bottom = RematerialDockMetrics.contentBottomPadding(bottom))) {
        item { RematerialTopBar("Detail permintaan", onBack = onBack) }
        item { Spacer(Modifier.height(16.dp)); Text("Hampir jadi.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Lengkapi detail agar pengrajin bisa memberi estimasi yang jelas.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(20.dp)); DetailSection("Produk", "${state.draft.title}\n${state.draft.materialSummary}") }
        item { RematerialField(state.quantity, onQuantity, "Kuantitas", placeholder = "Contoh: 2 unit") }
        item { RematerialField(state.address, onAddress, "Alamat pengiriman", placeholder = "Alamat lengkap") }
        item { RematerialField(state.phone, onPhone, "Nomor WhatsApp", placeholder = "08xxxxxxxxxx", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) }
        item { RematerialField(state.whatsapp, onWhatsapp, "WhatsApp (opsional)", placeholder = "Kosongkan jika sama", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) }
        item { Text("Kontak utama: ${state.preferredContact}. Ketuk untuk mengganti.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.DeepForest, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).clickable { onPreferredContact(if (state.preferredContact == "WhatsApp") "Telepon" else "WhatsApp") }.padding(vertical = 12.dp)) }
        item { RematerialField(state.targetDate, onTargetDate, "Target selesai", placeholder = "Contoh: 20 September 2026") }
        item { RematerialField(state.notes, onNotes, "Catatan untuk pengrajin", placeholder = "Bahan, ukuran, atau detail yang penting") }
        state.error?.let { error -> item { Text(error, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9B3F2F)) } }
        item { Spacer(Modifier.height(14.dp)); RematerialButton("Kirim permintaan", onSubmit, Modifier.fillMaxWidth(), enabled = !state.loading, leadingIcon = RematerialIcons.ArrowRight) }
    }
}

@Composable
private fun ConfirmationScreen(request: ProductionRequest?, onHistory: () -> Unit, onHome: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp),
        contentPadding = PaddingValues(top = 30.dp, bottom = RematerialDockMetrics.contentBottomPadding(bottom)),
    ) {
        item { RematerialIcon(RematerialIcons.Hammer, null, Modifier.size(30.dp), RematerialColors.DeepForest); Spacer(Modifier.height(28.dp)); Text("Permintaan terkirim.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(10.dp)); Text("${request?.artisan?.name ?: "Pengrajin"} akan meninjau detail karya dan menghubungimu lewat proses berikutnya.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted); Spacer(Modifier.height(28.dp)); request?.let { DetailSection("Nomor permintaan", it.id); DetailSection("Produk", it.draft.title); DetailSection("Target selesai", it.targetDate) }; Spacer(Modifier.height(16.dp)); RematerialButton("Lihat produksi", onHistory, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.History); Spacer(Modifier.height(10.dp)); RematerialButton("Kembali ke pengrajin", onHome, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowLeft) }
    }
}

@Composable
private fun ProductionHistoryScreen(requests: List<ProductionRequest>, onBack: () -> Unit, onOpen: (ProductionRequest) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())),
    ) {
        item { RematerialTopBar("Produksi", onBack = onBack); Spacer(Modifier.height(18.dp)); Text("Perjalanan karyamu", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Pantau permintaan yang sedang berjalan dan riwayat produksi.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(12.dp)) }
        items(requests, key = { it.id }) { request -> RequestRow(request, onOpen) }
    }
}

@Composable
private fun RequestRow(request: ProductionRequest, onOpen: (ProductionRequest) -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(request) }, color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
        Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(request.draft.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text(request.id, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; Spacer(Modifier.height(6.dp)); Text("${request.artisan.name} · target ${request.targetDate}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Spacer(Modifier.height(10.dp)); Text(request.status.label, style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest); Spacer(Modifier.height(6.dp)); RematerialProgress(request.status.progress) }
    }
}

@Composable
private fun RequestDetailScreen(request: ProductionRequest, onBack: () -> Unit, onHome: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp),
        contentPadding = PaddingValues(bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())),
    ) {
        item { RematerialTopBar("Detail produksi", onBack = onBack); Spacer(Modifier.height(18.dp)); Text(request.draft.title, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("${request.id} · ${request.artisan.name}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(20.dp)); DetailSection("Status saat ini", request.status.label); RematerialProgress(request.status.progress); Spacer(Modifier.height(22.dp)); Text("Linimasa", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(10.dp)); TimelineRow("Permintaan dikirim", "Detail material dan kebutuhan diterima", true); TimelineRow("Ditinjau pengrajin", "Kesesuaian alat dan estimasi diperiksa", request.status.progress >= .4f); TimelineRow("Dikerjakan", "Pengrajin mulai membentuk karya", request.status.progress >= .7f); TimelineRow("Siap dikirim", "Karya selesai dan menunggu pengiriman", request.status.progress >= 1f); Spacer(Modifier.height(18.dp)); DetailSection("Pengiriman", "${request.address}\nTarget ${request.targetDate}"); DetailSection("Kontak", "${request.preferredContact}: ${if (request.preferredContact == "WhatsApp") request.whatsapp.ifBlank { request.phone } else request.phone}"); DetailSection("Catatan", request.notes.ifBlank { "Tidak ada catatan tambahan." }); RematerialButton("Kembali ke daftar produksi", onHome, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowLeft) }
    }
}

@Composable
private fun TimelineRow(title: String, body: String, done: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) { Box(Modifier.padding(top = 4.dp).size(12.dp).background(if (done) RematerialColors.DeepForest else RematerialColors.Line, androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(12.dp)); Column { Text(title, style = MaterialTheme.typography.titleMedium, color = if (done) RematerialColors.Ink else RematerialColors.Muted); Text(body, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) } }
}
