package com.rematerial.app.feature.marketplace.presentation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rematerial.app.core.designsystem.DockDestination
import com.rematerial.app.core.designsystem.RematerialButton
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialDock
import com.rematerial.app.core.designsystem.RematerialField
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialListRow
import com.rematerial.app.core.designsystem.RematerialTopBar
import com.rematerial.app.feature.marketplace.domain.CartLine
import com.rematerial.app.feature.marketplace.domain.CheckoutDraft
import com.rematerial.app.feature.marketplace.domain.MarketplaceOrder
import com.rematerial.app.feature.marketplace.domain.MarketplaceProduct
import com.rematerial.app.feature.marketplace.domain.OrderStatus

private enum class MarketPage { HOME, DETAIL, CART, CHECKOUT, SUCCESS, ORDERS, ORDER_DETAIL }

@Composable
fun MarketplaceRoute(
    onDestinationSelected: (DockDestination) -> Unit,
    viewModel: MarketplaceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pageName by rememberSaveable { mutableStateOf(MarketPage.HOME.name) }
    var selectedOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    val page = MarketPage.valueOf(pageName)
    val go = { target: MarketPage -> pageName = target.name }
    Box(Modifier.fillMaxSize().background(RematerialColors.Canvas)) {
        when (page) {
            MarketPage.HOME -> MarketHomeScreen(state, viewModel::search, viewModel::category, viewModel::open, { go(MarketPage.CART) })
            MarketPage.DETAIL -> state.selectedProduct?.let { ProductDetailScreen(it, { viewModel.closeProduct(); go(MarketPage.HOME) }, { viewModel.add(it); go(MarketPage.CART) }) } ?: MarketHomeScreen(state, viewModel::search, viewModel::category, viewModel::open, { go(MarketPage.CART) })
            MarketPage.CART -> CartScreen(state.cart, state.sellerSwitchPrompt, viewModel::increment, viewModel::decrement, viewModel::remove, viewModel::dismissPrompt, { state.selectedProduct?.let(viewModel::confirmSellerSwitch) }, { go(MarketPage.CHECKOUT) }, { go(MarketPage.HOME) })
            MarketPage.CHECKOUT -> CheckoutScreen(state.cart, { viewModel.placeOrder(it); go(MarketPage.SUCCESS) }, { go(MarketPage.CART) })
            MarketPage.SUCCESS -> OrderSuccessScreen(state.lastOrder, { viewModel.clearLastOrder(); go(MarketPage.HOME) }, { go(MarketPage.ORDERS) })
            MarketPage.ORDERS -> OrderHistoryScreen(state.orders, { go(MarketPage.HOME) }, { order -> selectedOrderId = order.id; go(MarketPage.ORDER_DETAIL) })
            MarketPage.ORDER_DETAIL -> state.orders.firstOrNull { it.id == selectedOrderId }?.let { OrderDetailScreen(it, { go(MarketPage.ORDERS) }) } ?: OrderHistoryScreen(state.orders, { go(MarketPage.HOME) }, { order -> selectedOrderId = order.id; go(MarketPage.ORDER_DETAIL) })
        }
        if (page == MarketPage.HOME) {
            RematerialDock(DockDestination.Pasar, onDestinationSelected, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
fun MarketplaceOrdersRoute(onBack: () -> Unit, viewModel: MarketplaceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.orders.firstOrNull { it.id == selectedId }
    if (selected == null) OrderHistoryScreen(state.orders, onBack) { selectedId = it.id }
    else OrderDetailScreen(selected, { selectedId = null })
}

@Composable
private fun MarketHomeScreen(state: MarketplaceState, onSearch: (String) -> Unit, onCategory: (String?) -> Unit, onProduct: (MarketplaceProduct) -> Unit, onCart: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = 82.dp + bottom)) {
        RematerialTopBar("Pasar", actionIcon = RematerialIcons.ShoppingBag, actionDescription = "Keranjang", onAction = onCart)
        Spacer(Modifier.height(18.dp))
        Text("Benda dengan\ncerita baru.", style = MaterialTheme.typography.displayLarge, color = RematerialColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text("Temukan karya yang memberi umur kedua pada material.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted)
        Spacer(Modifier.height(20.dp))
        RematerialField(state.query, onSearch, "Cari di pasar", placeholder = "Cari produk atau material", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            CategoryLink("Semua", state.category == null) { onCategory(null) }
            listOf("Logam", "Kayu", "Tekstil").forEach { category -> CategoryLink(category, state.category == category) { onCategory(category) } }
        }
        Spacer(Modifier.height(24.dp))
        if (state.products.isEmpty()) {
            Text("Belum ada karya yang cocok.", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink)
            Spacer(Modifier.height(6.dp)); Text("Coba kata kunci atau kategori lain.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
        } else {
            Text(if (state.query.isBlank() && state.category == null) "Pilihan minggu ini" else "Hasil pencarian", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                items(state.products, key = { it.id }) { ProductCard(it, onProduct) }
            }
        }
    }
}

@Composable private fun CategoryLink(label: String, active: Boolean, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) RematerialColors.DeepForest else RematerialColors.Muted, modifier = Modifier.clickable(role = Role.Tab, onClick = onClick).padding(vertical = 8.dp).semantics { contentDescription = "Filter kategori $label" })
}

@Composable private fun ProductCard(product: MarketplaceProduct, onClick: (MarketplaceProduct) -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onClick(product) }, color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
        Column {
            Image(painterResource(product.imageRes), product.title, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Column(Modifier.padding(16.dp)) {
                Text(product.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = RematerialColors.Bronze)
                Spacer(Modifier.height(5.dp)); Text(product.title, style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink)
                Spacer(Modifier.height(4.dp)); Text("${product.seller.name} · ${product.location}", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted)
                Spacer(Modifier.height(10.dp)); Text(rupiah(product.price), style = MaterialTheme.typography.titleMedium, color = RematerialColors.DeepForest)
            }
        }
    }
}

@Composable private fun ProductDetailScreen(product: MarketplaceProduct, onBack: () -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        RematerialTopBar("Detail karya", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Spacer(Modifier.height(8.dp)); Image(painterResource(product.imageRes), product.title, Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Spacer(Modifier.height(20.dp)); Text(product.title, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text(rupiah(product.price), style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest)
                Spacer(Modifier.height(18.dp)); DetailLine("Asal material", product.materialOrigin); DetailLine("Cerita pembuat", "${product.seller.name} · ${product.seller.location}\n${product.seller.story}"); DetailLine("Ketersediaan", "${product.stock} tersisa · ${product.fulfillment}"); DetailLine("Pengiriman", "${product.location} · dikemas dengan bahan minim plastik")
                Spacer(Modifier.height(4.dp)); Text(product.description, style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted); Spacer(Modifier.height(22.dp)); RematerialButton("Tambah ke keranjang", onAdd, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ShoppingCart)
            }
        }
    }
}

@Composable private fun DetailLine(title: String, value: String) { Column(Modifier.padding(bottom = 17.dp)) { Text(title, style = MaterialTheme.typography.labelLarge, color = RematerialColors.Muted); Spacer(Modifier.height(5.dp)); Text(value, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Ink) } }

@Composable private fun CartScreen(lines: List<CartLine>, prompt: Boolean, onPlus: (String, Int) -> Unit, onMinus: (String, Int) -> Unit, onRemove: (String) -> Unit, onDismissPrompt: () -> Unit, onConfirmSwitch: () -> Unit, onCheckout: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        RematerialTopBar("Keranjang", onBack = onBack)
        if (lines.isEmpty()) { Spacer(Modifier.height(46.dp)); Text("Keranjang masih kosong.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Simpan karya yang ingin kamu bawa pulang.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted); return@Column }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 18.dp)) { items(lines, key = { it.product.id }) { line -> CartLineRow(line, onPlus, onMinus, onRemove) } }
        if (prompt) {
            Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), color = RematerialColors.BronzeSoft, border = BorderStroke(1.dp, RematerialColors.Bronze), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(14.dp)) { Text("Karya ini dari studio lain.", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(4.dp)); Text("Keranjang hanya bisa berisi satu studio agar pengiriman tetap sederhana.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Text("Batal", Modifier.clickable(onClick = onDismissPrompt).padding(10.dp), style = MaterialTheme.typography.labelLarge); Text("Ganti studio", Modifier.clickable(onClick = onConfirmSwitch).padding(10.dp), style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest) } } }
        }
        val total = lines.sumOf { it.product.price * it.quantity }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text(rupiah(total), style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest) }
        RematerialButton("Lanjut ke checkout", onCheckout, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowRight)
    }
}

@Composable private fun CartLineRow(line: CartLine, onPlus: (String, Int) -> Unit, onMinus: (String, Int) -> Unit, onRemove: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(line.product.imageRes), line.product.title, Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(line.product.title, style = MaterialTheme.typography.titleMedium); Text(rupiah(line.product.price), style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { SmallIcon(RematerialIcons.Minus, "Kurangi") { onMinus(line.product.id, line.quantity) }; Text("  ${line.quantity}  ", style = MaterialTheme.typography.labelLarge); SmallIcon(RematerialIcons.Plus, "Tambah") { onPlus(line.product.id, line.quantity) } } }
        Text("Hapus", style = MaterialTheme.typography.labelSmall, color = RematerialColors.Muted, modifier = Modifier.clickable(onClick = { onRemove(line.product.id) }).padding(8.dp))
    }
}

@Composable private fun SmallIcon(icon: Int, description: String, onClick: () -> Unit) { Box(Modifier.size(32.dp).clip(CircleShape).background(RematerialColors.Surface).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) { RematerialIcon(icon, description, Modifier.size(16.dp), RematerialColors.DeepForest) } }

@Composable private fun CheckoutScreen(lines: List<CartLine>, onPlace: (CheckoutDraft) -> Unit, onBack: () -> Unit) {
    var address by rememberSaveable { mutableStateOf("Jl. Riau No. 18, Bandung") }; var delivery by rememberSaveable { mutableStateOf("Reguler · 2–4 hari") }; var payment by rememberSaveable { mutableStateOf("Transfer bank demo") }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        RematerialTopBar("Checkout", onBack = onBack); LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) { item { Spacer(Modifier.height(18.dp)); Text("Hampir selesai.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Semua pilihan di bawah masih demo dan belum terhubung ke pembayaran nyata.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(22.dp)); RematerialField(address, { address = it }, "Alamat pengiriman"); Spacer(Modifier.height(18.dp)); SelectionField("Pengiriman", delivery, listOf("Reguler · 2–4 hari", "Ekonomis · 4–7 hari")) { delivery = it }; Spacer(Modifier.height(18.dp)); SelectionField("Metode pembayaran", payment, listOf("Transfer bank demo", "Dompet digital demo")) { payment = it }; Spacer(Modifier.height(20.dp)); Text("Ringkasan pesanan", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); lines.forEach { Text("${it.quantity}× ${it.product.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)) }; Spacer(Modifier.height(20.dp)); RematerialButton("Buat pesanan", { onPlace(CheckoutDraft(address, delivery, payment)) }, Modifier.fillMaxWidth(), enabled = address.isNotBlank(), leadingIcon = RematerialIcons.Check) } }
    }
}

