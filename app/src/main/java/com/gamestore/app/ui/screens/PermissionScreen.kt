package com.gamestore.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamestore.app.ui.viewmodel.MainViewModel
import com.gamestore.app.util.PermissionHelper
import com.gamestore.app.util.ShizukuInstaller
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var hasStoragePermission by remember { mutableStateOf(PermissionHelper.hasStoragePermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var hasInstallPermission by remember { mutableStateOf(false) }
    var hasShizukuPermission by remember { mutableStateOf(false) }
    
    var selectedInstallMethod by remember { mutableStateOf<String?>(null) }
    var showMethodDialog by remember { mutableStateOf(false) }
    
    val shizukuInstaller = remember { ShizukuInstaller(context) }

    fun checkPermissions() {
        val storage = PermissionHelper.hasStoragePermission(context)
        val notification = PermissionHelper.hasNotificationPermission(context)
        hasStoragePermission = storage
        hasNotificationPermission = notification
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hasInstallPermission = context.packageManager.canRequestPackageInstalls()
        } else {
            hasInstallPermission = true
        }
        
        hasShizukuPermission = shizukuInstaller.hasShizukuPermission()
        
        val basicPermissionsGranted = storage && (notification || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        
        if (basicPermissionsGranted && selectedInstallMethod != null) {
            val installOk = when (selectedInstallMethod) {
                "standard" -> hasInstallPermission
                "shizuku" -> hasShizukuPermission
                else -> false
            }
            
            if (installOk) {
                onPermissionsGranted()
            }
        }
    }
    
    val shizukuPermissionListener = remember {
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuInstaller.SHIZUKU_PERMISSION_REQUEST_CODE) {
                hasShizukuPermission = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                checkPermissions()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        }
    }

    val storageSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        checkPermissions()
    }
    
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
    }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "GameFluxer",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Configuração Inicial",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configure as permissões e método de instalação",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        PermissionCard(
            icon = Icons.Default.Folder,
            title = "Gerenciar Arquivos",
            subtitle = if (hasStoragePermission) "✓ Concedida" else "Necessária para salvar downloads",
            isGranted = hasStoragePermission,
            onClick = {
                if (!hasStoragePermission) {
                    if (context is Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            storageSettingsLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            storageSettingsLauncher.launch(intent)
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Notificações",
                subtitle = if (hasNotificationPermission) "✓ Concedida" else "Para acompanhar downloads e instalações",
                isGranted = hasNotificationPermission,
                onClick = {
                    if (!hasNotificationPermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (hasStoragePermission && (hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Método de Instalação",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (selectedInstallMethod) {
                                    "standard" -> "Padrão selecionado"
                                    "shizuku" -> "Shizuku selecionado"
                                    else -> "Selecione um método"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { showMethodDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escolher Método", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (selectedInstallMethod == "standard" && !hasInstallPermission) {
                PermissionCard(
                    icon = Icons.Default.Android,
                    title = "Instalação de APKs",
                    subtitle = "Permissão para instalar aplicativos",
                    isGranted = false,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            intent.data = Uri.parse("package:${context.packageName}")
                            installPermissionLauncher.launch(intent)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            if (selectedInstallMethod == "shizuku" && !hasShizukuPermission) {
                PermissionCard(
                    icon = Icons.Default.Security,
                    title = "Permissão Shizuku",
                    subtitle = if (shizukuInstaller.isShizukuAvailable()) 
                        "Toque para solicitar permissão" 
                    else 
                        "Shizuku não está ativo",
                    isGranted = false,
                    onClick = {
                        if (shizukuInstaller.isShizukuAvailable()) {
                            shizukuInstaller.requestShizukuPermission()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val canContinue = hasStoragePermission && 
            (hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) &&
            selectedInstallMethod != null &&
            ((selectedInstallMethod == "standard" && hasInstallPermission) || 
             (selectedInstallMethod == "shizuku" && hasShizukuPermission))

        if (canContinue) {
            Button(
                onClick = {
                    scope.launch {
                        viewModel.setInstallMethod(selectedInstallMethod!!)
                        onPermissionsGranted()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continuar", fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(
                onClick = { checkPermissions() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verificar Permissões")
            }
        }
    }
    
    if (showMethodDialog) {
        InstallMethodDialog(
            onDismiss = { showMethodDialog = false },
            onMethodSelected = { method ->
                selectedInstallMethod = method
                scope.launch {
                    viewModel.setInstallMethod(method)
                }
                showMethodDialog = false
                checkPermissions()
            },
            currentMethod = selectedInstallMethod
        )
    }
}

@Composable
fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isGranted, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isGranted)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isGranted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun InstallMethodDialog(
    onDismiss: () -> Unit,
    onMethodSelected: (String) -> Unit,
    currentMethod: String?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = {
            Text(
                "Método de Instalação",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Escolha como os APKs serão instalados:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMethodSelected("standard") },
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentMethod == "standard")
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMethod == "standard",
                            onClick = { onMethodSelected("standard") }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Padrão",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Instalação manual (requer confirmação)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMethodSelected("shizuku") },
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentMethod == "shizuku")
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMethod == "shizuku",
                            onClick = { onMethodSelected("shizuku") }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Shizuku (Automático)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Instalação automática após download",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
