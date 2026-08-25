package com.rematerial.app.feature.marketplace.presentation

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import com.rematerial.app.core.designsystem.RematerialDockMetrics
import com.rematerial.app.core.designsystem.HorizontalPageMotion
import com.rematerial.app.core.designsystem.RematerialField
import com.rematerial.app.core.designsystem.RematerialIcon
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialListRow
import com.rematerial.app.core.designsystem.RematerialTopBar
import com.rematerial.app.feature.marketplace.domain.CartLine
import com.rematerial.app.feature.marketplace.domain.BuyerContext
import com.rematerial.app.feature.marketplace.domain.CheckoutDraft
import com.rematerial.app.feature.marketplace.domain.MarketplaceOrder
import com.rematerial.app.feature.marketplace.domain.MarketplaceProduct
import com.rematerial.app.feature.marketplace.domain.OrderStatus
import com.rematerial.app.feature.identity.domain.Session

private enum class MarketPage { HOME, DETAIL, CART, CHECKOUT, SUCCESS, ORDERS, ORDER_DETAIL }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MarketplaceRoute(
    buyerContext: BuyerContext = BuyerContext(),
    shippingAddress: String = "",
    viewModel: MarketplaceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pageName by rememberSaveable { mutableStateOf(MarketPage.HOME.name) }
    var motionName by rememberSaveable { mutableStateOf(HorizontalPageMotion.FORWARD.name) }
    var selectedOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    val page = MarketPage.valueOf(pageName)
    val motion = HorizontalPageMotion.valueOf(motionName)
    val go: (MarketPage, Boolean) -> Unit = { target, isBack ->
        motionName = if (isBack) HorizontalPageMotion.BACKWARD.name else HorizontalPageMotion.FORWARD.name
        pageName = target.name
    }
    BackHandler(enabled = page != MarketPage.HOME) {
        val parent = when (page) {
            MarketPage.DETAIL, MarketPage.CART, MarketPage.ORDERS -> MarketPage.HOME
            MarketPage.CHECKOUT -> MarketPage.CART
            MarketPage.SUCCESS -> MarketPage.HOME
            MarketPage.ORDER_DETAIL -> MarketPage.ORDERS
            MarketPage.HOME -> MarketPage.HOME
        }
        if (page == MarketPage.DETAIL) viewModel.closeProduct()
        if (page == MarketPage.ORDER_DETAIL) selectedOrderId = null
        go(parent, true)
    }
    LaunchedEffect(page, state.selectedProduct, selectedOrderId, state.orders) {
        when {
            page == MarketPage.DETAIL && state.selectedProduct == null -> go(MarketPage.HOME, true)
            page == MarketPage.ORDER_DETAIL && state.orders.none { it.id == selectedOrderId } -> {
                selectedOrderId = null
                go(MarketPage.ORDERS, true)
            }
            page == MarketPage.SUCCESS && state.lastOrder == null -> go(MarketPage.HOME, true)
            page != MarketPage.DETAIL && page != MarketPage.CART && state.selectedProduct != null -> viewModel.closeProduct()
            page != MarketPage.ORDER_DETAIL && selectedOrderId != null -> selectedOrderId = null
        }
    }
    Box(Modifier.fillMaxSize().background(RematerialColors.Canvas)) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (motion == HorizontalPageMotion.FORWARD) {
                    slideInHorizontally(tween(210)) { it } togetherWith slideOutHorizontally(tween(210)) { -it }
                } else {
                    slideInHorizontally(tween(210)) { -it } togetherWith slideOutHorizontally(tween(210)) { it }
                }
            },
            label = "market-page-transition",
        ) { currentPage ->
            when (currentPage) {
                MarketPage.HOME -> MarketHomeScreen(state, viewModel::search, viewModel::category, { product -> viewModel.open(product); go(MarketPage.DETAIL, false) }, { go(MarketPage.CART, false) })
                MarketPage.DETAIL -> state.selectedProduct?.let { ProductDetailScreen(it, { viewModel.closeProduct(); go(MarketPage.HOME, true) }, { viewModel.add(it) { go(MarketPage.CART, false) } }) } ?: MarketHomeScreen(state, viewModel::search, viewModel::category, { product -> viewModel.open(product); go(MarketPage.DETAIL, false) }, { go(MarketPage.CART, false) })
                MarketPage.CART -> CartScreen(state.cart, state.sellerSwitchPrompt, viewModel::increment, viewModel::decrement, viewModel::remove, viewModel::dismissPrompt, { state.selectedProduct?.let(viewModel::confirmSellerSwitch) }, { go(MarketPage.CHECKOUT, false) }, { go(MarketPage.HOME, true) })
                MarketPage.CHECKOUT -> CheckoutScreen(state.cart, shippingAddress, { checkout -> viewModel.placeOrder(checkout, buyerContext) { go(MarketPage.SUCCESS, false) } }, { go(MarketPage.CART, true) })
                MarketPage.SUCCESS -> state.lastOrder?.let { order -> OrderSuccessScreen(order, { viewModel.clearLastOrder(); go(MarketPage.HOME, true) }, { go(MarketPage.ORDERS, false) }) }
                    ?: MarketHomeScreen(state, viewModel::search, viewModel::category, { product -> viewModel.open(product); go(MarketPage.DETAIL, false) }, { go(MarketPage.CART, false) })
                MarketPage.ORDERS -> OrderHistoryScreen(state.orders, { go(MarketPage.HOME, true) }, { order -> selectedOrderId = order.id; go(MarketPage.ORDER_DETAIL, false) }, dockVisible = true)
                MarketPage.ORDER_DETAIL -> state.orders.firstOrNull { it.id == selectedOrderId }?.let { order -> OrderDetailScreen(order, { selectedOrderId = null; go(MarketPage.ORDERS, true) }, { viewModel.cancelOrder(order.id) }, { viewModel.confirmReceipt(order.id) }, dockVisible = true) } ?: OrderHistoryScreen(state.orders, { go(MarketPage.HOME, true) }, { order -> selectedOrderId = order.id; go(MarketPage.ORDER_DETAIL, false) }, dockVisible = true)
            }
        }
        state.errorMessage?.let { message ->
            Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp).fillMaxWidth().clickable { viewModel.clearError() }, color = Color(0xFFFFF4EF), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFC46A4A))) {
                Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7D3425))
            }
        }
        if (state.isMutating) Surface(Modifier.fillMaxSize(), color = RematerialColors.Canvas.copy(alpha = .72f)) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RematerialColors.DeepForest) }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MarketplaceOrdersRoute(onBack: () -> Unit, viewModel: MarketplaceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var motionName by rememberSaveable { mutableStateOf(HorizontalPageMotion.FORWARD.name) }
    val motion = HorizontalPageMotion.valueOf(motionName)
    val closeDetail = { motionName = HorizontalPageMotion.BACKWARD.name; selectedId = null }
    BackHandler(enabled = selectedId != null, onBack = closeDetail)
    LaunchedEffect(selectedId, state.orders) {
        if (selectedId != null && state.orders.none { it.id == selectedId }) closeDetail()
    }
    AnimatedContent(
        targetState = selectedId,
        transitionSpec = {
            if (motion == HorizontalPageMotion.FORWARD) slideInHorizontally(tween(210)) { it } togetherWith slideOutHorizontally(tween(210)) { -it }
            else slideInHorizontally(tween(210)) { -it } togetherWith slideOutHorizontally(tween(210)) { it }
        },
        label = "standalone-orders-transition",
    ) { currentId ->
        val selected = state.orders.firstOrNull { it.id == currentId }
        if (selected == null) OrderHistoryScreen(state.orders, onBack, { motionName = HorizontalPageMotion.FORWARD.name; selectedId = it.id }, dockVisible = false)
        else OrderDetailScreen(selected, closeDetail, { viewModel.cancelOrder(selected.id) }, { viewModel.confirmReceipt(selected.id) }, dockVisible = false)
    }
}

