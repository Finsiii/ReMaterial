package com.rematerial.app.feature.artisan.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import com.rematerial.app.feature.artisan.domain.ArtisanJob
import com.rematerial.app.feature.production.domain.ProductionStatus
import com.rematerial.app.feature.identity.domain.Session
import com.rematerial.app.feature.identity.domain.VerificationStatus
import com.rematerial.app.feature.artisan.domain.ArtisanProfileDraft
import com.rematerial.app.feature.artisan.domain.ProfileSubmissionState

internal enum class ArtisanPage { HOME, REQUESTS, UPDATES, SERVICES, JOB, PROFILE, SETTINGS }
internal enum class ArtisanProfileOrigin { HOME, SETTINGS }
private enum class ArtisanTab(val label: String, val icon: Int) {
    HOME("Meja Kerja", RematerialIcons.Hammer),
    REQUESTS("Permintaan", RematerialIcons.Receipt),
    UPDATES("Update", RematerialIcons.Bell),
    SERVICES("Jasa", RematerialIcons.Package),
    SETTINGS("Akun", RematerialIcons.UserRound),
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ArtisanWorkspaceRoute(
    session: Session,
    onLogout: () -> Unit,
    viewModel: ArtisanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(session.accountId.value, session.role) { viewModel.applySession(session) }
    var pageName by rememberSaveable { mutableStateOf(ArtisanPage.HOME.name) }
    var motionName by rememberSaveable { mutableStateOf(HorizontalPageMotion.FORWARD.name) }
    var profileOriginName by rememberSaveable { mutableStateOf(ArtisanProfileOrigin.HOME.name) }
    val page = ArtisanPage.valueOf(pageName)
    val motion = HorizontalPageMotion.valueOf(motionName)
    val profileOrigin = ArtisanProfileOrigin.valueOf(profileOriginName)
    val go: (ArtisanPage, Boolean) -> Unit = { target, isBack -> motionName = if (isBack) HorizontalPageMotion.BACKWARD.name else HorizontalPageMotion.FORWARD.name; pageName = target.name }
    BackHandler(enabled = page != ArtisanPage.HOME) {
        if (page == ArtisanPage.JOB) viewModel.clearJob()
        val target = if (page == ArtisanPage.PROFILE) artisanProfileBackTarget(profileOrigin) else ArtisanPage.HOME
        go(target, artisanTabPosition(target) < artisanTabPosition(page) || page == ArtisanPage.JOB)
    }
    LaunchedEffect(page, state.selectedJob) {
        if (page == ArtisanPage.JOB && state.selectedJob == null) go(ArtisanPage.HOME, true)
        if (page != ArtisanPage.JOB && state.selectedJob != null) viewModel.clearJob()
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (motion == HorizontalPageMotion.FORWARD) slideInHorizontally(tween(210)) { it } togetherWith slideOutHorizontally(tween(210)) { -it }
                else slideInHorizontally(tween(210)) { -it } togetherWith slideOutHorizontally(tween(210)) { it }
            },
            label = "artisan-page-transition",
        ) { currentPage ->
            when (currentPage) {
                ArtisanPage.HOME -> ArtisanHomeScreen(session.displayName, state.jobs, { viewModel.selectJob(it); go(ArtisanPage.JOB, false) }, { go(ArtisanPage.SERVICES, false) }, { go(ArtisanPage.SETTINGS, false) })
                ArtisanPage.REQUESTS -> ArtisanJobsScreen("Permintaan masuk", "Tinjau pekerjaan baru dan antrean yang sedang berjalan.", state.jobs, { viewModel.selectJob(it); go(ArtisanPage.JOB, false) })
                ArtisanPage.UPDATES -> ArtisanJobsScreen("Update pekerjaan", "Perbarui pekerjaan aktif sampai siap diperiksa pelanggan.", state.jobs.filter { it.status !in setOf(ProductionStatus.SUBMITTED, ProductionStatus.COMPLETED, ProductionStatus.CANCELLED) }, { viewModel.selectJob(it); go(ArtisanPage.JOB, false) })
                ArtisanPage.SERVICES -> ArtisanProfileScreen(state.profile, session.verificationStatus) { go(ArtisanPage.HOME, true) }
                ArtisanPage.JOB -> state.selectedJob?.let { ArtisanJobDetailScreen(it, { viewModel.clearJob(); go(ArtisanPage.HOME, true) }, viewModel::transition) } ?: ArtisanHomeScreen(session.displayName, state.jobs, { viewModel.selectJob(it); go(ArtisanPage.JOB, false) }, { profileOriginName = ArtisanProfileOrigin.HOME.name; go(ArtisanPage.PROFILE, false) }, { go(ArtisanPage.SETTINGS, false) })
                ArtisanPage.PROFILE -> ArtisanProfileScreen(state.profile, session.verificationStatus) { val target = artisanProfileBackTarget(profileOrigin); go(target, artisanTabPosition(target) < artisanTabPosition(ArtisanPage.PROFILE)) }
                ArtisanPage.SETTINGS -> ArtisanSettingsScreen(state.profile, session.email, { go(ArtisanPage.HOME, true) }, { profileOriginName = ArtisanProfileOrigin.SETTINGS.name; go(ArtisanPage.PROFILE, true) }, onLogout)
            }
        }
        if (page in setOf(ArtisanPage.HOME, ArtisanPage.REQUESTS, ArtisanPage.UPDATES, ArtisanPage.SERVICES, ArtisanPage.SETTINGS)) ArtisanDock(page) { target -> go(target, artisanTabPosition(target) < artisanTabPosition(page)) }
    }
}

