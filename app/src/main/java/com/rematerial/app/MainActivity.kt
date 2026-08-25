package com.rematerial.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rematerial.app.core.designsystem.DockDestination
import com.rematerial.app.core.designsystem.HorizontalPageMotion
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialDock
import com.rematerial.app.core.designsystem.horizontalPageMotion
import com.rematerial.app.feature.home.UserHomeScreen
import com.rematerial.app.feature.analysis.presentation.AnalysisRoute
import com.rematerial.app.feature.artisan.presentation.ArtisanWorkspaceRoute
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.core.model.SafetyOutcome
import com.rematerial.app.feature.identity.presentation.IdentityEntryScreen
import com.rematerial.app.feature.identity.presentation.IdentityEvent
import com.rematerial.app.feature.identity.presentation.IdentityViewModel
import com.rematerial.app.feature.production.domain.ProductDraft
import com.rematerial.app.feature.production.presentation.ProductionRoute
import com.rematerial.app.feature.production.presentation.ProductionViewModel
import com.rematerial.app.feature.marketplace.presentation.MarketplaceRoute
import com.rematerial.app.feature.marketplace.presentation.MarketplaceOrdersRoute
import com.rematerial.app.feature.marketplace.presentation.UserAccountRoute
import com.rematerial.app.feature.marketplace.domain.BuyerContext
import com.rematerial.app.feature.seller.presentation.SellerWorkspaceRoute
import dagger.hilt.android.AndroidEntryPoint

private object Routes {
    const val Identity = "identity"
    const val UserHome = "user-home"
    const val Analysis = "analysis"
    const val Production = "production"
    const val Market = "market"
    const val Account = "account"
    const val Orders = "orders"
    const val ArtisanWorkspace = "artisan-workspace"
    const val SellerWorkspace = "seller-workspace"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReMaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = RematerialColors.Canvas) {
                    ReMaterialNavHost()
                }
            }
        }
    }
}