@Composable
private fun MarketHomeScreen(state: MarketplaceState, onSearch: (String) -> Unit, onCategory: (String?) -> Unit, onProduct: (MarketplaceProduct) -> Unit, onCart: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = RematerialDockMetrics.contentBottomPadding(bottom)),
    ) {
        item {
            RematerialTopBar("Pasar", actionIcon = RematerialIcons.ShoppingBag, actionDescription = "Keranjang", onAction = onCart)
            Spacer(Modifier.height(18.dp))
            Text("Benda dengan\ncerita baru.", style = MaterialTheme.typography.displayLarge, color = RematerialColors.Ink)
            Spacer(Modifier.height(8.dp))
            Text("Temukan karya yang memberi umur kedua pada material.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted)
            Spacer(Modifier.height(20.dp))
            RematerialField(state.query, onSearch, "Cari di pasar", placeholder = "Cari produk atau material", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
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
            }
        }
        items(state.products, key = { it.id }) { ProductCard(it, onProduct) }
    }
}

@Composable private fun CategoryLink(label: String, active: Boolean, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) RematerialColors.DeepForest else RematerialColors.Muted, modifier = Modifier.sizeIn(minHeight = 48.dp).selectable(selected = active, role = Role.Tab, onClick = onClick).padding(vertical = 8.dp).semantics { contentDescription = "Filter kategori $label" })
}

