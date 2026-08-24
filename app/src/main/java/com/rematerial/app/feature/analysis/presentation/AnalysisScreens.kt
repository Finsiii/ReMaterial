package com.rematerial.app.feature.analysis.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rematerial.app.core.designsystem.RematerialButton
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.DockDestination
import com.rematerial.app.core.designsystem.RematerialField
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialProgress
import com.rematerial.app.core.designsystem.RematerialTopBar
import com.rematerial.app.core.media.MediaUriHelper
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.SafetyOutcome
import com.rematerial.app.core.model.UnitCode
import com.rematerial.app.feature.analysis.domain.InspectionFieldType
import com.rematerial.app.feature.analysis.domain.ProductOption

@Composable
fun AnalysisRoute(
    onClose: () -> Unit,
    onOpenArtisan: () -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.attachPhoto(it.toString()) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let { viewModel.attachPhoto(it.toString()) }
    }
    fun launchCamera() {
        val uri = MediaUriHelper.newCameraUri(context)
        pendingCameraUri = uri
        camera.launch(uri)
    }
    Box(Modifier.fillMaxSize().background(RematerialColors.Canvas)) {
        when (state.step) {
            AnalysisStep.SCAN -> ScanScreen(onClose, { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, ::launchCamera, viewModel::chooseManual)
            AnalysisStep.PREVIEW -> PreviewScreen(state, onClose, viewModel::startPhotoAnalysis)
            AnalysisStep.CONFIRM -> ConfirmScreen(state, onClose, viewModel::setCategory, viewModel::continueToInputs)
            AnalysisStep.INPUTS -> InputScreen(state, onClose, viewModel::updateValue, viewModel::submitInputs)
            AnalysisStep.RESULT -> ResultScreen(state, onClose, onOpenArtisan, viewModel::selectOption, viewModel::saveForMaking)
        }
        if (state.loading) {
            Surface(Modifier.align(Alignment.Center), color = RematerialColors.Surface, shape = RoundedCornerShape(18.dp), shadowElevation = 12.dp) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = RematerialColors.Bronze, strokeWidth = 2.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Membaca material...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ScanScreen(onClose: () -> Unit, onPick: () -> Unit, onCamera: () -> Unit, onManual: (MaterialCategory) -> Unit) {
    var showManual by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
        RematerialTopBar("Scan material", onBack = onClose)
        Spacer(Modifier.height(20.dp))
        Text("Mulai dari yang paling mudah", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text("Ambil foto, pilih dari galeri, atau masukkan kategori secara manual. Hasil awal selalu bisa kamu koreksi.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted)
        Spacer(Modifier.height(26.dp))
        ScanChoice("Ambil foto", "Gunakan kamera perangkat", RematerialIcons.Camera, onCamera)
        Spacer(Modifier.height(12.dp))
        ScanChoice("Pilih dari galeri", "Gunakan foto yang sudah ada", RematerialIcons.Upload, onPick)
        Spacer(Modifier.height(12.dp))
        ScanChoice("Pilih kategori manual", "Lanjut tanpa foto", RematerialIcons.Search, { showManual = true })
        Spacer(Modifier.height(26.dp))
        Text("Tips foto", style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text("Gunakan cahaya cukup dan tampilkan permukaan material dari dekat. Hindari foto yang buram atau terlalu gelap.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
        if (showManual) {
            Spacer(Modifier.height(20.dp))
            Surface(color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RematerialColors.Line)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Kategori material", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    MaterialCategory.entries.forEach { category ->
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onManual(category) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(category.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            RematerialIcon(RematerialIcons.ArrowRight, null, Modifier.size(18.dp), RematerialColors.Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanChoice(title: String, supporting: String, icon: Int, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RematerialColors.Line)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(46.dp), color = RematerialColors.BronzeSoft, shape = RoundedCornerShape(12.dp)) {
                Box(contentAlignment = Alignment.Center) { RematerialIcon(icon, null, Modifier.size(21.dp), RematerialColors.DeepForest) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(supporting, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }
            RematerialIcon(RematerialIcons.ArrowRight, null, Modifier.size(18.dp), RematerialColors.Muted)
        }
    }
}

@Composable
private fun PreviewScreen(state: AnalysisUiState, onClose: () -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
        RematerialTopBar("Periksa foto", onBack = onClose)
        Spacer(Modifier.height(18.dp))
        Text("Foto terlihat siap", style = MaterialTheme.typography.headlineLarge, color = RematerialColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text("Pastikan material berada di dalam frame sebelum memulai analisis.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
        Spacer(Modifier.height(20.dp))
        Surface(Modifier.fillMaxWidth().height(310.dp), color = RematerialColors.Surface, shape = RoundedCornerShape(18.dp)) {
            AsyncImage(model = state.photoUri, contentDescription = "Pratinjau foto material", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(24.dp))
        RematerialButton("Mulai analisis", onStart, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowRight)
    }
}

@Composable
private fun ConfirmScreen(state: AnalysisUiState, onClose: () -> Unit, onCategoryChange: (MaterialCategory) -> Unit, onContinue: () -> Unit) {
    val prediction = state.prediction
    val selected = state.selectedCategory ?: prediction?.category ?: MaterialCategory.METAL
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
        RematerialTopBar("Konfirmasi material", onBack = onClose)
        Spacer(Modifier.height(18.dp))
        Text(if (state.isManual) "Kategori pilihanmu" else "Ini yang kami temukan", style = MaterialTheme.typography.headlineLarge, color = RematerialColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(if (state.isManual) "Kategori ini akan menjadi konteks untuk rekomendasi." else "Koreksi kategori jika hasil foto belum tepat.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
        Spacer(Modifier.height(22.dp))
        Surface(Modifier.fillMaxWidth(), color = RematerialColors.DeepForest, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(selected.displayName, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Surface)
                prediction?.let { Text("Keyakinan awal ${(it.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.BronzeSoft) }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Pilih kategori yang paling sesuai", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        MaterialCategory.entries.forEach { category ->
            val active = category == selected
            Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onCategoryChange(category) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (active) RematerialColors.DeepForest else RematerialColors.Surface).semantics { contentDescription = category.displayName })
                Spacer(Modifier.width(12.dp)); Text(category.displayName, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(16.dp))
        RematerialButton("Lanjutkan", onContinue, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowRight)
    }
}

@Composable
private fun InputScreen(state: AnalysisUiState, onClose: () -> Unit, onValueChange: (String, String) -> Unit, onSubmit: () -> Unit) {
    val fields = state.initial?.requestedFields.orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
        RematerialTopBar("Detail material", onBack = onClose)
        Spacer(Modifier.height(8.dp)); RematerialProgress((fields.indexOfFirst { state.values[it.id.value].isNullOrBlank() }.coerceAtLeast(0).toFloat() / fields.size.coerceAtLeast(1)), Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Text("Bantu kami memahami bahan", style = MaterialTheme.typography.headlineLarge, color = RematerialColors.Ink)
        Spacer(Modifier.height(8.dp)); Text("Jawabanmu membuat perhitungan dan rekomendasi lebih relevan.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
        Spacer(Modifier.height(20.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 14.dp)) {
            items(fields) { field -> FieldInput(field, state.values[field.id.value].orEmpty()) { onValueChange(field.id.value, it) } }
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9B3E32), modifier = Modifier.padding(vertical = 8.dp)) }
        RematerialButton("Lihat hasil", onSubmit, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowRight)
    }
}

@Composable
private fun FieldInput(field: com.rematerial.app.feature.analysis.domain.RequestedField, value: String, onChange: (String) -> Unit) {
    when (field.type) {
        InspectionFieldType.CHOICE -> Column {
            Text(field.label, style = MaterialTheme.typography.labelMedium, color = RematerialColors.Muted)
            Spacer(Modifier.height(4.dp))
            field.choices.forEach { choice ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onChange(choice) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (value == choice) RematerialColors.DeepForest else RematerialColors.Surface))
                    Spacer(Modifier.width(10.dp)); Text(choice.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        InspectionFieldType.BOOLEAN -> Column {
            Text(field.label, style = MaterialTheme.typography.labelMedium, color = RematerialColors.Muted)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange(if (value == "true") "false" else "true") }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(if (value == "true") RematerialColors.DeepForest else RematerialColors.Surface))
                Spacer(Modifier.width(10.dp)); Text(if (value == "true") "Ya" else "Tidak", style = MaterialTheme.typography.bodyMedium)
            }
        }
        else -> RematerialField(value, onChange, field.label, placeholder = field.unit?.let { "Nilai dalam ${it.displayName()}" }, keyboardOptions = KeyboardOptions(keyboardType = if (field.type == InspectionFieldType.DECIMAL || field.type == InspectionFieldType.WHOLE) KeyboardType.Number else KeyboardType.Text))
    }
}

private fun UnitCode.displayName(): String = when (this) {
    UnitCode.KG -> "kg"; UnitCode.G -> "gram"; UnitCode.M -> "meter"; UnitCode.CM -> "cm"; UnitCode.MM -> "mm"; UnitCode.M2 -> "m²"; UnitCode.PERCENT -> "%"; UnitCode.PCS -> "pcs"; UnitCode.L -> "liter"; UnitCode.NONE -> "nilai"
}

@Composable
private fun ResultScreen(state: AnalysisUiState, onClose: () -> Unit, onOpenArtisan: () -> Unit, onSelect: (String) -> Unit, onSave: () -> Unit) {
    val result = state.result ?: return
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
        item { RematerialTopBar("Hasil analisis", onBack = onClose) }
        item { Spacer(Modifier.height(18.dp)); Text("Material siap dipahami", style = MaterialTheme.typography.headlineLarge, color = RematerialColors.Ink); Spacer(Modifier.height(6.dp)); Text("${result.category.displayName} · keyakinan ${(result.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted); Spacer(Modifier.height(22.dp)) }
        item { ResultSection("Ringkasan", "Klasifikasi awal menunjukkan ${result.category.displayName.lowercase()} dengan bukti visual dan jawaban yang kamu berikan.") }
        item { Spacer(Modifier.height(12.dp)); ResultSection("Sains & bukti", result.science.joinToString("\n\n") { "${it.title}\nPrinsip: ${it.principle}\nSumber: ${it.sourceRefs.joinToString { source -> source.value }}\nInterpretasi: ${it.interpretation}\nBatasan: ${it.limitation}\nVerifikasi: ${it.recommendedVerification}" }) }
        item { Spacer(Modifier.height(12.dp)); ResultSection("Matematika", result.mathematics.joinToString("\n\n") { "Input: ${it.inputs.joinToString { input -> "${input.name} ${input.value} ${input.unit.displayName()}" }}\nFormula: ${it.formulaExpression}\nHasil: ${"%.2f".format(it.result)} ${it.unit.displayName()}\nBatasan: ${it.limitations}" }) }
        item { Spacer(Modifier.height(12.dp)); val safetyText = when (result.safety.outcome) { SafetyOutcome.ALLOW -> "Aman untuk eksplorasi produksi awal."; SafetyOutcome.CAUTION -> "Lanjut dengan pemeriksaan tambahan."; SafetyOutcome.BLOCK -> "Jangan diproses sebelum risiko ditangani." }; ResultSection("Keselamatan · ${result.safety.outcome.name}", safetyText + if (result.safety.reasons.isNotEmpty()) "\n\nAlasan: ${result.safety.reasons.joinToString()}" else "") }
        item { Spacer(Modifier.height(12.dp)); Text("Pilihan produk", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)) }
        if (result.productOptions.isEmpty()) item { ResultSection("Belum ada opsi", "Lengkapi verifikasi keselamatan sebelum mencari produk yang sesuai.") }
        items(result.productOptions) { option -> ProductOptionCard(option, option.optionId.value == state.selectedOptionId, { onSelect(option.optionId.value) }) }
        item { Spacer(Modifier.height(18.dp)); if (state.saved) Text("Tersimpan di daftar ide produksi.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.DeepForest); Spacer(Modifier.height(8.dp)); RematerialButton(if (state.saved) "Tersimpan" else "Simpan untuk dibuat sendiri", onSave, Modifier.fillMaxWidth(), enabled = !state.saved, leadingIcon = RematerialIcons.Hammer); Spacer(Modifier.height(10.dp)); RematerialButton("Buat di Pengrajin", onOpenArtisan, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.Store) }
    }
}

@Composable
private fun ResultSection(title: String, body: String) {
    Surface(Modifier.fillMaxWidth(), color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RematerialColors.Line)) { Column(Modifier.padding(18.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text(body, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted) } }
}

@Composable
private fun ProductOptionCard(option: ProductOption, selected: Boolean, onSelect: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onSelect), color = if (selected) RematerialColors.BronzeSoft else RematerialColors.Surface, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) RematerialColors.Bronze else RematerialColors.Line)) {
        Column(Modifier.padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(option.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text("${option.provisionalProductScore.toInt()}/100", style = MaterialTheme.typography.titleMedium, color = RematerialColors.DeepForest) }; Spacer(Modifier.height(8.dp)); Text(option.explanation, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(10.dp)); HorizontalDivider(color = RematerialColors.Line); Spacer(Modifier.height(10.dp)); Text("Butuh: ${option.requiredToolsText()} · ${option.requiredSkillIds.joinToString()}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }
    }
    Spacer(Modifier.height(10.dp))
}

private fun ProductOption.requiredToolsText(): String = requiredToolIds.joinToString { it.replace('-', ' ') }

@Composable
fun UserPlaceholderScreen(title: String, description: String, destination: DockDestination, onBack: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().background(RematerialColors.Canvas)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = 92.dp + bottom)) {
            RematerialTopBar(title, onBack = onBack)
            Spacer(Modifier.height(36.dp))
            Text(title, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink)
            Spacer(Modifier.height(10.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted)
            Spacer(Modifier.height(28.dp))
            ResultSection("Segera hadir", "Kami sedang menyiapkan pengalaman ${title.lowercase()} yang terhubung dengan data nyata. Kamu bisa kembali ke Beranda untuk menganalisis material lain.")
        }
        com.rematerial.app.core.designsystem.RematerialDock(destination, { selected -> if (selected == DockDestination.Beranda) onBack() }, Modifier.align(Alignment.BottomCenter))
    }
}
