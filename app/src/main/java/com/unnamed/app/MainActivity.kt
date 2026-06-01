package com.unnamed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.unnamed.app.ui.navigation.AppScaffold
import com.unnamed.app.ui.theme.UnnamedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnnamedTheme {
                AppScaffold()
            }
        }
    }
}