private fun artisanTabPosition(page: ArtisanPage): Int = when (page) { ArtisanPage.HOME -> 0; ArtisanPage.REQUESTS -> 1; ArtisanPage.UPDATES -> 2; ArtisanPage.SERVICES, ArtisanPage.PROFILE -> 3; ArtisanPage.SETTINGS -> 4; ArtisanPage.JOB -> 1 }
internal fun artisanProfileBackTarget(origin: ArtisanProfileOrigin): ArtisanPage = if (origin == ArtisanProfileOrigin.SETTINGS) ArtisanPage.SETTINGS else ArtisanPage.HOME

@Composable
private fun ArtisanDock(page: ArtisanPage, onSelect: (ArtisanPage) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = RematerialDockMetrics.horizontalPadding).padding(top = RematerialDockMetrics.outerVerticalPadding, bottom = RematerialDockMetrics.bottomGap),
            color = RematerialColors.Surface,
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, RematerialColors.Line),
        ) {
            Row(Modifier.fillMaxWidth().height(RematerialDockMetrics.surfaceHeight).selectableGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                ArtisanTab.entries.forEach { tab ->
                    val target = when (tab) {
                        ArtisanTab.HOME -> ArtisanPage.HOME
                        ArtisanTab.REQUESTS -> ArtisanPage.REQUESTS
                        ArtisanTab.UPDATES -> ArtisanPage.UPDATES
                        ArtisanTab.SERVICES -> ArtisanPage.SERVICES
                        ArtisanTab.SETTINGS -> ArtisanPage.SETTINGS
                    }
                    Column(Modifier.weight(1f).height(RematerialDockMetrics.surfaceHeight).selectable(selected = page == target, role = Role.Tab) { onSelect(target) }.semantics { contentDescription = tab.label }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        RematerialIcon(tab.icon, null, Modifier.size(20.dp), if (page == target) RematerialColors.DeepForest else RematerialColors.Muted)
                        Spacer(Modifier.height(4.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (page == target) RematerialColors.DeepForest else RematerialColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtisanHomeScreen(
    displayName: String,
    jobs: List<ArtisanJob>,
    onJob: (ArtisanJob) -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    val current = jobs.firstOrNull { it.status == ProductionStatus.IN_PRODUCTION } ?: jobs.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp), contentPadding = PaddingValues(bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { RematerialTopBar("Ruang Pengrajin", actionIcon = RematerialIcons.UserRound, actionDescription = "Profil pengrajin", onAction = onProfile) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Selamat datang, $displayName.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Text("Kerjakan yang paling penting.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink) }; RematerialIcon(RematerialIcons.Bell, "Notifikasi", Modifier.size(22.dp), RematerialColors.DeepForest) } }
        item { Text("Pekerjaan utama", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink) }
        current?.let { item { PriorityJobCard(it, onJob) } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Antrean pekerjaan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Text("${jobs.size} pekerjaan", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) } }
        items(jobs.filter { it.id != current?.id }, key = { it.id }) { job -> QueueRow(job, onJob) }
        item { Text("Pengaturan akun", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.sizeIn(minHeight = 48.dp).clickable(onClick = onSettings).padding(vertical = 12.dp)) }
    }
}

@Composable
private fun ArtisanJobsScreen(
    title: String,
    supporting: String,
    jobs: List<ArtisanJob>,
    onJob: (ArtisanJob) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RematerialTopBar("Ruang Pengrajin")
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink)
            Spacer(Modifier.height(7.dp))
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
            Spacer(Modifier.height(12.dp))
        }
        if (jobs.isEmpty()) item { Text("Belum ada pekerjaan pada bagian ini.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted) }
        items(jobs, key = { it.id }) { QueueRow(it, onJob) }
    }
}

@Composable
private fun PriorityJobCard(job: ArtisanJob, onJob: (ArtisanJob) -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onJob(job) }, color = RematerialColors.DeepForest, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(20.dp)) { Text(job.status.label, style = MaterialTheme.typography.labelLarge, color = RematerialColors.BronzeSoft); Spacer(Modifier.height(10.dp)); Text(job.productTitle, style = MaterialTheme.typography.headlineSmall, color = RematerialColors.Surface); Spacer(Modifier.height(6.dp)); Text("Untuk ${job.customerName} · target ${job.deadlineIso}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.BronzeSoft); Spacer(Modifier.height(18.dp)); Text("Langkah berikutnya: ${nextAction(job.status)}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Surface); Spacer(Modifier.height(10.dp)); RematerialProgress(job.status.progress, height = 5.dp) }
    }
}

private fun nextAction(status: ProductionStatus): String = when (status) {
    ProductionStatus.SUBMITTED -> "tinjau detail dan terima pekerjaan"
    ProductionStatus.NEEDS_CLARIFICATION -> "tinjau jawaban lalu terima pekerjaan"
    ProductionStatus.ACCEPTED -> "mulai proses pengerjaan"
    ProductionStatus.IN_PRODUCTION -> "selesaikan pengerjaan dan kirim untuk diperiksa"
    ProductionStatus.READY_FOR_REVIEW -> "tunggu pemeriksaan pengguna"
    ProductionStatus.REVISION_REQUESTED -> "perbaiki karya lalu mulai proses kembali"
    ProductionStatus.COMPLETED -> "pekerjaan telah selesai"
    ProductionStatus.CANCELLED -> "permintaan dibatalkan"
}

@Composable
private fun QueueRow(job: ArtisanJob, onJob: (ArtisanJob) -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onJob(job) }, color = RematerialColors.Surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(job.productTitle, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(3.dp)); Text("${job.customerName} · ${job.quantity} unit", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; Column(horizontalAlignment = Alignment.End) { Text(job.status.label, style = MaterialTheme.typography.bodySmall, color = RematerialColors.DeepForest); Text(job.deadlineIso, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) } }
    }
}

