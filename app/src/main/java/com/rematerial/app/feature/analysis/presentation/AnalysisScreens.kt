package com.rematerial.app.feature.analysis.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rematerial.app.R
import com.rematerial.app.core.designsystem.RematerialButton
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialField
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialProgress
import com.rematerial.app.core.designsystem.RematerialTopBar
import com.rematerial.app.core.media.MediaUriHelper
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.AnalysisId
import com.rematerial.app.core.model.SafetyOutcome
import com.rematerial.app.feature.analysis.domain.InspectionFieldType
import com.rematerial.app.feature.analysis.domain.ProductOption
import java.util.concurrent.Executors

@Composable
fun AnalysisRoute(
    onClose: () -> Unit,
    onOpenArtisan: (ProductOption, AnalysisId, SafetyOutcome) -> Unit,
    viewModel: AnalysisViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error = state.error
    val context = LocalContext.current
    var cameraOpen by rememberSaveable { mutableStateOf(false) }
    var cameraPermissionMessage by rememberSaveable { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { context.attachPickedPhoto(it, viewModel) } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) cameraOpen = true else cameraPermissionMessage = true }
    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraOpen = true
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    if (cameraOpen) {
        CameraScreen(
            onClose = { cameraOpen = false },
            onGallery = { cameraOpen = false; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onCaptured = { uri, type, size -> cameraOpen = false; viewModel.attachPhoto(uri.toString(), type, size) },
        )
        return
    }
    Box(Modifier.fillMaxSize().background(RematerialColors.Canvas)) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                slideInHorizontally { full -> full * direction } togetherWith slideOutHorizontally { full -> -full * direction }
            },
            label = "analysis-step",
        ) { step ->
            when (step) {
                AnalysisStep.SCAN -> ScanScreen(onClose, { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, ::openCamera, viewModel::chooseManual, cameraPermissionMessage) { cameraPermissionMessage = false }
                AnalysisStep.PREVIEW -> PreviewScreen(state, onClose, viewModel::startPhotoAnalysis)
                AnalysisStep.CONFIRM -> ConfirmScreen(state, onClose, viewModel::setCategory, viewModel::continueToInputs)
                AnalysisStep.INPUTS -> InputScreen(state, onClose, viewModel::updateValue, viewModel::submitInputs)
                AnalysisStep.RESULT -> ResultScreen(
                    state = state,
                    onClose = onClose,
                    onOpenArtisan = { option ->
                        val result = state.result ?: return@ResultScreen
                        onOpenArtisan(option, result.analysisId, result.safety.outcome)
                    },
                    onSelect = viewModel::selectOption,
                    onSave = viewModel::saveForMaking,
                )
            }
        }
        if (state.loading) {
            Surface(Modifier.align(Alignment.Center), color = RematerialColors.Surface, shape = RoundedCornerShape(18.dp), shadowElevation = 12.dp) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = RematerialColors.Bronze, strokeWidth = 2.dp)
                    Spacer(Modifier.width(14.dp)); Text("Membaca material...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (!state.loading && error != null) {
            AnalysisErrorBanner(
                message = error,
                onRetry = when (state.step) {
                    AnalysisStep.PREVIEW -> viewModel::startPhotoAnalysis
                    AnalysisStep.INPUTS -> viewModel::submitInputs
                    else -> viewModel::clearError
                },
            )
        }
    }
}

@Composable
private fun AnalysisErrorBanner(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp).align(Alignment.BottomCenter),
            color = Color(0xFFF4DDD7), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFD9A69A)),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = Color(0xFF73372F), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp)); Text("Coba lagi", style = MaterialTheme.typography.labelLarge, color = Color(0xFF73372F), modifier = Modifier.clickable(onClick = onRetry))
            }
        }
    }
}

private fun Context.attachPickedPhoto(uri: Uri, viewModel: AnalysisViewModel) {
    val type = contentResolver.getType(uri) ?: "image/jpeg"
    val queriedSize = runCatching {
        contentResolver.query(uri, arrayOf("_size"), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex("_size")
            if (cursor.moveToFirst() && index >= 0) cursor.getLong(index) else 0L
        } ?: 0L
    }.getOrDefault(0L)
    val size = queriedSize.takeIf { it > 0L }
        ?: runCatching { contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }.orZero() }.getOrDefault(0L)
    viewModel.attachPhoto(uri.toString(), type, size)
}

private fun Long?.orZero(): Long = this ?: 0L

