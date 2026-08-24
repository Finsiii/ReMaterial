package com.rematerial.app.feature.artisan.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rematerial.app.core.designsystem.RematerialButton
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialField
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialProgress
import com.rematerial.app.core.designsystem.RematerialTopBar
import com.rematerial.app.feature.artisan.domain.ArtisanJob
import com.rematerial.app.feature.artisan.domain.ArtisanJobStatus
import com.rematerial.app.feature.artisan.domain.ArtisanProfileDraft
import com.rematerial.app.feature.artisan.domain.ProfileSubmissionState

private enum class ArtisanPage { HOME, JOB, PROFILE, SETTINGS }

@Composable
fun ArtisanWorkspaceRoute(
    onLogout: () -> Unit,
    viewModel: ArtisanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pageName by rememberSaveable { mutableStateOf(ArtisanPage.HOME.name) }
    val page = ArtisanPage.valueOf(pageName)
    when (page) {
        ArtisanPage.HOME -> ArtisanHomeScreen(state.jobs, { viewModel.selectJob(it); pageName = ArtisanPage.JOB.name }, { pageName = ArtisanPage.PROFILE.name }, { pageName = ArtisanPage.SETTINGS.name })
        ArtisanPage.JOB -> state.selectedJob?.let { ArtisanJobDetailScreen(it, viewModel::clearJob, viewModel::transition) } ?: run { pageName = ArtisanPage.HOME.name }
        ArtisanPage.PROFILE -> ArtisanProfileScreen(state.profile, { pageName = ArtisanPage.HOME.name }, viewModel::updateProfile)
        ArtisanPage.SETTINGS -> ArtisanSettingsScreen(state.profile, { pageName = ArtisanPage.HOME.name }, { pageName = ArtisanPage.PROFILE.name }, onLogout)
    }
}

@Composable
private fun ArtisanHomeScreen(
    jobs: List<ArtisanJob>,
    onJob: (ArtisanJob) -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    val current = jobs.firstOrNull { it.status == ArtisanJobStatus.PROCESSING } ?: jobs.firstOrNull()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        RematerialTopBar("Ruang Pengrajin", actionIcon = RematerialIcons.UserRound, actionDescription = "Profil pengrajin", onAction = onProfile)
        Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Selamat datang, Bima.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Text("Kerjakan yang paling penting.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink) }; RematerialIcon(RematerialIcons.Bell, "Notifikasi", Modifier.size(22.dp), RematerialColors.DeepForest) }
        Spacer(Modifier.height(24.dp)); Text("Pekerjaan utama", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Spacer(Modifier.height(10.dp))
        current?.let { PriorityJobCard(it, onJob) }
        Spacer(Modifier.height(24.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Antrean pekerjaan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Text("${jobs.size} pekerjaan", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }
        Spacer(Modifier.height(8.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) { items(jobs, key = { it.id }) { job -> if (job.id != current?.id) QueueRow(job, onJob) } }
        Spacer(Modifier.height(4.dp)); Text("Pengaturan akun", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.clickable(onClick = onSettings).padding(vertical = 12.dp))
    }
}

@Composable
private fun PriorityJobCard(job: ArtisanJob, onJob: (ArtisanJob) -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onJob(job) }, color = RematerialColors.DeepForest, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(20.dp)) { Text(job.status.label, style = MaterialTheme.typography.labelLarge, color = RematerialColors.BronzeSoft); Spacer(Modifier.height(10.dp)); Text(job.productTitle, style = MaterialTheme.typography.headlineSmall, color = RematerialColors.Surface); Spacer(Modifier.height(6.dp)); Text("Untuk ${job.customerName} · target ${job.deadline}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.BronzeSoft); Spacer(Modifier.height(18.dp)); Text("Langkah berikutnya: ${nextAction(job.status)}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Surface); Spacer(Modifier.height(10.dp)); RematerialProgress(job.status.progress, height = 5.dp) }
    }
}

private fun nextAction(status: ArtisanJobStatus): String = when (status) {
    ArtisanJobStatus.NEW -> "tinjau detail dan terima pekerjaan"
    ArtisanJobStatus.ACCEPTED -> "mulai proses pengerjaan"
    ArtisanJobStatus.REVISION -> "kirim catatan revisi ke pelanggan"
    ArtisanJobStatus.PROCESSING -> "perbarui progres atau tandai selesai"
    ArtisanJobStatus.COMPLETED -> "siapkan serah terima"
}

@Composable
private fun QueueRow(job: ArtisanJob, onJob: (ArtisanJob) -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onJob(job) }, color = RematerialColors.Surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(job.productTitle, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(3.dp)); Text("${job.customerName} · ${job.quantity}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; Column(horizontalAlignment = Alignment.End) { Text(job.status.label, style = MaterialTheme.typography.bodySmall, color = RematerialColors.DeepForest); Text(job.deadline, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) } }
    }
}

