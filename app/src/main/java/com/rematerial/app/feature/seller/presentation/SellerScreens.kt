package com.rematerial.app.feature.seller.presentation

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.rematerial.app.core.designsystem.HorizontalPageMotion
import com.rematerial.app.core.designsystem.RematerialDockMetrics
import com.rematerial.app.core.designsystem.RematerialField
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialTopBar
import com.rematerial.app.feature.marketplace.domain.OrderStatus
import com.rematerial.app.feature.seller.domain.SellerListing
import com.rematerial.app.feature.seller.domain.SellerOrder
import com.rematerial.app.feature.seller.domain.SellerProfile
import com.rematerial.app.core.commerce.ListingState
import com.rematerial.app.feature.identity.domain.Session
import com.rematerial.app.feature.identity.domain.VerificationStatus
import com.rematerial.app.feature.seller.domain.VerificationState

private enum class SellerPage { HOME, PRODUCTS, FORM, LISTING, ORDERS, ORDER, ACCOUNT, ONBOARDING, SETTINGS }
private enum class SellerTab(val label: String, val icon: Int) { HOME("Beranda", RematerialIcons.Hammer), PRODUCTS("Produk", RematerialIcons.Package), ADD("Tambah Produk", RematerialIcons.Plus), ORDERS("Pesanan", RematerialIcons.Receipt), ACCOUNT("Akun", RematerialIcons.UserRound) }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SellerWorkspaceRoute(session: Session, onLogout: () -> Unit, viewModel: SellerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle(); var pageName by rememberSaveable { mutableStateOf(SellerPage.HOME.name) }; var motionName by rememberSaveable { mutableStateOf(HorizontalPageMotion.FORWARD.name) }; val page = SellerPage.valueOf(pageName); val motion = HorizontalPageMotion.valueOf(motionName); val go = { value: SellerPage -> motionName = if (sellerIsBackward(page, value)) HorizontalPageMotion.BACKWARD.name else HorizontalPageMotion.FORWARD.name; pageName = value.name }
    BackHandler(enabled = page != SellerPage.HOME) {
        if (page == SellerPage.FORM || page == SellerPage.LISTING) viewModel.selectListing(null)
        if (page == SellerPage.ORDER) viewModel.selectOrder(null)
        go(sellerParent(page))
    }
    LaunchedEffect(page, state.selectedListing, state.selectedOrder) {
        when {
            page == SellerPage.LISTING && state.selectedListing == null -> go(SellerPage.PRODUCTS)
            page == SellerPage.ORDER && state.selectedOrder == null -> go(SellerPage.ORDERS)
            page != SellerPage.LISTING && page != SellerPage.FORM && state.selectedListing != null -> viewModel.selectListing(null)
            page != SellerPage.ORDER && state.selectedOrder != null -> viewModel.selectOrder(null)
        }
    }
    Box(Modifier.fillMaxSize().background(RematerialColors.Canvas).imePadding()) {
        AnimatedContent(
            modifier = if (sellerNeedsRootInset(page)) Modifier.fillMaxSize().navigationBarsPadding() else Modifier.fillMaxSize(),
            targetState = page,
            transitionSpec = {
                if (motion == HorizontalPageMotion.FORWARD) {
                    slideInHorizontally(tween(210)) { it } togetherWith slideOutHorizontally(tween(210)) { -it }
                } else {
                    slideInHorizontally(tween(210)) { -it } togetherWith slideOutHorizontally(tween(210)) { it }
                }
            },
            label = "seller-page-transition",
        ) { currentPage ->
            when (currentPage) {
                SellerPage.HOME -> SellerDashboard(session.displayName, state, { go(SellerPage.ORDERS) }, { go(SellerPage.PRODUCTS) }, { go(SellerPage.ACCOUNT) })
                SellerPage.PRODUCTS -> SellerProductsScreen(state.listings, { go(SellerPage.HOME) }, { viewModel.selectListing(it); go(SellerPage.LISTING) }, { viewModel.selectListing(null); go(SellerPage.FORM) })
                SellerPage.FORM -> SellerListingForm(state.selectedListing, viewModel::importListingImage, { viewModel.selectListing(null); go(SellerPage.PRODUCTS) }, { listing -> viewModel.saveListing(listing) { viewModel.selectListing(null); go(SellerPage.PRODUCTS) } })
                SellerPage.LISTING -> state.selectedListing?.let { SellerListingDetail(it, { viewModel.selectListing(null); go(SellerPage.PRODUCTS) }, { viewModel.toggleListing(it.id, it.state == ListingState.PUBLISHED); viewModel.selectListing(null); go(SellerPage.PRODUCTS) }, { viewModel.archiveListing(it.id); viewModel.selectListing(null); go(SellerPage.PRODUCTS) }, { go(SellerPage.FORM) }) } ?: SellerProductsScreen(state.listings, { go(SellerPage.HOME) }, { viewModel.selectListing(it); go(SellerPage.LISTING) }, { viewModel.selectListing(null); go(SellerPage.FORM) })
                SellerPage.ORDERS -> SellerOrdersScreen(state.orders, { go(SellerPage.HOME) }, { viewModel.selectOrder(it); go(SellerPage.ORDER) })
                SellerPage.ORDER -> state.selectedOrder?.let { SellerOrderDetail(it, { viewModel.selectOrder(null); go(SellerPage.ORDERS) }, { target -> viewModel.transitionOrder(it.id, target) }) } ?: SellerOrdersScreen(state.orders, { go(SellerPage.HOME) }, { viewModel.selectOrder(it); go(SellerPage.ORDER) })
                SellerPage.ACCOUNT -> SellerAccountScreen(state.profile, session.verificationStatus, { go(SellerPage.HOME) }, { go(SellerPage.ONBOARDING) }, { go(SellerPage.SETTINGS) })
                SellerPage.ONBOARDING -> SellerOnboardingScreen(state.profile, session.verificationStatus) { go(SellerPage.ACCOUNT) }
                SellerPage.SETTINGS -> SellerSettingsScreen(state.profile, session.email, { go(SellerPage.ACCOUNT) }, onLogout)
            }
        }
        state.errorMessage?.let { message ->
            Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp).fillMaxWidth().clickable { viewModel.clearError() }, color = Color(0xFFFFF4EF), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFC46A4A))) {
                Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7D3425))
            }
        }
        if (sellerDockVisible(page)) SellerDock(page, go)
    }
}

