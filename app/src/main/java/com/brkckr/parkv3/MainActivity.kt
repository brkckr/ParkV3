package com.brkckr.parkv3

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.brkckr.parkv3.navigation.AppNavHost
import com.brkckr.parkv3.ui.theme.ParkTheme
import dagger.hilt.android.AndroidEntryPoint

/** AppCompatActivity so the in-app language choice applies below Android 13 too (docs/adr/0013). */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ParkTheme {
                AppNavHost()
            }
        }
    }
}