@Composable
private fun ReMaterialNavHost() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val identityViewModel: IdentityViewModel = hiltViewModel()
    val productionViewModel: ProductionViewModel = hiltViewModel()
    val identityState by identityViewModel.state.collectAsStateWithLifecycle()
    val productionState by productionViewModel.state.collectAsStateWithLifecycle()
    val session = identityState.session
    LaunchedEffect(session) { productionViewModel.applySession(session) }
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = session?.role?.route() ?: Routes.Identity,
            enterTransition = {
                slideIntoContainer(
                    userRouteMotion(initialState.destination.route, targetState.destination.route).slideDirection,
                    animationSpec = tween(210),
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    userRouteMotion(initialState.destination.route, targetState.destination.route).slideDirection,
                    animationSpec = tween(210),
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    userRouteMotion(initialState.destination.route, targetState.destination.route).slideDirection,
                    animationSpec = tween(210),
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    userRouteMotion(initialState.destination.route, targetState.destination.route).slideDirection,
                    animationSpec = tween(210),
                )
            },
        ) {
        composable(Routes.Identity) {
            IdentityEntryScreen(
                viewModel = identityViewModel,
                onSignedIn = { role ->
                    navController.navigate(role.route()) {
                        popUpTo(Routes.Identity) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.UserHome) {
            UserHomeScreen(
                displayName = session?.displayName.orEmpty(),
                area = session?.location?.area.orEmpty(),
                latestRequest = productionState.requests.firstOrNull(),
                nearbyArtisan = productionState.artisans.firstOrNull(),
                onScan = { navController.navigateUserDestination(Routes.Analysis) },
                onProduction = { navController.navigateUserDestination(Routes.Production) },
                onArtisans = { navController.navigateUserDestination(Routes.Production) },
            )
        }
        composable(Routes.Analysis) {
            AnalysisRoute(
                onClose = { navController.popBackStack() },
                onOpenArtisan = { option, analysisId, safetyOutcome ->
                    productionViewModel.saveDraft(
                        ProductDraft(
                            optionId = option.optionId,
                            title = option.title,
                            materialSummary = "${option.requiredMaterial} terpilih dari hasil analisis ${analysisId.value.takeLast(8)}",
                            minimumQuantity = "Perlu dikonfirmasi pengrajin",
                            analysisId = analysisId.value,
                            safetyAllowed = safetyOutcome != SafetyOutcome.BLOCK,
                            requiredCapabilities = capabilityKeys(option.requiredMaterial),
                            requiredTools = option.requiredToolIds,
                            requiredSkills = option.requiredSkillIds,
                            provisionalScore = option.provisionalProductScore,
                            estimatedUsage = "Ditentukan setelah pengukuran pengrajin",
                        ),
                    )
                    navController.navigateUserDestination(Routes.Production)
                },
            )
        }
        composable(Routes.Production) {
            ProductionRoute(
                onBack = { navController.popBackStack() },
                onChangeProduct = { navController.navigateUserDestination(Routes.Analysis) },
                viewModel = productionViewModel,
            )
        }
        composable(Routes.Market) {
            MarketplaceRoute(
                BuyerContext(session?.displayName.orEmpty(), session?.contact?.whatsapp.orEmpty()),
                session?.location?.address.orEmpty(),
            )
        }
        composable(Routes.Account) {
            session?.let { activeSession -> UserAccountRoute(
                session = activeSession,
                onBack = { navController.popBackStack() },
                onAnalysis = { navController.navigate(Routes.Analysis) },
                onProduction = { navController.navigate(Routes.Production) },
                onOrders = { navController.navigate(Routes.Orders) },
                onLogout = { returnToIdentity(navController, identityViewModel) },
                onUpdateLocation = { area, address -> identityViewModel.onEvent(IdentityEvent.UpdateSessionLocation(area, address)) },
            ) }
        }
        composable(Routes.Orders) { MarketplaceOrdersRoute(onBack = { navController.popBackStack() }) }
        composable(Routes.ArtisanWorkspace) {
            session?.let { ArtisanWorkspaceRoute(it, onLogout = { returnToIdentity(navController, identityViewModel) }) }
        }
        composable(Routes.SellerWorkspace) {
            session?.let { SellerWorkspaceRoute(it, onLogout = { returnToIdentity(navController, identityViewModel) }) }
        }
        }
        userDockDestination(currentBackStackEntry?.destination?.route)?.let { selected ->
            RematerialDock(
                selected = selected,
                onDestinationSelected = navController::navigateDockDestination,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private val HorizontalPageMotion.slideDirection: AnimatedContentTransitionScope.SlideDirection
    get() = if (this == HorizontalPageMotion.FORWARD) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }

private fun Role.route(): String = when (this) {
    Role.USER -> Routes.UserHome
    Role.ARTISAN -> Routes.ArtisanWorkspace
    Role.SELLER -> Routes.SellerWorkspace
}

private fun returnToIdentity(navController: NavHostController, viewModel: IdentityViewModel) {
    viewModel.signOut(
        onSuccess = {
            navController.navigate(Routes.Identity) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        },
    )
}

private fun NavHostController.navigateUserDestination(route: String) {
    navigate(route) {
        popUpTo(Routes.UserHome) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateDockDestination(destination: DockDestination) {
    val route = when (destination) {
        DockDestination.Beranda -> Routes.UserHome
        DockDestination.Produksi -> Routes.Production
        DockDestination.Scan -> Routes.Analysis
        DockDestination.Pasar -> Routes.Market
        DockDestination.Akun -> Routes.Account
    }
    if (currentDestination?.route != route) navigateUserDestination(route)
}

internal fun userDockDestination(route: String?): DockDestination? = when (route) {
    Routes.UserHome -> DockDestination.Beranda
    Routes.Production -> DockDestination.Produksi
    Routes.Market -> DockDestination.Pasar
    Routes.Account -> DockDestination.Akun
    else -> null
}

internal fun userTabIndex(route: String?): Int = when (route) {
    Routes.UserHome -> 0
    Routes.Production -> 1
    Routes.Analysis -> 2
    Routes.Market -> 3
    Routes.Account -> 4
    Routes.Orders -> 5
    Routes.ArtisanWorkspace, Routes.SellerWorkspace -> 0
    Routes.Identity -> -1
    else -> 10
}

internal fun userRouteMotion(initialRoute: String?, targetRoute: String?): HorizontalPageMotion =
    horizontalPageMotion(userTabIndex(initialRoute), userTabIndex(targetRoute))

private fun capabilityKeys(material: String): List<String> = when {
    material.contains("kabel", true) || material.contains("cable", true) -> listOf("cable")
    material.contains("metal", true) || material.contains("logam", true) -> listOf("metal")
    material.contains("kayu", true) || material.contains("wood", true) -> listOf("wood")
    material.contains("tekstil", true) || material.contains("textile", true) -> listOf("textile")
    material.contains("plastik", true) || material.contains("plastic", true) -> listOf("plastic")
    else -> emptyList()
}