@Composable
private fun ScanScreen(onClose: () -> Unit, onPick: () -> Unit, onCamera: () -> Unit, onManual: (MaterialCategory) -> Unit, cameraPermissionDenied: Boolean, onDismissPermission: () -> Unit) {
    var showManual by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { RematerialTopBar("Scan material", onBack = onClose) }
        item {
            Spacer(Modifier.height(8.dp)); Text("Beri materialmu arah baru", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink)
            Spacer(Modifier.height(8.dp)); Text("Foto dulu untuk mendapat ide pemakaian yang cocok, atau pilih kategori secara manual.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted)
        }
        item { ScanChoice("Ambil foto", "Kamera dengan panduan frame", RematerialIcons.Camera, onCamera) }
        item { ScanChoice("Pilih dari galeri", "Gunakan foto yang sudah ada", RematerialIcons.Upload, onPick) }
        item { ScanChoice("Pilih kategori manual", "Lanjut tanpa foto", RematerialIcons.Search, { showManual = true }) }
        if (cameraPermissionDenied) item {
            Surface(color = RematerialColors.BronzeSoft, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, RematerialColors.Bronze)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Kamera belum diizinkan", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp)); Text("Kamu tetap bisa memilih foto dari galeri atau lanjut manual.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
                    Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pilih galeri", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.clickable { onDismissPermission(); onPick() })
                        Text("Lanjut manual", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.clickable { onDismissPermission(); showManual = true })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)); Text("Agar foto mudah dibaca", style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink); Text("Gunakan cahaya cukup, ambil dari dekat, dan pastikan seluruh permukaan material terlihat.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted) }
        if (showManual) item {
            Surface(color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Kategori material", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
                    MaterialCategory.entries.forEach { category ->
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onManual(category) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(category.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); RematerialIcon(RematerialIcons.ArrowRight, null, Modifier.size(18.dp), RematerialColors.Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanChoice(title: String, supporting: String, icon: Int, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(46.dp), color = RematerialColors.BronzeSoft, shape = RoundedCornerShape(12.dp)) { Box(contentAlignment = Alignment.Center) { RematerialIcon(icon, null, Modifier.size(21.dp), RematerialColors.DeepForest) } }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(supporting, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }
            RematerialIcon(RematerialIcons.ArrowRight, null, Modifier.size(18.dp), RematerialColors.Muted)
        }
    }
}

@Composable
private fun CameraScreen(onClose: () -> Unit, onGallery: () -> Unit, onCaptured: (Uri, String, Long) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flashOn by rememberSaveable { mutableStateOf(false) }
    var cameraError by rememberSaveable { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val providerFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    LaunchedEffect(providerFuture, previewView) {
        val view = previewView ?: return@LaunchedEffect
        providerFuture.addListener({
            try {
                val provider = providerFuture.get(); provider.unbindAll()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                imageCapture = capture; cameraError = null
            } catch (_: Exception) { cameraError = "Kamera belum siap. Coba buka ulang kamera." }
        }, ContextCompat.getMainExecutor(context))
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF111312)).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        AndroidView(factory = { PreviewView(it).also { previewView = it } }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 88.dp).border(1.dp, Color.White.copy(alpha = .8f), RoundedCornerShape(26.dp)))
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CameraControl(RematerialIcons.X, "Tutup kamera", onClose); Spacer(Modifier.weight(1f))
                CameraControl(RematerialIcons.Flash, "Lampu kamera") { if (camera?.cameraInfo?.hasFlashUnit() == true) { flashOn = !flashOn; camera?.cameraControl?.enableTorch(flashOn) } }
            }
            Spacer(Modifier.weight(1f)); Text("Posisikan material di dalam frame", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp)); Text("Cahaya merata membantu hasil analisis lebih tepat", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                CameraControl(RematerialIcons.Image, "Buka galeri", onGallery)
                Surface(Modifier.size(76.dp).clickable(enabled = !capturing) {
                    val capture = imageCapture ?: run { cameraError = "Kamera belum siap. Coba lagi."; return@clickable }
                    val file = try { MediaUriHelper.newCameraFile(context) } catch (_: Exception) { cameraError = "Penyimpanan sementara tidak tersedia."; return@clickable }
                    capturing = true
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val size = file.length(); context.mainExecutor.execute { capturing = false; if (!file.exists() || size <= 0L) cameraError = "Foto kosong. Coba ambil ulang." else onCaptured(Uri.fromFile(file), "image/jpeg", size) }
                        }
                        override fun onError(exception: ImageCaptureException) { context.mainExecutor.execute { capturing = false; cameraError = "Foto belum tersimpan. Coba lagi." } }
                    })
                }, color = RematerialColors.Surface, shape = CircleShape, shadowElevation = 5.dp) {
                    Box(Modifier.fillMaxSize().padding(7.dp).border(2.dp, RematerialColors.DeepForest, CircleShape), contentAlignment = Alignment.Center) { if (capturing) CircularProgressIndicator(Modifier.size(24.dp), color = RematerialColors.DeepForest, strokeWidth = 2.dp) }
                }
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(16.dp))
            cameraError?.let { message ->
                Surface(color = RematerialColors.Surface, shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Ink, modifier = Modifier.weight(1f)); Text("Ulangi", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.clickable { cameraError = null }) } }
                Spacer(Modifier.height(10.dp))
            }
            Text("Tip: hindari bayangan dan permukaan yang terlalu memantul", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun CameraControl(icon: Int, description: String, onClick: () -> Unit) {
    Surface(Modifier.size(46.dp).clickable(role = Role.Button, onClick = onClick), color = Color.Black.copy(alpha = .38f), shape = CircleShape) { Box(contentAlignment = Alignment.Center) { RematerialIcon(icon, description, Modifier.size(21.dp), Color.White) } }
}

@Composable
private fun PreviewScreen(state: AnalysisUiState, onClose: () -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 22.dp, vertical = 14.dp)) {
        RematerialTopBar("Periksa foto", onBack = onClose); Spacer(Modifier.height(18.dp)); Text("Foto terlihat siap", style = MaterialTheme.typography.headlineLarge, color = RematerialColors.Ink)
        Spacer(Modifier.height(8.dp)); Text("Pastikan material terlihat jelas sebelum memulai analisis.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(20.dp))
        Surface(Modifier.fillMaxWidth().height(310.dp), color = RematerialColors.Surface, shape = RoundedCornerShape(18.dp)) { AsyncImage(model = state.photoUri, contentDescription = "Pratinjau foto material", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop) }
        Spacer(Modifier.height(14.dp)); if (state.photoSizeBytes <= 0L) Text("Ukuran foto belum terbaca; coba pilih foto lain bila analisis gagal.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
        Spacer(Modifier.weight(1f)); RematerialButton("Mulai analisis", onStart, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowRight)
    }
}

@Composable
private fun ConfirmScreen(state: AnalysisUiState, onClose: () -> Unit, onCategoryChange: (MaterialCategory) -> Unit, onContinue: () -> Unit) {
    val prediction = state.prediction; val selected = state.selectedCategory ?: prediction?.category ?: MaterialCategory.METAL
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 22.dp, vertical = 14.dp)) {
        RematerialTopBar("Konfirmasi material", onBack = onClose); Spacer(Modifier.height(18.dp)); Text(if (state.isManual) "Kategori pilihanmu" else "Ini yang kami temukan", style = MaterialTheme.typography.headlineLarge, color = RematerialColors.Ink)
        Spacer(Modifier.height(8.dp)); Text(if (state.isManual) "Kategori ini membantu kami memberi ide yang relevan." else "Koreksi kategori jika hasil foto belum tepat.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(22.dp))
        Surface(Modifier.fillMaxWidth(), color = RematerialColors.DeepForest, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(20.dp)) { Text(selected.displayName, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Surface); Text(AnalysisPresentation.categoryIntro(selected), style = MaterialTheme.typography.bodyMedium, color = RematerialColors.BronzeSoft); prediction?.let { Text("Keyakinan awal ${(it.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = RematerialColors.BronzeSoft, modifier = Modifier.padding(top = 12.dp)) } } }
        Spacer(Modifier.height(22.dp)); Text("Pilih kategori yang paling sesuai", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
        MaterialCategory.entries.forEach { category ->
            val active = category == selected
            Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onCategoryChange(category) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(20.dp).clip(CircleShape).background(if (active) RematerialColors.DeepForest else RematerialColors.Surface).border(1.dp, if (active) RematerialColors.DeepForest else RematerialColors.Line, CircleShape).semantics { contentDescription = category.displayName }); Spacer(Modifier.width(12.dp)); Text(category.displayName, style = MaterialTheme.typography.bodyLarge) }
        }
        Spacer(Modifier.weight(1f)); RematerialButton("Lanjutkan", onContinue, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowRight)
    }
}

@Composable
private fun InputScreen(state: AnalysisUiState, onClose: () -> Unit, onValueChange: (String, String) -> Unit, onSubmit: () -> Unit) {
    val fields = state.initial?.requestedFields.orEmpty(); val completed = fields.count { !state.values[it.id.value].isNullOrBlank() }; val progress = completed.toFloat() / fields.size.coerceAtLeast(1).toFloat()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 22.dp, vertical = 14.dp)) {
        RematerialTopBar("Detail material", onBack = onClose); Row(verticalAlignment = Alignment.CenterVertically) { RematerialProgress(progress, Modifier.weight(1f)); Spacer(Modifier.width(10.dp)); Text("$completed/${fields.size}", style = MaterialTheme.typography.labelMedium, color = RematerialColors.Muted) }
        Spacer(Modifier.height(18.dp)); Text("Bantu kami memahami bahan", style = MaterialTheme.typography.headlineLarge, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Jawabanmu membuat ide dan peringatan keselamatan lebih relevan.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(20.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 14.dp)) { items(fields) { field -> FieldInput(field, state.values[field.id.value].orEmpty()) { onValueChange(field.id.value, it) } } }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9B3E32), modifier = Modifier.padding(vertical = 8.dp)) }; RematerialButton("Lihat hasil", onSubmit, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowRight)
    }
}

