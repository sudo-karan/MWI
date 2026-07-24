package com.ismartcoding.plain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ismartcoding.plain.ui.App

/**
 * The single Activity host. All UI is Compose (`App()` lives in commonMain), so this class stays
 * thin — it only wires the Compose content into the Android window.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
