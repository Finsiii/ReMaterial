package com.rematerial.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rematerial.app.core.designsystem.DockDestination
import com.rematerial.app.core.designsystem.RematerialButton
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialDock
import com.rematerial.app.core.designsystem.RematerialIcons
import com.rematerial.app.core.designsystem.RematerialListRow
import com.rematerial.app.core.designsystem.RematerialTopBar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReMaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = RematerialColors.Canvas) {
                    ReMaterialContent()
                }
            }
        }
    }
}

@Composable
private fun ReMaterialContent() {
    var selectedName by rememberSaveable { mutableStateOf(DockDestination.Beranda.name) }
    val selected = DockDestination.valueOf(selectedName)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 16.dp)
                .padding(bottom = 104.dp),
        ) {
            RematerialTopBar(
                title = "ReMaterial",
                actionIcon = RematerialIcons.Bell,
                actionDescription = "Notifikasi",
                onAction = {},
            )
            Spacer(Modifier.height(28.dp))
            Text("Selamat datang, Dika", style = MaterialTheme.typography.bodyMedium, color = RematerialColors.Muted)
            Spacer(Modifier.height(6.dp))
            Text(
                "Bahan ini akan\nmenjadi apa?",
                style = MaterialTheme.typography.displayLarge,
                color = RematerialColors.Ink,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Pahami material yang kamu punya. Temukan kemungkinan baru untuknya.",
                style = MaterialTheme.typography.bodyLarge,
                color = RematerialColors.Muted,
            )
            Spacer(Modifier.height(24.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listOf("metal", "wood", "textile")) { material ->
                    val image = when (material) {
                        "metal" -> R.drawable.material_metal
                        "wood" -> R.drawable.material_wood
                        else -> R.drawable.material_textile
                    }
                    Surface(
                        modifier = Modifier.size(width = 124.dp, height = 156.dp),
                        color = RematerialColors.Surface,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column {
                            Image(
                                painter = painterResource(image),
                                contentDescription = material,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(112.dp)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                material.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            RematerialButton(
                text = "Analisis bahan",
                onClick = {},
                leadingIcon = RematerialIcons.Camera,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Text("Aktivitas terbaru", style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink)
            RematerialListRow(
                title = "Kabel tembaga",
                supportingText = "Dianalisis hari ini · 2,45 kg",
                leadingIcon = RematerialIcons.Sparkles,
                onClick = {},
            )
        }
        RematerialDock(
            selected = selected,
            onDestinationSelected = { selectedName = it.name },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReMaterialPreview() {
    ReMaterialTheme { ReMaterialContent() }
}