@Composable
private fun FieldInput(field: com.rematerial.app.feature.analysis.domain.RequestedField, value: String, onChange: (String) -> Unit) {
    when (field.type) {
        InspectionFieldType.CHOICE -> Column { Text(field.label, style = MaterialTheme.typography.labelMedium, color = RematerialColors.Muted); Spacer(Modifier.height(4.dp)); field.choices.forEach { choice -> Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onChange(choice) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(18.dp).clip(CircleShape).background(if (value == choice) RematerialColors.DeepForest else RematerialColors.Surface).border(1.dp, RematerialColors.Line, CircleShape)); Spacer(Modifier.width(10.dp)); Text(AnalysisPresentation.choice(choice), style = MaterialTheme.typography.bodyMedium) } } }
        InspectionFieldType.BOOLEAN -> Column { Text(field.label, style = MaterialTheme.typography.labelMedium, color = RematerialColors.Muted); Spacer(Modifier.height(4.dp)); Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange(if (value == "true") "false" else "true") }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(if (value == "true") RematerialColors.DeepForest else RematerialColors.Surface).border(1.dp, RematerialColors.Line, RoundedCornerShape(5.dp))); Spacer(Modifier.width(10.dp)); Text(AnalysisPresentation.choice(if (value == "true") "true" else "false"), style = MaterialTheme.typography.bodyMedium) } }
        else -> RematerialField(value, onChange, field.label, placeholder = field.unit?.let { "Nilai dalam ${AnalysisPresentation.unit(it)}" }, keyboardOptions = KeyboardOptions(keyboardType = if (field.type == InspectionFieldType.DECIMAL || field.type == InspectionFieldType.WHOLE) KeyboardType.Decimal else KeyboardType.Text))
    }
}

