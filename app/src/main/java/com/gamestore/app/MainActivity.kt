package com.gamestore.app

import android.content.pm.PackageManager
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
import com.gamestore.app.util.ShizukuInstaller
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    
    private val shizukuPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ShizukuInstaller.SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                android.util.Log.d("MainActivity", "Shizuku permission granted")
            } else {
                android.util.Log.d("MainActivity", "Shizuku permission denied")
            }
        }
    }
    
    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        android.util.Log.d("MainActivity", "Shizuku binder received")
    }
    
    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        android.util.Log.d("MainActivity", "Shizuku binder dead")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
        
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
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
    }
}