@Composable
private fun ArtisanJobDetailScreen(job: ArtisanJob, onBack: () -> Unit, onTransition: (ProductionStatus) -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        RematerialTopBar("Detail pekerjaan", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Spacer(Modifier.height(18.dp)); Text(job.productTitle, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("${job.id} · ${job.customerName}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(20.dp)); Text(job.status.label, style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest); Spacer(Modifier.height(8.dp)); RematerialProgress(job.status.progress, height = 6.dp); Spacer(Modifier.height(22.dp)); JobDetailSection("Material", job.materialSummary); JobDetailSection("Kuantitas", "${job.quantity} unit"); JobDetailSection("Kebutuhan kemampuan", job.requiredCapabilities.ifEmpty { listOf("Mengikuti detail produk") }.joinToString(", ")); JobDetailSection("Alat yang diperlukan", job.requiredTools.ifEmpty { listOf("Ditentukan pengrajin") }.joinToString(", ")); JobDetailSection("Teknik yang diperlukan", job.requiredSkills.ifEmpty { listOf("Ditentukan pengrajin") }.joinToString(", ")); if (job.provisionalScore > 0) JobDetailSection("Perkiraan pemakaian", "${job.estimatedUsage.ifBlank { "Mengikuti analisis" }} · kecocokan ${job.provisionalScore.toInt()}/100"); JobDetailSection("Batas waktu", job.deadlineIso); JobDetailSection("Alamat", job.address); JobDetailSection("Kontak pelanggan", "${job.preferredContact}: ${if (job.preferredContact == "WhatsApp") job.customerWhatsapp else job.customerPhone}"); Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { RematerialButton("Salin nomor", { clipboard.setText(AnnotatedString(if (job.preferredContact == "WhatsApp") job.customerWhatsapp else job.customerPhone)) }, Modifier.weight(1f), leadingIcon = RematerialIcons.Upload); RematerialButton("Siapkan pesan", { clipboard.setText(AnnotatedString(job.customerWhatsapp.ifBlank { job.customerPhone })) }, Modifier.weight(1f), leadingIcon = RematerialIcons.ArrowRight) }; JobDetailSection("Catatan pelanggan", job.notes); Spacer(Modifier.height(8.dp)); Text("Perbarui pekerjaan", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(10.dp)); JobAction("Terima pekerjaan", ProductionStatus.ACCEPTED, job.status, onTransition); JobAction("Minta penjelasan", ProductionStatus.NEEDS_CLARIFICATION, job.status, onTransition); JobAction("Mulai proses", ProductionStatus.IN_PRODUCTION, job.status, onTransition); JobAction("Kirim untuk diperiksa", ProductionStatus.READY_FOR_REVIEW, job.status, onTransition)
                JobAction("Mulai ulang setelah revisi", ProductionStatus.IN_PRODUCTION, job.status, onTransition)
            }
        }
    }
}

