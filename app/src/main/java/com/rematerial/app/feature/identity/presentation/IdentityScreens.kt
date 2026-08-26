package com.rematerial.app.feature.identity.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.rematerial.app.core.designsystem.RematerialButton
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialField
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialTopBar
import com.rematerial.app.feature.identity.domain.ContactPreference
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.VerificationDocumentKind

@Composable
fun IdentityEntryScreen(
    viewModel: IdentityViewModel,
    onSignedIn: (Role) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.session) {
        state.session?.role?.let(onSignedIn)
    }
    if (state.role == null) {
        RoleSelectionScreen { viewModel.onEvent(IdentityEvent.SelectRole(it)) }
    } else {
        LoginScreen(
            state = state,
            onEvent = viewModel::onEvent,
        )
    }
}

private data class RoleOption(
    val role: Role,
    val title: String,
    val description: String,
    val icon: Int,
)

private val roleOptions = listOf(
    RoleOption(Role.USER, "Pengguna", "Analisis bahan dan pilih produk.", RematerialIcons.UserRound),
    RoleOption(Role.ARTISAN, "Pengrajin", "Wujudkan karya yang sudah dipetakan.", RematerialIcons.Hammer),
    RoleOption(Role.SELLER, "Penjual", "Tawarkan karya ke orang yang tepat.", RematerialIcons.Store),
)

@Composable
private fun RoleSelectionScreen(onRoleSelected: (Role) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        Text("ReMaterial", style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest)
        Spacer(Modifier.height(58.dp))
        Text("Pilih jenis akun", style = MaterialTheme.typography.headlineLarge, color = RematerialColors.Ink)
        Spacer(Modifier.height(10.dp))
        Text(
            "Satu ruang untuk memahami bahan, membuat karya, dan menemukan pasarnya.",
            style = MaterialTheme.typography.bodyLarge,
            color = RematerialColors.Muted,
        )
        Spacer(Modifier.height(36.dp))
        Text("Masuk sebagai", style = MaterialTheme.typography.labelLarge, color = RematerialColors.Muted)
        Spacer(Modifier.height(12.dp))
        roleOptions.forEach { option ->
            RoleOptionRow(option = option, onClick = { onRoleSelected(option.role) })
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(34.dp))
        Text(
            "Admin dikelola melalui dashboard internal.",
            style = MaterialTheme.typography.bodySmall,
            color = RematerialColors.Muted,
        )
    }
}

@Composable
private fun RoleOptionRow(option: RoleOption, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clickable(role = SemanticsRole.Button, onClick = onClick)
            .semantics { contentDescription = "Pilih ${option.title}" },
        color = RematerialColors.Surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, RematerialColors.Line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                color = RematerialColors.BronzeSoft,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RematerialIcon(option.icon, null, Modifier.size(21.dp), RematerialColors.DeepForest)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(option.title, style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink)
                Spacer(Modifier.height(3.dp))
                Text(option.description, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
            }
            RematerialIcon(RematerialIcons.ArrowRight, "Pilih", Modifier.size(19.dp), RematerialColors.Bronze)
        }
    }
}