private fun sellerParent(page: SellerPage): SellerPage = when (page) { SellerPage.PRODUCTS, SellerPage.ORDERS, SellerPage.ACCOUNT -> SellerPage.HOME; SellerPage.FORM, SellerPage.LISTING -> SellerPage.PRODUCTS; SellerPage.ORDER -> SellerPage.ORDERS; SellerPage.ONBOARDING, SellerPage.SETTINGS -> SellerPage.ACCOUNT; SellerPage.HOME -> SellerPage.HOME }
private fun sellerDockVisible(page: SellerPage): Boolean = page == SellerPage.HOME || page == SellerPage.PRODUCTS || page == SellerPage.ORDERS || page == SellerPage.ACCOUNT
private fun sellerNeedsRootInset(page: SellerPage): Boolean = page == SellerPage.ORDER || page == SellerPage.ONBOARDING || page == SellerPage.SETTINGS
private fun sellerTabPosition(page: SellerPage): Int? = when (page) { SellerPage.HOME -> 0; SellerPage.PRODUCTS -> 1; SellerPage.ORDERS -> 2; SellerPage.ACCOUNT -> 3; else -> null }
private fun sellerIsBackward(current: SellerPage, target: SellerPage): Boolean = if (target == sellerParent(current) && current != SellerPage.HOME) true else sellerTabPosition(current)?.let { from -> sellerTabPosition(target)?.let { it < from } } ?: false