@Composable private fun ProductCard(product: MarketplaceProduct, onClick: (MarketplaceProduct) -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onClick(product) }, color = RematerialColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, RematerialColors.Line)) {
        Column {
            MarketplaceProductImage(product, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
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
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))) {
        RematerialTopBar("Detail karya", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Spacer(Modifier.height(8.dp)); MarketplaceProductImage(product, Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.height(20.dp)); Text(product.title, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text(rupiah(product.price), style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest)
                Spacer(Modifier.height(18.dp)); DetailLine("Asal material", product.materialOrigin); DetailLine("Cerita pembuat", "${product.seller.name} · ${product.seller.location}\n${product.seller.story}"); DetailLine("Ketersediaan", "${product.stock} tersisa · ${product.fulfillment}"); DetailLine("Dikirim dari", product.location)
                Spacer(Modifier.height(4.dp)); Text(product.description, style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted); Spacer(Modifier.height(22.dp)); RematerialButton("Tambah ke keranjang", onAdd, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ShoppingCart)
            }
        }
    }
}

@Composable private fun DetailLine(title: String, value: String) { Column(Modifier.padding(bottom = 17.dp)) { Text(title, style = MaterialTheme.typography.labelLarge, color = RematerialColors.Muted); Spacer(Modifier.height(5.dp)); Text(value, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Ink) } }

@Composable private fun CartScreen(lines: List<CartLine>, prompt: Boolean, onPlus: (String, Int) -> Unit, onMinus: (String, Int) -> Unit, onRemove: (String) -> Unit, onDismissPrompt: () -> Unit, onConfirmSwitch: () -> Unit, onCheckout: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))) {
        RematerialTopBar("Keranjang", onBack = onBack)
        if (lines.isEmpty()) { Spacer(Modifier.height(46.dp)); Text("Keranjang masih kosong.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Simpan karya yang ingin kamu bawa pulang.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted); return@Column }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 18.dp)) { items(lines, key = { it.product.id }) { line -> CartLineRow(line, onPlus, onMinus, onRemove) } }
        if (prompt) {
            Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), color = RematerialColors.BronzeSoft, border = BorderStroke(1.dp, RematerialColors.Bronze), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(14.dp)) { Text("Karya ini dari studio lain.", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(4.dp)); Text("Keranjang hanya bisa berisi satu studio agar pengiriman tetap sederhana.", style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Text("Batal", Modifier.sizeIn(minHeight = 48.dp).clickable(onClick = onDismissPrompt).padding(10.dp), style = MaterialTheme.typography.labelLarge); Text("Ganti studio", Modifier.sizeIn(minHeight = 48.dp).clickable(onClick = onConfirmSwitch).padding(10.dp), style = MaterialTheme.typography.labelLarge, color = RematerialColors.DeepForest) } } }
        }
        val total = lines.sumOf { it.product.price * it.quantity }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text(rupiah(total), style = MaterialTheme.typography.titleLarge, color = RematerialColors.DeepForest) }
        RematerialButton("Lanjut ke checkout", onCheckout, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowRight)
    }
}