@Composable private fun SelectionField(title: String, selected: String, options: List<String>, onSelect: (String) -> Unit) { Column { Text(title, style = MaterialTheme.typography.labelMedium, color = RematerialColors.Muted); Spacer(Modifier.height(8.dp)); options.forEach { option -> Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onSelect(option) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { RematerialIcon(if (option == selected) RematerialIcons.CircleCheck else RematerialIcons.Circle, null, Modifier.size(20.dp), if (option == selected) RematerialColors.DeepForest else RematerialColors.Line); Spacer(Modifier.width(10.dp)); Text(option, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Ink) } } } }

@Composable private fun OrderSuccessScreen(order: MarketplaceOrder?, onHome: () -> Unit, onOrders: () -> Unit) { Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 36.dp)) { RematerialIcon(RematerialIcons.CircleCheck, null, Modifier.size(34.dp), RematerialColors.DeepForest); Spacer(Modifier.height(26.dp)); Text("Pesanan dibuat.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Karya pilihanmu akan diproses oleh studio dengan tenang.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted); Spacer(Modifier.height(28.dp)); order?.let { DetailLine("Nomor pesanan", it.id); DetailLine("Total", rupiah(it.total)); DetailLine("Studio", it.lines.firstOrNull()?.product?.seller?.name.orEmpty()) }; Spacer(Modifier.height(16.dp)); RematerialButton("Lihat pesanan", onOrders, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.Receipt); Spacer(Modifier.height(10.dp)); RematerialButton("Kembali ke pasar", onHome, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowLeft) } }

@Composable private fun OrderHistoryScreen(orders: List<MarketplaceOrder>, onBack: () -> Unit, onOpen: (MarketplaceOrder) -> Unit) { Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp)) { RematerialTopBar("Pesanan", onBack = onBack); Spacer(Modifier.height(18.dp)); Text("Perjalanan belanjamu.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Riwayat pesanan dari studio yang kamu pilih.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(22.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) { items(orders, key = { it.id }) { order -> Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(order) }, color = RematerialColors.Surface, border = BorderStroke(1.dp, RematerialColors.Line), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp)) { Row { Text(order.id, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text(order.status.label, style = MaterialTheme.typography.bodySmall, color = RematerialColors.DeepForest) }; Spacer(Modifier.height(6.dp)); Text(order.lines.joinToString { "${it.quantity}× ${it.product.title}" }, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Spacer(Modifier.height(8.dp)); Text(rupiah(order.total), style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink) } } } } } }

@Composable private fun OrderDetailScreen(order: MarketplaceOrder, onBack: () -> Unit) { Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp)) { RematerialTopBar("Detail pesanan", onBack = onBack); LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { item { Spacer(Modifier.height(18.dp)); Text(order.id, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text(order.createdLabel, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(24.dp)); OrderTimeline(order.status); Spacer(Modifier.height(26.dp)); DetailLine("Dikirim ke", order.address); DetailLine("Pengiriman", order.delivery); DetailLine("Pembayaran", order.payment); DetailLine("Total", rupiah(order.total)) } } } }

@Composable private fun OrderTimeline(status: OrderStatus) { Column { Text("Status pesanan", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(10.dp)); OrderStatus.entries.forEach { item -> Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { RematerialIcon(if (item.ordinal <= status.ordinal) RematerialIcons.CircleCheck else RematerialIcons.CircleCheck, null, Modifier.size(19.dp), if (item.ordinal <= status.ordinal) RematerialColors.DeepForest else RematerialColors.Line); Spacer(Modifier.width(10.dp)); Text(item.label, style = MaterialTheme.typography.bodyMedium, color = if (item.ordinal <= status.ordinal) RematerialColors.Ink else RematerialColors.Muted) } } } }

@Composable
fun UserAccountRoute(
    onBack: () -> Unit,
    onAnalysis: () -> Unit,
    onProduction: () -> Unit,
    onOrders: () -> Unit,
    onDestinationSelected: (DockDestination) -> Unit,
    onLogout: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = 94.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
            RematerialTopBar("Akun", onBack = onBack)
            Spacer(Modifier.height(22.dp))
            Text("Dika", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink)
            Text("user@rematerial.demo", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
            Spacer(Modifier.height(28.dp))
            Text("Ruangmu", style = MaterialTheme.typography.titleLarge)
            AccountRow("Analisis material", "Lihat material yang pernah dipetakan", RematerialIcons.Camera, onAnalysis)
            AccountRow("Produksi", "Pantau karya yang sedang dibuat", RematerialIcons.Hammer, onProduction)
            AccountRow("Pesanan pasar", "Riwayat pembelian dan pengiriman", RematerialIcons.Receipt, onOrders)
            Spacer(Modifier.height(24.dp))
            Text("Akun siap digunakan.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
            Spacer(Modifier.height(24.dp))
            RematerialButton("Keluar dari akun", onLogout, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.LogOut)
        }
        RematerialDock(DockDestination.Akun, onDestinationSelected, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable private fun AccountRow(title: String, supporting: String, icon: Int, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { RematerialIcon(icon, null, Modifier.size(21.dp), RematerialColors.DeepForest); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(supporting, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; RematerialIcon(RematerialIcons.ChevronRight, null, Modifier.size(18.dp), RematerialColors.Muted) } }

private fun rupiah(value: Int): String = "Rp" + "%,d".format(value).replace(',', '.')
