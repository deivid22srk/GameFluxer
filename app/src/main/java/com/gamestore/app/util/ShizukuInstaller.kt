package com.gamestore.app.util

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.io.IOException

class ShizukuInstaller(private val context: Context) {
    
    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "ShizukuInstaller"
    }
    
    fun isShizukuAvailable(): Boolean {
        return try {
            val available = Shizuku.pingBinder()
            Log.d(TAG, "Shizuku available: $available")
            available
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku availability", e)
            false
        }
    }
    
    fun hasShizukuPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                Log.d(TAG, "Shizuku is pre-v11, returning false")
                false
            } else {
                val permission = Shizuku.checkSelfPermission()
                val hasPermission = permission == android.content.pm.PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Shizuku permission status: $permission, hasPermission: $hasPermission")
                hasPermission
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku permission", e)
            false
        }
    }
    
    fun requestShizukuPermission() {
        try {
            Log.d(TAG, "Requesting Shizuku permission")
            if (Shizuku.isPreV11()) {
                Log.d(TAG, "Cannot request permission on pre-v11")
                return
            }
            
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Log.d(TAG, "Should show rationale")
            }
            
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            Log.d(TAG, "Permission requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Shizuku permission", e)
            e.printStackTrace()
        }
    }
    
    fun installApk(apkFile: File, onProgress: (String) -> Unit, onComplete: (Boolean, String?) -> Unit) {
        if (!isShizukuAvailable()) {
            onComplete(false, "Shizuku não está disponível")
            return
        }
        
        if (!hasShizukuPermission()) {
            onComplete(false, "Permissão do Shizuku não concedida")
            return
        }
        
        try {
            onProgress("Preparando instalação...")
            
            val packageInstaller = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.packageManager.packageInstaller
            } else {
                onComplete(false, "Versão do Android não suportada")
                return
            }
            
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            
            onProgress("Copiando APK...")
            
            session.openWrite("package", 0, -1).use { outputStream ->
                apkFile.inputStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    val fileSize = apkFile.length()
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        
                        if (fileSize > 0) {
                            val progress = (totalBytes * 100 / fileSize).toInt()
                            onProgress("Instalando... $progress%")
                        }
                    }
                    outputStream.flush()
                }
            }
            
            onProgress("Finalizando instalação...")
            
            val intent = android.content.Intent(context, context.javaClass)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            session.commit(pendingIntent.intentSender)
            session.close()
            
            onComplete(true, null)
            
        } catch (e: IOException) {
            e.printStackTrace()
            onComplete(false, "Erro de I/O: ${e.message}")
        } catch (e: SecurityException) {
            e.printStackTrace()
            onComplete(false, "Erro de segurança: ${e.message}")
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false, "Erro: ${e.message}")
        }
    }
}