@Composable private fun SellerDock(page: SellerPage, go: (SellerPage) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = RematerialDockMetrics.horizontalPadding).padding(top = RematerialDockMetrics.outerVerticalPadding, bottom = RematerialDockMetrics.bottomGap), color = RematerialColors.Glass, shape = RoundedCornerShape(30.dp), shadowElevation = 16.dp, border = BorderStroke(1.dp, Color.White.copy(alpha = .8f))) {
            Row(Modifier.fillMaxWidth().height(RematerialDockMetrics.surfaceHeight).selectableGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                SellerTab.entries.forEach { tab ->
                    if (tab == SellerTab.ADD) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(RematerialColors.DeepForest).clickable(role = Role.Button) { go(SellerPage.FORM) }.semantics { contentDescription = "Tambah produk" }, contentAlignment = Alignment.Center) { RematerialIcon(tab.icon, null, Modifier.size(25.dp), RematerialColors.Surface) }
                    } else {
                        val target = when (tab) { SellerTab.HOME -> SellerPage.HOME; SellerTab.PRODUCTS -> SellerPage.PRODUCTS; SellerTab.ORDERS -> SellerPage.ORDERS; SellerTab.ACCOUNT -> SellerPage.ACCOUNT; SellerTab.ADD -> SellerPage.FORM }
                        val active = target == page
                        Column(Modifier.width(68.dp).height(RematerialDockMetrics.surfaceHeight).selectable(selected = active, role = Role.Tab) { go(target) }.padding(vertical = 7.dp).semantics { contentDescription = tab.label }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { RematerialIcon(tab.icon, null, Modifier.size(20.dp), if (active) RematerialColors.DeepForest else RematerialColors.Muted); Spacer(Modifier.height(4.dp)); Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (active) RematerialColors.DeepForest else RematerialColors.Muted) }
                    }
                }
            }
        }
    }
}