@Composable
private fun JobDetailSection(title: String, body: String) { Column(Modifier.padding(bottom = 16.dp)) { Text(title, style = MaterialTheme.typography.labelLarge, color = RematerialColors.Muted); Spacer(Modifier.height(4.dp)); Text(body, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Ink) } }

@Composable
private fun JobAction(label: String, target: ProductionStatus, current: ProductionStatus, onTransition: (ProductionStatus) -> Unit) {
    val enabled = when (current) {
        ProductionStatus.SUBMITTED -> target == ProductionStatus.ACCEPTED || target == ProductionStatus.NEEDS_CLARIFICATION
        ProductionStatus.NEEDS_CLARIFICATION -> target == ProductionStatus.ACCEPTED
        ProductionStatus.ACCEPTED -> target == ProductionStatus.IN_PRODUCTION
        ProductionStatus.IN_PRODUCTION -> target == ProductionStatus.READY_FOR_REVIEW
        ProductionStatus.REVISION_REQUESTED -> target == ProductionStatus.IN_PRODUCTION
        else -> false
    }
    RematerialButton(label, { onTransition(target) }, Modifier.fillMaxWidth().padding(vertical = 4.dp), enabled = enabled, leadingIcon = if (target == ProductionStatus.NEEDS_CLARIFICATION) RematerialIcons.ArrowLeft else RematerialIcons.ArrowRight)
}

@Composable
private fun ArtisanProfileScreen(profile: ArtisanProfileDraft, verificationStatus: VerificationStatus, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))) {
        RematerialTopBar("Profil pengrajin", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Spacer(Modifier.height(16.dp)); Text(profile.name, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Status akun: ${verificationStatus.displayLabel()}", style = MaterialTheme.typography.titleMedium, color = RematerialColors.DeepForest); Spacer(Modifier.height(14.dp)); Text("KTP, selfie, dan portofolio disalin ke penyimpanan privat saat pendaftaran. Dokumen tidak ditampilkan kembali di perangkat ini.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(22.dp)); Text(if (verificationStatus == VerificationStatus.APPROVED) "Akun dapat menerima dan memperbarui pekerjaan." else "Pekerjaan baru belum dapat diterima sampai verifikasi selesai.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Ink) }
        }
    }
}

@Composable
private fun ArtisanSettingsScreen(profile: ArtisanProfileDraft, email: String, onBack: () -> Unit, onProfile: () -> Unit, onLogout: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 14.dp, bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))) { item { RematerialTopBar("Pengaturan", onBack = onBack); Spacer(Modifier.height(24.dp)); Text(profile.name, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(6.dp)); Text(email, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(28.dp)); SettingsRow("Profil dan verifikasi", "NIK, dokumen privat, dan portofolio", onProfile); Spacer(Modifier.height(30.dp)); RematerialButton("Keluar dari akun", onLogout, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowLeft) } }
}

@Composable
private fun SettingsRow(title: String, supporting: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(supporting, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; RematerialIcon(RematerialIcons.ChevronRight, null, Modifier.size(18.dp), RematerialColors.Muted) } }

private fun VerificationStatus.displayLabel(): String = when (this) { VerificationStatus.NOT_REQUIRED -> "Tidak diperlukan"; VerificationStatus.PENDING -> "Sedang ditinjau"; VerificationStatus.APPROVED -> "Terverifikasi"; VerificationStatus.NEEDS_CORRECTION -> "Perlu koreksi" }
