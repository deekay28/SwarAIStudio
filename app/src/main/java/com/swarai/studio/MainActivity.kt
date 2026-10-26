package com.swarai.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarai.studio.data.AppSettings
import com.swarai.studio.ui.SwarAppRoot
import com.swarai.studio.ui.theme.SwarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as SwarApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            SwarTheme(mode = settings.themeMode) {
                SwarAppRoot(container)
            }
        }
    }
}