@Composable private fun CartLineRow(line: CartLine, onPlus: (String, Int) -> Unit, onMinus: (String, Int) -> Unit, onRemove: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MarketplaceProductImage(line.product, Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(line.product.title, style = MaterialTheme.typography.titleMedium); Text(rupiah(line.product.price), style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { SmallIcon(RematerialIcons.Minus, "Kurangi") { onMinus(line.product.id, line.quantity) }; Text("  ${line.quantity}  ", style = MaterialTheme.typography.labelLarge); SmallIcon(RematerialIcons.Plus, "Tambah") { onPlus(line.product.id, line.quantity) } } }
        Text("Hapus", style = MaterialTheme.typography.labelSmall, color = RematerialColors.Muted, modifier = Modifier.sizeIn(minHeight = 48.dp).clickable(onClick = { onRemove(line.product.id) }).padding(8.dp))
    }
}

@Composable private fun SmallIcon(icon: Int, description: String, onClick: () -> Unit) { Box(Modifier.size(RematerialDockMetrics.minHitTarget).clip(CircleShape).background(RematerialColors.Surface).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) { RematerialIcon(icon, null, Modifier.size(16.dp), RematerialColors.DeepForest) } }

@Composable private fun CheckoutScreen(lines: List<CartLine>, initialAddress: String, onPlace: (CheckoutDraft) -> Unit, onBack: () -> Unit) {
    var address by androidx.compose.runtime.remember(initialAddress) { mutableStateOf(initialAddress) }; var delivery by rememberSaveable { mutableStateOf("Reguler · 2–4 hari") }; var payment by rememberSaveable { mutableStateOf("Transfer bank demo") }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))) {
        RematerialTopBar("Checkout", onBack = onBack); LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) { item { Spacer(Modifier.height(18.dp)); Text("Hampir selesai.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Semua pilihan di bawah masih demo dan belum terhubung ke pembayaran nyata.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(22.dp)); RematerialField(address, { address = it }, "Alamat pengiriman"); Spacer(Modifier.height(18.dp)); SelectionField("Pengiriman", delivery, listOf("Reguler · 2–4 hari", "Ekonomis · 4–7 hari")) { delivery = it }; Spacer(Modifier.height(18.dp)); SelectionField("Metode pembayaran", payment, listOf("Transfer bank demo", "Dompet digital demo")) { payment = it }; Spacer(Modifier.height(20.dp)); Text("Ringkasan pesanan", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); lines.forEach { Text("${it.quantity}× ${it.product.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)) }; Spacer(Modifier.height(20.dp)); RematerialButton("Buat pesanan", { onPlace(CheckoutDraft(address, delivery, payment)) }, Modifier.fillMaxWidth(), enabled = address.isNotBlank(), leadingIcon = RematerialIcons.Check) } }
    }
}

