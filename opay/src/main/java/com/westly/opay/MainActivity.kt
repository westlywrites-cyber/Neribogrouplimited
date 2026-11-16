package com.westly.opay

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.westly.opay.core.theme.OPayTheme
import com.westly.opay.navigation.MainScaffold

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val repository = (application as OPayApplication).repository
        setContent {
            val themeMode by repository.themeMode.collectAsState()
            OPayTheme(mode = themeMode) {
                MainScaffold()
            }
        }
    }
}