@Composable
private fun ArtisanJobDetailScreen(job: ArtisanJob, onBack: () -> Unit, onTransition: (ArtisanJobStatus) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        RematerialTopBar("Detail pekerjaan", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Spacer(Modifier.height(18.dp)); Text(job.productTitle, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("${job.id} · ${job.customerName}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(20.dp)); Text(job.status.label, style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest); Spacer(Modifier.height(8.dp)); RematerialProgress(job.status.progress, height = 6.dp); Spacer(Modifier.height(22.dp)); JobDetailSection("Material", job.materialSummary); JobDetailSection("Kuantitas", job.quantity); JobDetailSection("Batas waktu", job.deadline); JobDetailSection("Alamat", job.address); JobDetailSection("Catatan pelanggan", job.notes); Spacer(Modifier.height(8.dp)); Text("Perbarui pekerjaan", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(10.dp)); JobAction("Terima pekerjaan", ArtisanJobStatus.ACCEPTED, job.status, onTransition); JobAction("Minta revisi", ArtisanJobStatus.REVISION, job.status, onTransition); JobAction("Mulai proses", ArtisanJobStatus.PROCESSING, job.status, onTransition); JobAction("Tandai selesai", ArtisanJobStatus.COMPLETED, job.status, onTransition) }
        }
    }
}

@Composable
private fun JobDetailSection(title: String, body: String) { Column(Modifier.padding(bottom = 16.dp)) { Text(title, style = MaterialTheme.typography.labelLarge, color = RematerialColors.Muted); Spacer(Modifier.height(4.dp)); Text(body, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Ink) } }

@Composable
private fun JobAction(label: String, target: ArtisanJobStatus, current: ArtisanJobStatus, onTransition: (ArtisanJobStatus) -> Unit) {
    val enabled = target != current && !(current == ArtisanJobStatus.COMPLETED)
    RematerialButton(label, { onTransition(target) }, Modifier.fillMaxWidth().padding(vertical = 4.dp), enabled = enabled, leadingIcon = if (target == ArtisanJobStatus.REVISION) RematerialIcons.ArrowLeft else RematerialIcons.ArrowRight)
}

@Composable
private fun ArtisanProfileScreen(profile: ArtisanProfileDraft, onBack: () -> Unit, onSave: (ArtisanProfileDraft) -> Unit) {
    var draft by rememberSaveable(profile) { mutableStateOf(profile) }
    val ktpPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { draft = draft.copy(ktpUri = it.toString(), submissionState = ProfileSubmissionState.NOT_SUBMITTED) } }
    val selfiePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { draft = draft.copy(selfieUri = it.toString(), submissionState = ProfileSubmissionState.NOT_SUBMITTED) } }
    val portfolioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> if (uris.isNotEmpty()) draft = draft.copy(portfolioUris = (draft.portfolioUris + uris.map { it.toString() }).distinct(), submissionState = ProfileSubmissionState.NOT_SUBMITTED) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        RematerialTopBar("Profil pengrajin", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Spacer(Modifier.height(16.dp)); Text("Lengkapi ruang kerjamu.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Data ini hanya demo lokal untuk menyiapkan alur verifikasi pengrajin.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(20.dp)); RematerialField(draft.name, { draft = draft.copy(name = it) }, "Nama tampilan"); Spacer(Modifier.height(16.dp)); RematerialField(draft.nik, { draft = draft.copy(nik = it, submissionState = ProfileSubmissionState.NOT_SUBMITTED) }, "NIK", placeholder = "Masukkan 16 digit NIK", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); Spacer(Modifier.height(18.dp)); PickerRow("Foto KTP", if (draft.ktpUri == null) "Belum dipilih" else "Foto demo terpilih", { ktpPicker.launch("image/*") }); PickerRow("Foto selfie", if (draft.selfieUri == null) "Belum dipilih" else "Foto demo terpilih", { selfiePicker.launch("image/*") }); PickerRow("Portofolio karya", if (draft.portfolioUris.isEmpty()) "Belum ada foto" else "${draft.portfolioUris.size} foto demo terpilih", { portfolioPicker.launch("image/*") }); Spacer(Modifier.height(12.dp)); Text("Status: ${draft.submissionState.label}", style = MaterialTheme.typography.bodyMedium, color = if (draft.submissionState == ProfileSubmissionState.NEEDS_CORRECTION) androidx.compose.ui.graphics.Color(0xFF9B3F2F) else RematerialColors.DeepForest); if (draft.submissionState == ProfileSubmissionState.NEEDS_CORRECTION) { Spacer(Modifier.height(6.dp)); Text("Foto KTP perlu lebih terang. Perbarui pilihan lalu kirim kembali.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; if (draft.submissionState == ProfileSubmissionState.SUBMITTED) { Spacer(Modifier.height(10.dp)); Text("Simulasikan koreksi untuk demo", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.clickable { draft = draft.copy(submissionState = ProfileSubmissionState.NEEDS_CORRECTION); onSave(draft) }.padding(vertical = 8.dp)) }; Spacer(Modifier.height(20.dp)); RematerialButton("Kirim untuk ditinjau", { draft = draft.copy(submissionState = ProfileSubmissionState.SUBMITTED); onSave(draft) }, Modifier.fillMaxWidth(), enabled = draft.nik.length == 16 && draft.ktpUri != null && draft.selfieUri != null, leadingIcon = RematerialIcons.Upload) }
        }
    }
}

@Composable
private fun PickerRow(title: String, supporting: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 13.dp).semantics { contentDescription = "Pilih $title" }, verticalAlignment = Alignment.CenterVertically) { RematerialIcon(RematerialIcons.Upload, null, Modifier.size(20.dp), RematerialColors.DeepForest); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(supporting, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; RematerialIcon(RematerialIcons.ChevronRight, null, Modifier.size(18.dp), RematerialColors.Muted) }
}

@Composable
private fun ArtisanSettingsScreen(profile: ArtisanProfileDraft, onBack: () -> Unit, onProfile: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp)) { RematerialTopBar("Pengaturan", onBack = onBack); Spacer(Modifier.height(24.dp)); Text(profile.name, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(6.dp)); Text("artisan@rematerial.demo", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(28.dp)); SettingsRow("Profil dan verifikasi", "NIK, dokumen demo, dan portofolio", onProfile); SettingsRow("Preferensi pesanan", "Area layanan dan kapasitas produksi", {}); SettingsRow("Bantuan", "Panduan menerima dan menyelesaikan pekerjaan", {}); Spacer(Modifier.height(30.dp)); RematerialButton("Keluar dari akun", onLogout, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowLeft) }
}

@Composable
private fun SettingsRow(title: String, supporting: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(supporting, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; RematerialIcon(RematerialIcons.ChevronRight, null, Modifier.size(18.dp), RematerialColors.Muted) } }