@Composable
private fun LoginScreen(
    state: IdentityState,
    onEvent: (IdentityEvent) -> Unit,
) {
    val roleTitle = roleOptions.first { it.role == state.role }.title
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onEvent(IdentityEvent.LocationPermissionChanged(result[Manifest.permission.ACCESS_COARSE_LOCATION] == true))
    }
    val ktpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onEvent(IdentityEvent.VerificationDocumentSelected(VerificationDocumentKind.KTP, it.toString())) }
    }
    val selfieLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onEvent(IdentityEvent.VerificationDocumentSelected(VerificationDocumentKind.SELFIE, it.toString())) }
    }
    val evidenceKind = if (state.role == Role.ARTISAN) VerificationDocumentKind.PORTFOLIO else VerificationDocumentKind.STORE_EVIDENCE
    val evidenceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onEvent(IdentityEvent.VerificationDocumentSelected(evidenceKind, it.toString())) }
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) onEvent(IdentityEvent.LocationPermissionChanged(true))
    }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        RematerialTopBar(title = "Akun $roleTitle", onBack = { onEvent(IdentityEvent.ChangeRole) })
        Spacer(Modifier.height(38.dp))
        Text(
            if (state.isRegistering) "Buat ruang kerjamu." else "Selamat datang kembali.",
            style = MaterialTheme.typography.displaySmall,
            color = RematerialColors.Ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (state.isRegistering) "Mulai perjalanan bahanmu bersama ReMaterial." else "Masuk untuk melanjutkan perjalanan bahanmu.",
            style = MaterialTheme.typography.bodyLarge,
            color = RematerialColors.Muted,
        )
        Spacer(Modifier.height(30.dp))
        if (state.isRegistering) {
            RematerialField(
                value = state.displayName,
                onValueChange = { onEvent(IdentityEvent.DisplayNameChanged(it)) },
                label = "Nama",
                placeholder = "Nama yang ingin ditampilkan",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(16.dp))
            RematerialField(
                value = state.phone,
                onValueChange = { onEvent(IdentityEvent.PhoneChanged(it)) },
                label = "Nomor WhatsApp",
                placeholder = "08xxxxxxxxxx",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(16.dp))
            RematerialField(
                value = state.whatsapp,
                onValueChange = { onEvent(IdentityEvent.WhatsappChanged(it)) },
                label = "WhatsApp lain (opsional)",
                placeholder = "Kosongkan jika sama",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Kontak utama: ${if (state.preferredContact == ContactPreference.WHATSAPP) "WhatsApp" else "Telepon"}",
                style = MaterialTheme.typography.bodySmall,
                color = RematerialColors.DeepForest,
                modifier = Modifier.clickable {
                    onEvent(IdentityEvent.PreferredContactChanged(if (state.preferredContact == ContactPreference.WHATSAPP) ContactPreference.TELEPON else ContactPreference.WHATSAPP))
                }.padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(10.dp))
            Surface(color = RematerialColors.Surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Lokasi untuk rekomendasi terdekat", style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink)
                    Spacer(Modifier.height(5.dp))
                    Text("Lokasi dipakai untuk mencari pengrajin di sekitar area kamu. Izin ditolak? Isi area manual saja.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
                    Spacer(Modifier.height(12.dp))
                    RematerialButton(
                        "Gunakan lokasi saya",
                        { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) },
                        Modifier.fillMaxWidth(),
                        enabled = !state.isResolvingLocation,
                        leadingIcon = RematerialIcons.MapPin,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            state.isResolvingLocation -> "Mencari perkiraan lokasi perangkat…"
                            state.latitude != null -> "Perkiraan lokasi perangkat berhasil dibaca."
                            state.locationPermissionGranted -> "Izin tersedia, tetapi koordinat belum terbaca. Isi area manual bila perlu."
                            else -> "Belum ada lokasi perangkat."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = RematerialColors.Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    RematerialField(state.area, { onEvent(IdentityEvent.AreaChanged(it)) }, "Area atau kota", placeholder = "Contoh: Bandung", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next))
                    Spacer(Modifier.height(12.dp))
                    RematerialField(state.address, { onEvent(IdentityEvent.AddressChanged(it)) }, "Alamat (opsional)", placeholder = "Area cukup untuk mulai", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state.role != Role.USER) {
                Text("Verifikasi ${if (state.role == Role.ARTISAN) "pengrajin" else "penjual"}", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink)
                Spacer(Modifier.height(8.dp))
                Text("Dokumen disalin ke penyimpanan privat aplikasi dan akun baru akan berstatus menunggu verifikasi.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
                Spacer(Modifier.height(14.dp))
                RematerialField(
                    state.nik,
                    { onEvent(IdentityEvent.NikChanged(it)) },
                    "NIK",
                    placeholder = "16 digit NIK",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(10.dp))
                VerificationDocumentRow("Foto KTP", state.ktpPrivatePath != null) { ktpLauncher.launch(arrayOf("image/*")) }
                VerificationDocumentRow("Foto selfie", state.selfiePrivatePath != null) { selfieLauncher.launch(arrayOf("image/*")) }
                VerificationDocumentRow(if (state.role == Role.ARTISAN) "Foto portofolio" else "Bukti toko", state.roleEvidencePrivatePath != null) { evidenceLauncher.launch(arrayOf("image/*")) }
                Spacer(Modifier.height(16.dp))
            }
        }
        RematerialField(
            value = state.email,
            onValueChange = { onEvent(IdentityEvent.EmailChanged(it)) },
            label = "Email",
            placeholder = "nama@email.com",
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(16.dp))
        RematerialField(
            value = state.password,
            onValueChange = { onEvent(IdentityEvent.PasswordChanged(it)) },
            label = "Kata sandi",
            placeholder = "Minimal 8 karakter",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (state.isRegistering) ImeAction.Next else ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus(); onEvent(IdentityEvent.Submit) }),
        )
        if (state.isRegistering) {
            Spacer(Modifier.height(16.dp))
            RematerialField(
                value = state.confirmPassword,
                onValueChange = { onEvent(IdentityEvent.ConfirmPasswordChanged(it)) },
                label = "Konfirmasi kata sandi",
                placeholder = "Ulangi kata sandi",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus(); onEvent(IdentityEvent.Submit) }),
            )
        }
        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9B3F2F))
        }
        Spacer(Modifier.height(24.dp))
        Box {
            RematerialButton(
                text = if (state.isRegistering) "Buat akun" else "Masuk",
                onClick = { onEvent(IdentityEvent.Submit) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(22.dp),
                    color = RematerialColors.Surface,
                    strokeWidth = 2.dp,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.isRegistering) "Sudah punya akun?" else "Belum punya akun?",
                style = MaterialTheme.typography.bodySmall,
                color = RematerialColors.Muted,
            )
            Text(
                if (state.isRegistering) " Masuk" else " Daftar sebagai pengguna",
                style = MaterialTheme.typography.labelLarge,
                color = RematerialColors.DeepForest,
                modifier = Modifier
                    .clickable { onEvent(IdentityEvent.ToggleRegistration) }
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun VerificationDocumentRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = SemanticsRole.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RematerialIcon(RematerialIcons.Upload, null, Modifier.size(20.dp), RematerialColors.DeepForest)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink)
            Text(if (selected) "Tersimpan privat" else "Pilih foto", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
        }
        RematerialIcon(if (selected) RematerialIcons.Check else RematerialIcons.ChevronRight, null, Modifier.size(18.dp), RematerialColors.Bronze)
    }
}
