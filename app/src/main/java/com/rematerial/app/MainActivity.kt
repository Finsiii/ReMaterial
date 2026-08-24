package com.rematerial.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.DockDestination
import com.rematerial.app.feature.home.UserHomeScreen
import com.rematerial.app.feature.analysis.presentation.AnalysisRoute
import com.rematerial.app.feature.analysis.presentation.UserPlaceholderScreen
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.presentation.IdentityEntryScreen
import com.rematerial.app.feature.identity.presentation.IdentityEvent
import com.rematerial.app.feature.identity.presentation.IdentityViewModel
import com.rematerial.app.feature.identity.presentation.UpcomingWorkspaceScreen
import dagger.hilt.android.AndroidEntryPoint

private object Routes {
    const val Identity = "identity"
    const val UserHome = "user-home"
    const val Analysis = "analysis"
    const val Production = "production"
    const val Market = "market"
    const val Account = "account"
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
    val identityViewModel: IdentityViewModel = hiltViewModel()
    NavHost(navController = navController, startDestination = Routes.Identity) {
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
                onScan = { navController.navigate(Routes.Analysis) },
                onDestinationSelected = { destination ->
                    when (destination) {
                        DockDestination.Beranda -> Unit
                        DockDestination.Scan -> navController.navigate(Routes.Analysis)
                        DockDestination.Produksi -> navController.navigate(Routes.Production)
                        DockDestination.Pasar -> navController.navigate(Routes.Market)
                        DockDestination.Akun -> navController.navigate(Routes.Account)
                    }
                },
            )
        }
        composable(Routes.Analysis) {
            AnalysisRoute(
                onClose = { navController.popBackStack() },
                onOpenArtisan = { navController.navigate(Routes.ArtisanWorkspace) },
            )
        }
        composable(Routes.Production) { UserPlaceholderScreen("Produksi", "Rencanakan proses pembuatan dari material yang sudah kamu simpan.", DockDestination.Produksi) { navController.popBackStack() } }
        composable(Routes.Market) { UserPlaceholderScreen("Pasar", "Temukan produk dan inspirasi yang bisa lahir dari materialmu.", DockDestination.Pasar) { navController.popBackStack() } }
        composable(Routes.Account) { UserPlaceholderScreen("Akun", "Kelola profil, preferensi analisis, dan daftar ide produksi.", DockDestination.Akun) { navController.popBackStack() } }
        composable(Routes.ArtisanWorkspace) {
            UpcomingWorkspaceScreen(Role.ARTISAN) { returnToIdentity(navController, identityViewModel) }
        }
        composable(Routes.SellerWorkspace) {
            UpcomingWorkspaceScreen(Role.SELLER) { returnToIdentity(navController, identityViewModel) }
        }
    }
}

private fun Role.route(): String = when (this) {
    Role.USER -> Routes.UserHome
    Role.ARTISAN -> Routes.ArtisanWorkspace
    Role.SELLER -> Routes.SellerWorkspace
}

private fun returnToIdentity(navController: NavHostController, viewModel: IdentityViewModel) {
    viewModel.onEvent(IdentityEvent.SignOut)
    navController.navigate(Routes.Identity) { popUpTo(0) }
}