@Composable private fun SelectionField(title: String, selected: String, options: List<String>, onSelect: (String) -> Unit) { Column { Text(title, style = MaterialTheme.typography.labelMedium, color = RematerialColors.Muted); Spacer(Modifier.height(8.dp)); options.forEach { option -> Row(Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).clickable(role = Role.RadioButton) { onSelect(option) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { RematerialIcon(if (option == selected) RematerialIcons.CircleCheck else RematerialIcons.Circle, null, Modifier.size(20.dp), if (option == selected) RematerialColors.DeepForest else RematerialColors.Line); Spacer(Modifier.width(10.dp)); Text(option, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Ink) } } } }

@Composable private fun OrderSuccessScreen(order: MarketplaceOrder, onHome: () -> Unit, onOrders: () -> Unit) { LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 36.dp, bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))) { item { RematerialIcon(RematerialIcons.CircleCheck, null, Modifier.size(34.dp), RematerialColors.DeepForest); Spacer(Modifier.height(26.dp)); Text("Pesanan dibuat.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text("Karya pilihanmu akan diproses oleh studio dengan tenang.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted); Spacer(Modifier.height(28.dp)); DetailLine("Nomor pesanan", order.id); DetailLine("Total", rupiah(order.total)); DetailLine("Studio", order.lines.firstOrNull()?.product?.seller?.name.orEmpty()); Spacer(Modifier.height(16.dp)); RematerialButton("Lihat pesanan", onOrders, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.Receipt); Spacer(Modifier.height(10.dp)); RematerialButton("Kembali ke pasar", onHome, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowLeft) } } }

@Composable
private fun OrderHistoryScreen(
    orders: List<MarketplaceOrder>,
    onBack: () -> Unit,
    onOpen: (MarketplaceOrder) -> Unit,
    dockVisible: Boolean,
) {
    val bottom = RematerialDockMetrics.screenBottomPadding(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        dockVisible,
    )
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = bottom)) {
        RematerialTopBar("Pesanan", onBack = onBack)
        Spacer(Modifier.height(18.dp))
        Text("Perjalanan belanjamu.", style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text("Riwayat pesanan dari studio yang kamu pilih.", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
        Spacer(Modifier.height(22.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (orders.isEmpty()) item { Text("Belum ada pesanan.", style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted) }
            items(orders, key = { it.id }) { order ->
                Surface(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(order) }, color = RematerialColors.Surface, border = BorderStroke(1.dp, RematerialColors.Line), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp)) { Row { Text(order.id, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text(order.status.label, style = MaterialTheme.typography.bodySmall, color = RematerialColors.DeepForest) }; Spacer(Modifier.height(6.dp)); Text(order.lines.joinToString { "${it.quantity}× ${it.product.title}" }, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted); Spacer(Modifier.height(8.dp)); Text(rupiah(order.total), style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink) }
                }
            }
        }
    }
}

@Composable
private fun OrderDetailScreen(order: MarketplaceOrder, onBack: () -> Unit, onCancel: () -> Unit, onConfirmReceipt: () -> Unit, dockVisible: Boolean) {
    val bottom = RematerialDockMetrics.screenBottomPadding(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        dockVisible,
    )
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp).padding(bottom = bottom)) {
        RematerialTopBar("Detail pesanan", onBack = onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Spacer(Modifier.height(18.dp)); Text(order.id, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Spacer(Modifier.height(8.dp)); Text(order.createdLabel, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(24.dp)); OrderTimeline(order.status); Spacer(Modifier.height(26.dp)); DetailLine("Dikirim ke", order.address); DetailLine("Pengiriman", order.delivery); DetailLine("Pembayaran", order.payment); DetailLine("Total", rupiah(order.total)); if (order.status == OrderStatus.PLACED) RematerialButton("Batalkan pesanan", onCancel, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.ArrowLeft); if (order.status == OrderStatus.SHIPPED) RematerialButton("Konfirmasi sudah diterima", onConfirmReceipt, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.Check) }
        }
    }
}

