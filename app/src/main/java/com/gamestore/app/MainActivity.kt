package com.gamestore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gamestore.app.ui.navigation.AppNavigation
import com.gamestore.app.ui.theme.GameStoreTheme
import com.gamestore.app.util.PermissionHelper
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    
    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
    }
    
    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        
        PermissionHelper.checkAndRequestPermissions(this)
        
        setContent {
            GameStoreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
    }
}