@Composable
private fun ResultScreen(state: AnalysisUiState, onClose: () -> Unit, onOpenArtisan: (ProductOption) -> Unit, onSelect: (String) -> Unit, onSave: () -> Unit) {
    val result = state.result ?: return; var showTechnical by rememberSaveable { mutableStateOf(false) }; val selected = result.productOptions.firstOrNull { it.optionId.value == state.selectedOptionId }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp)) {
        item { RematerialTopBar("Hasil analisis", onBack = onClose) }
        item { Spacer(Modifier.height(14.dp)); state.photoUri?.let { uri -> AsyncImage(model = uri, contentDescription = "Foto material", modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop); Spacer(Modifier.height(16.dp)) }; Text(result.category.displayName, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Text("Keyakinan ${(result.confidence * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = RematerialColors.Bronze); Spacer(Modifier.height(6.dp)); Text(AnalysisPresentation.categoryIntro(result.category), style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted) }
        item { Spacer(Modifier.height(22.dp)); Text("Ide yang bisa kamu buat", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)) }
        if (result.productOptions.isEmpty()) item { ResultSection("Belum ada ide yang aman", "Lengkapi pemeriksaan keselamatan sebelum mencari bentuk baru untuk material ini.", RematerialColors.BronzeSoft) } else itemsIndexed(result.productOptions) { index, option -> ProductOptionCard(result.category, index, option, option.optionId.value == state.selectedOptionId) { onSelect(option.optionId.value) } }
        item { Spacer(Modifier.height(16.dp)); val safety = result.safety; Surface(Modifier.fillMaxWidth(), color = if (safety.outcome == SafetyOutcome.BLOCK) Color(0xFFF4DDD7) else RematerialColors.Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, RematerialColors.Line)) { Column(Modifier.padding(18.dp)) { Text("Keamanan penggunaan", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text(AnalysisPresentation.safetyTitle(safety.outcome), style = MaterialTheme.typography.titleLarge, color = if (safety.outcome == SafetyOutcome.BLOCK) Color(0xFF9B3E32) else RematerialColors.DeepForest); Spacer(Modifier.height(6.dp)); Text(AnalysisPresentation.safetyBody(safety.outcome), style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); safety.reasons.forEach { reason -> Text("• $reason", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted, modifier = Modifier.padding(top = 8.dp)) } } } }
        item { Spacer(Modifier.height(12.dp)); ResultSection("Yang kami lihat", result.science.joinToString("\n\n") { "${it.title}\n${it.interpretation}\n\nBatasannya: ${it.limitation}" }, RematerialColors.Surface) }
        item { Spacer(Modifier.height(12.dp)); Surface(Modifier.fillMaxWidth().clickable { showTechnical = !showTechnical }, color = RematerialColors.BronzeSoft, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(18.dp)) { Text(if (showTechnical) "Sembunyikan penjelasan teknis" else "Lihat penjelasan teknis", style = MaterialTheme.typography.titleMedium, color = RematerialColors.DeepForest); if (showTechnical) { Spacer(Modifier.height(12.dp)); result.science.forEach { finding -> Text("Dasar pemeriksaan", style = MaterialTheme.typography.labelMedium, color = RematerialColors.Muted); Text("${finding.principle}\nReferensi: ${finding.sourceRefs.joinToString { AnalysisPresentation.source(it.value) }}\nVerifikasi: ${finding.recommendedVerification}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Ink, modifier = Modifier.padding(top = 4.dp)) }; result.mathematics.forEach { calculation -> Spacer(Modifier.height(10.dp)); Text("Perkiraan jumlah", style = MaterialTheme.typography.labelMedium, color = RematerialColors.Muted); Text("${calculation.inputs.joinToString { "${it.name}: ${it.value} ${AnalysisPresentation.unit(it.unit)}" }}\nRumus: ${calculation.formulaExpression}\nHasil: ${"%.2f".format(calculation.result)} ${AnalysisPresentation.unit(calculation.unit)}\nCatatan: ${calculation.limitations}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Ink, modifier = Modifier.padding(top = 4.dp)) } } } } }
        item { Spacer(Modifier.height(18.dp)); if (state.saved) Text("Tersimpan di daftar ide produksi.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.DeepForest); Spacer(Modifier.height(8.dp)); RematerialButton(if (state.saved) "Tersimpan" else "Simpan ide ini", onSave, Modifier.fillMaxWidth(), enabled = selected != null && !state.saved, leadingIcon = RematerialIcons.Hammer); Spacer(Modifier.height(10.dp)); selected?.let { option -> RematerialButton("Buat di pengrajin", { onOpenArtisan(option) }, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.Store) }; if (selected == null && result.productOptions.isNotEmpty()) Text("Pilih satu ide dulu untuk melanjutkan.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun ResultSection(title: String, body: String, color: Color) { Surface(Modifier.fillMaxWidth(), color = color, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, RematerialColors.Line)) { Column(Modifier.padding(18.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text(body, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted) } } }

@Composable
private fun ProductOptionCard(category: MaterialCategory, index: Int, option: ProductOption, selected: Boolean, onSelect: () -> Unit) {
    val image = when ((index + category.ordinal) % 3) { 0 -> R.drawable.material_metal; 1 -> R.drawable.material_wood; else -> R.drawable.material_textile }
    Surface(Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onSelect), color = if (selected) RematerialColors.BronzeSoft else RematerialColors.Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (selected) RematerialColors.Bronze else RematerialColors.Line)) {
        Column { AsyncImage(model = image, contentDescription = option.title, modifier = Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)), contentScale = ContentScale.Crop); Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(option.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text("${option.provisionalProductScore.toInt()}/100", style = MaterialTheme.typography.titleMedium, color = RematerialColors.DeepForest) }; Spacer(Modifier.height(7.dp)); Text(option.explanation, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(10.dp)); HorizontalDivider(color = RematerialColors.Line); Spacer(Modifier.height(10.dp)); Text("Mulai dari ${option.minimumQuantity} ${AnalysisPresentation.unit(option.minimumUnit)} ${option.requiredMaterial.lowercase()}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Ink); Text("Alat: ${option.requiredToolIds.joinToString { AnalysisPresentation.tool(it) }}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Text("Keterampilan: ${option.requiredSkillIds.joinToString { AnalysisPresentation.skill(it) }}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) } }
    }
    Spacer(Modifier.height(10.dp))
}