@Composable private fun SellerDashboard(displayName: String, state: SellerState, onOrders: () -> Unit, onProducts: () -> Unit, onAccount: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val next = state.orders.firstOrNull { it.status == OrderStatus.PLACED }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = RematerialDockMetrics.contentBottomPadding(bottom)),
    ) {
        item {
            RematerialTopBar("Ruang Penjual", actionIcon = RematerialIcons.UserRound, actionDescription = "Akun penjual", onAction = onAccount)
            Spacer(Modifier.height(20.dp)); Text("Selamat datang, $displayName.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
            Spacer(Modifier.height(5.dp)); Text("Buat studio tetap bergerak.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink)
            Spacer(Modifier.height(28.dp)); Row(Modifier.fillMaxWidth()) {
                DashboardMetric("${state.orders.count { it.status != OrderStatus.DELIVERED }}", "Pesanan aktif", Modifier.weight(1f))
                DashboardMetric("${state.listings.count { it.state == ListingState.PUBLISHED }}", "Produk tayang", Modifier.weight(1f))
                DashboardMetric(rupiah(state.orders.filter { it.status == OrderStatus.DELIVERED }.sumOf { it.total }), "Penjualan selesai", Modifier.weight(1f))
            }
            Spacer(Modifier.height(30.dp)); Text("Langkah berikutnya", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(10.dp))
            Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOrders), color = RematerialColors.DeepForest, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(if (next == null) "Semua tertangani" else "Konfirmasi pesanan ${next.id}", style = MaterialTheme.typography.titleMedium, color = RematerialColors.Surface)
                    Spacer(Modifier.height(5.dp)); Text(if (next == null) "Studio siap menerima karya baru." else "${next.productTitle} · ${next.buyerName}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.BronzeSoft)
                    Spacer(Modifier.height(14.dp)); Text(if (next == null) "Buka pesanan" else "Tinjau sekarang", style = MaterialTheme.typography.labelLarge, color = RematerialColors.BronzeSoft)
                }
            }
            Spacer(Modifier.height(28.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Produk terbaru", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("Lihat semua", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.sizeIn(minHeight = 48.dp).clickable(onClick = onProducts).padding(8.dp))
            }
        }
        items(state.listings.take(2), key = { it.id }) { listing ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SellerListingImage(listing, Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(listing.title, style = MaterialTheme.typography.titleMedium); Text("${listing.stock} stok · ${listing.state.label}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }
                Text(rupiah(listing.price), style = MaterialTheme.typography.bodySmall, color = RematerialColors.DeepForest)
            }
        }
    }
}

@Composable private fun DashboardMetric(value: String, label: String, modifier: Modifier) { Column(modifier.padding(end = 8.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall, color = RematerialColors.DeepForest); Spacer(Modifier.height(3.dp)); Text(label, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) } }

@Composable private fun SellerProductsScreen(listings: List<SellerListing>, onBack: () -> Unit, onOpen: (SellerListing) -> Unit, onAdd: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 14.dp, bottom = RematerialDockMetrics.contentBottomPadding(bottom))) {
        item {
            RematerialTopBar("Produk", onBack = onBack); Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Katalog studio.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Text("Kelola karya yang terlihat di pasar.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted) }
                Text("Tambah", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.sizeIn(minHeight = 48.dp).clickable(onClick = onAdd).padding(8.dp))
            }
        }
        if (listings.isEmpty()) item { Text("Belum ada produk. Tambahkan karya pertama studio.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted) }
        items(listings, key = { it.id }) { listing ->
            Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(listing) }, color = RematerialColors.Surface, border = BorderStroke(1.dp, RematerialColors.Line), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SellerListingImage(listing, Modifier.size(72.dp).clip(RoundedCornerShape(9.dp)))
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(listing.title, style = MaterialTheme.typography.titleMedium); Text(listing.materialOrigin, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Spacer(Modifier.height(5.dp)); Text("${listing.stock} stok · ${listing.state.label}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.DeepForest) }
                    Text(rupiah(listing.price), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable private fun SellerListingDetail(listing: SellerListing, onBack: () -> Unit, onToggle: () -> Unit, onArchive: () -> Unit, onEdit: () -> Unit) { Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) { RematerialTopBar("Detail produk", onBack = onBack, actionIcon = RematerialIcons.Pencil, actionDescription = "Edit produk", onAction = onEdit); LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { item { Spacer(Modifier.height(12.dp)); SellerListingImage(listing, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp))); Spacer(Modifier.height(20.dp)); Text(listing.title, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text(rupiah(listing.price), style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest); Spacer(Modifier.height(22.dp)); SellerDetail("Asal material", listing.materialOrigin); SellerDetail("Deskripsi", listing.description); SellerDetail("Stok", "${listing.stock} unit"); SellerDetail("Pemenuhan", "${listing.fulfillment} · ${listing.location}"); Spacer(Modifier.height(12.dp)); if (listing.state != ListingState.ARCHIVED) { RematerialButton(if (listing.state == ListingState.PUBLISHED) "Jedaikan produk" else "Terbitkan produk", onToggle, Modifier.fillMaxWidth(), leadingIcon = if (listing.state == ListingState.PUBLISHED) RematerialIcons.Pause else RematerialIcons.Check); Spacer(Modifier.height(10.dp)); Text("Arsipkan produk", style = MaterialTheme.typography.labelLarge, color = RematerialColors.Muted, modifier = Modifier.fillMaxWidth().clickable(onClick = onArchive).padding(14.dp)) } else Text("Produk telah diarsipkan.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted) } } } }

@Composable private fun SellerListingForm(existing: SellerListing?, onImportImage: (String, (String) -> Unit) -> Unit, onBack: () -> Unit, onSave: (SellerListing) -> Unit) { var draft by androidx.compose.runtime.remember(existing?.id) { mutableStateOf(existing ?: SellerListing(id = "p-${System.currentTimeMillis()}", title = "", materialOrigin = "", description = "", price = 0, stock = 0, fulfillment = "Dikirim dalam 3–5 hari", location = "Bandung")) }; val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onImportImage(it.toString()) { ownedUri -> draft = draft.copy(imageUri = ownedUri) } } }; Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) { RematerialTopBar(if (existing == null) "Tambah produk" else "Edit produk", onBack = onBack); LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) { item { Spacer(Modifier.height(16.dp)); Text("Ceritakan karyamu.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Detail sederhana membantu pembeli memilih dengan yakin.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(20.dp)); Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { picker.launch(arrayOf("image/*")) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { RematerialIcon(RematerialIcons.Image, null, Modifier.size(22.dp), RematerialColors.DeepForest); Spacer(Modifier.width(12.dp)); Text(if (draft.imageUri == null && draft.imageRes == null) "Pilih foto produk" else "Foto produk tersimpan privat", style = MaterialTheme.typography.titleMedium) }; Spacer(Modifier.height(12.dp)); RematerialField(draft.title, { draft = draft.copy(title = it) }, "Nama produk", placeholder = "Contoh: Lampu Tembaga Senja"); Spacer(Modifier.height(16.dp)); RematerialField(draft.materialOrigin, { draft = draft.copy(materialOrigin = it) }, "Asal material", placeholder = "Dari mana material ini diselamatkan?"); Spacer(Modifier.height(16.dp)); RematerialField(draft.description, { draft = draft.copy(description = it) }, "Deskripsi", placeholder = "Ceritakan karakter karya"); Spacer(Modifier.height(16.dp)); RematerialField(if (draft.price == 0) "" else draft.price.toString(), { draft = draft.copy(price = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, "Harga (Rupiah)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); Spacer(Modifier.height(16.dp)); RematerialField(if (draft.stock == 0) "" else draft.stock.toString(), { draft = draft.copy(stock = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, "Stok", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); Spacer(Modifier.height(16.dp)); RematerialField(draft.fulfillment, { draft = draft.copy(fulfillment = it) }, "Pemenuhan"); Spacer(Modifier.height(16.dp)); RematerialField(draft.location, { draft = draft.copy(location = it) }, "Lokasi studio"); Spacer(Modifier.height(22.dp)); RematerialButton("Simpan dan terbitkan", { onSave(draft.copy(state = ListingState.PUBLISHED)) }, Modifier.fillMaxWidth(), enabled = draft.title.isNotBlank() && draft.price > 0 && draft.stock > 0, leadingIcon = RematerialIcons.Check); Spacer(Modifier.height(8.dp)); Text("Simpan sebagai draft", style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest, modifier = Modifier.fillMaxWidth().clickable { onSave(draft.copy(state = ListingState.DRAFT)) }.padding(14.dp)) } } } }

@Composable private fun SellerOrdersScreen(orders: List<SellerOrder>, onBack: () -> Unit, onOpen: (SellerOrder) -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 14.dp, bottom = RematerialDockMetrics.contentBottomPadding(bottom))) {
        item { RematerialTopBar("Pesanan", onBack = onBack); Spacer(Modifier.height(18.dp)); Text("Pesanan masuk.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Satu per satu, sampai karya tiba.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(10.dp)) }
        if (orders.isEmpty()) item { Text("Belum ada pesanan masuk.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted) }
        items(orders, key = { it.id }) { order ->
            Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(order) }, color = RematerialColors.Surface, border = BorderStroke(1.dp, RematerialColors.Line), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) { Row { Text(order.id, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text(order.status.label, style = MaterialTheme.typography.bodySmall, color = RematerialColors.DeepForest) }; Spacer(Modifier.height(6.dp)); Text("${order.productTitle} · ${order.buyerName}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Spacer(Modifier.height(6.dp)); Text(rupiah(order.total), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable private fun SellerOrderDetail(order: SellerOrder, onBack: () -> Unit, onTransition: (OrderStatus) -> Unit) { val next = when (order.status) { OrderStatus.PLACED -> OrderStatus.CONFIRMED; OrderStatus.CONFIRMED -> OrderStatus.PROCESSING; OrderStatus.PROCESSING -> OrderStatus.READY_TO_SHIP; OrderStatus.READY_TO_SHIP -> OrderStatus.SHIPPED; else -> null }; Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) { RematerialTopBar("Detail pesanan", onBack = onBack); LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { item { Spacer(Modifier.height(18.dp)); Text(order.id, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("${order.productTitle} · ${order.buyerName}", style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.height(8.dp)); Text("WhatsApp ${order.buyerWhatsapp}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.DeepForest); Spacer(Modifier.height(22.dp)); Text("Status pesanan", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(10.dp)); OrderStatus.entries.filter { it != OrderStatus.CANCELLED }.forEach { status -> val reached = status.ordinal <= order.status.ordinal && order.status != OrderStatus.CANCELLED; Row(Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).clickable(enabled = status == next) { onTransition(status) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RematerialIcon(if (reached) RematerialIcons.CircleCheck else RematerialIcons.Circle, null, Modifier.size(19.dp), if (reached) RematerialColors.DeepForest else if (status == next) RematerialColors.Bronze else RematerialColors.Line); Spacer(Modifier.width(10.dp)); Text(status.label, style = MaterialTheme.typography.bodyMedium, color = if (reached) RematerialColors.Ink else if (status == next) RematerialColors.DeepForest else RematerialColors.Muted) } }; if (next == null && order.status == OrderStatus.SHIPPED) Text("Menunggu pembeli mengonfirmasi barang diterima.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(22.dp)); SellerDetail("Jumlah", "${order.quantity} unit"); SellerDetail("Total", rupiah(order.total)); SellerDetail("Alamat pembeli", order.address) } } } }

@Composable private fun SellerAccountScreen(profile: SellerProfile, verificationStatus: VerificationStatus, onBack: () -> Unit, onOnboarding: () -> Unit, onSettings: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 14.dp, bottom = RematerialDockMetrics.contentBottomPadding(bottom))) {
        item { RematerialTopBar("Akun", onBack = onBack); Spacer(Modifier.height(22.dp)); Text(profile.storeName, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Text("${profile.name} · ${profile.location}", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(28.dp)); Text("Kelola studio", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(6.dp)); AccountSetting("Verifikasi penjual", "${verificationStatus.displayLabel()} · dokumen tersimpan privat", RematerialIcons.UserRound, onOnboarding); AccountSetting("Pengaturan", "Notifikasi dan keamanan", RematerialIcons.Settings, onSettings) }
    }
}
@Composable private fun AccountSetting(title: String, supporting: String, icon: Int, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { RematerialIcon(icon, null, Modifier.size(21.dp), RematerialColors.DeepForest); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(supporting, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; RematerialIcon(RematerialIcons.ChevronRight, null, Modifier.size(18.dp), RematerialColors.Muted) } }

@Composable private fun SellerOnboardingScreen(profile: SellerProfile, verificationStatus: VerificationStatus, onBack: () -> Unit) { Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) { RematerialTopBar("Verifikasi penjual", onBack = onBack); Spacer(Modifier.height(24.dp)); Text(profile.storeName, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(10.dp)); Text(verificationStatus.displayLabel(), style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest); Spacer(Modifier.height(16.dp)); Text("KTP, selfie, dan bukti toko disalin ke penyimpanan privat saat pendaftaran. ReMaterial tidak menampilkan kembali isi dokumen pada layar ini.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted); Spacer(Modifier.height(24.dp)); Text(if (verificationStatus == VerificationStatus.APPROVED) "Studio dapat menerbitkan produk." else "Produk hanya dapat disimpan sebagai draft sampai verifikasi selesai.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Ink) } }
@Composable private fun SellerSettingsScreen(profile: SellerProfile, email: String, onBack: () -> Unit, onLogout: () -> Unit) { Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp)) { RematerialTopBar("Pengaturan", onBack = onBack); Spacer(Modifier.height(24.dp)); Text(profile.storeName, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Text(email, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(28.dp)); SellerInfoRow("Preferensi pesanan", "Pemenuhan dan notifikasi", RematerialIcons.Package); SellerInfoRow("Bantuan", "Panduan mengelola toko", RematerialIcons.Receipt); Spacer(Modifier.height(28.dp)); RematerialButton("Keluar dari akun", onLogout, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.LogOut) } }
@Composable private fun SellerInfoRow(title: String, supporting: String, icon: Int) { Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { RematerialIcon(icon, null, Modifier.size(21.dp), RematerialColors.DeepForest); Spacer(Modifier.width(14.dp)); Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(supporting, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) } } }
@Composable private fun SellerDetail(title: String, value: String) { Column(Modifier.padding(bottom = 16.dp)) { Text(title, style = MaterialTheme.typography.labelLarge, color = RematerialColors.Muted); Spacer(Modifier.height(5.dp)); Text(value, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Ink) } }
private fun rupiah(value: Int): String = "Rp" + "%,d".format(value).replace(',', '.')
private fun VerificationStatus.displayLabel(): String = when (this) { VerificationStatus.NOT_REQUIRED -> "Tidak diperlukan"; VerificationStatus.PENDING -> "Sedang ditinjau"; VerificationStatus.APPROVED -> "Terverifikasi"; VerificationStatus.NEEDS_CORRECTION -> "Perlu koreksi" }

@Composable private fun SellerListingImage(listing: SellerListing, modifier: Modifier) {
    when {
        listing.imageUri != null -> AsyncImage(listing.imageUri, listing.title, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        listing.imageRes != null -> Image(painterResource(listing.imageRes), listing.title, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else -> Box(modifier.background(RematerialColors.BronzeSoft), contentAlignment = Alignment.Center) { RematerialIcon(RematerialIcons.Image, null, Modifier.size(25.dp), RematerialColors.Bronze) }
    }
}