@Composable private fun OrderTimeline(status: OrderStatus) { Column { Text("Status pesanan", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(10.dp)); val steps = if (status == OrderStatus.CANCELLED) listOf(OrderStatus.PLACED, OrderStatus.CANCELLED) else OrderStatus.entries.filter { it != OrderStatus.CANCELLED }; steps.forEach { item -> val reached = item == OrderStatus.CANCELLED || item.ordinal <= status.ordinal; Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { RematerialIcon(RematerialIcons.CircleCheck, null, Modifier.size(19.dp), if (reached) RematerialColors.DeepForest else RematerialColors.Line); Spacer(Modifier.width(10.dp)); Text(item.label, style = MaterialTheme.typography.bodyMedium, color = if (reached) RematerialColors.Ink else RematerialColors.Muted) } } } }

@Composable
fun UserAccountRoute(
    session: Session,
    onBack: () -> Unit,
    onAnalysis: () -> Unit,
    onProduction: () -> Unit,
    onOrders: () -> Unit,
    onLogout: () -> Unit,
    onUpdateLocation: (String, String) -> Unit,
) {
    var accountArea by androidx.compose.runtime.remember(session.location) { mutableStateOf(session.location?.area.orEmpty()) }
    var accountAddress by androidx.compose.runtime.remember(session.location) { mutableStateOf(session.location?.address.orEmpty()) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp),
            contentPadding = PaddingValues(bottom = RematerialDockMetrics.contentBottomPadding(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())),
        ) {
            item { RematerialTopBar("Akun", onBack = onBack); Spacer(Modifier.height(22.dp)); Text(session.displayName, style = MaterialTheme.typography.displaySmall, color = RematerialColors.Ink); Text(session.email, style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted); Spacer(Modifier.height(28.dp)); Text("Ruangmu", style = MaterialTheme.typography.titleLarge); AccountRow("Analisis material", "Lihat material yang pernah dipetakan", RematerialIcons.Camera, onAnalysis); AccountRow("Produksi", "Pantau karya yang sedang dibuat", RematerialIcons.Hammer, onProduction); AccountRow("Pesanan pasar", "Riwayat pembelian dan pengiriman", RematerialIcons.Receipt, onOrders); Spacer(Modifier.height(26.dp)); Text("Lokasi rekomendasi", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(12.dp)); RematerialField(accountArea, { accountArea = it }, "Kota atau area", placeholder = "Contoh: Bandung"); Spacer(Modifier.height(12.dp)); RematerialField(accountAddress, { accountAddress = it }, "Alamat", placeholder = "Alamat untuk produksi dan pengiriman"); Spacer(Modifier.height(14.dp)); RematerialButton("Simpan lokasi", { onUpdateLocation(accountArea, accountAddress) }, Modifier.fillMaxWidth(), enabled = accountArea.isNotBlank() || accountAddress.isNotBlank(), leadingIcon = RematerialIcons.MapPin); Spacer(Modifier.height(24.dp)); RematerialButton("Keluar dari akun", onLogout, Modifier.fillMaxWidth(), leadingIcon = RematerialIcons.LogOut) }
        }
    }
}

@Composable private fun AccountRow(title: String, supporting: String, icon: Int, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { RematerialIcon(icon, null, Modifier.size(21.dp), RematerialColors.DeepForest); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(supporting, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }; RematerialIcon(RematerialIcons.ChevronRight, null, Modifier.size(18.dp), RematerialColors.Muted) } }

private fun rupiah(value: Int): String = "Rp" + "%,d".format(value).replace(',', '.')

@Composable
private fun MarketplaceProductImage(product: MarketplaceProduct, modifier: Modifier) {
    when {
        product.imageUri != null -> AsyncImage(model = product.imageUri, contentDescription = product.title, modifier = modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        product.imageRes != null -> Image(painterResource(product.imageRes), product.title, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else -> Box(modifier.background(RematerialColors.BronzeSoft), contentAlignment = Alignment.Center) {
            RematerialIcon(RematerialIcons.Image, null, Modifier.size(28.dp), RematerialColors.Bronze)
        }
    }
}
