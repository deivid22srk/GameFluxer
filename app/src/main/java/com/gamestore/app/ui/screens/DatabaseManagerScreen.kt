package com.gamestore.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamestore.app.data.model.Game
import com.gamestore.app.ui.viewmodel.DatabaseManagerViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseManagerScreen(
    onBackClick: () -> Unit,
    onNavigateToPlatformGames: (String) -> Unit = {},
    viewModel: DatabaseManagerViewModel = viewModel()
) {
    val currentConfig by viewModel.currentConfig.collectAsState()
    val gamesMap by viewModel.gamesMap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddPlatformDialog by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf<String?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(statusMessage) {
        statusMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusMessage()
        }
    }
    
    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importDatabase(uri)
            }
        }
    }
    
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && selectedPlatform != null) {
            result.data?.data?.let { uri ->
                viewModel.importJsonForPlatform(selectedPlatform!!, uri)
            }
        }
    }
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.exportDatabase(uri)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Gerenciar Banco de Dados", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (currentConfig != null) {
                        IconButton(onClick = { viewModel.saveCurrentDatabase() }) {
                            Icon(Icons.Default.Save, contentDescription = "Salvar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ActionCard(
                        icon = Icons.Default.Add,
                        title = "Criar Novo Banco",
                        subtitle = "Criar um banco de dados vazio",
                        gradient = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        )
                    ) {
                        showCreateDialog = true
                    }
                }
                
                item {
                    ActionCard(
                        icon = Icons.Default.Upload,
                        title = "Importar Banco ZIP",
                        subtitle = "Importar banco de dados completo",
                        gradient = listOf(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ),
                        enabled = !isLoading
                    ) {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/zip"
                        }
                        importZipLauncher.launch(intent)
                    }
                }
                
                item {
                    ActionCard(
                        icon = Icons.Default.Download,
                        title = "Exportar Banco ZIP",
                        subtitle = "Exportar banco de dados atual",
                        gradient = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        ),
                        enabled = currentConfig != null && !isLoading
                    ) {
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/zip"
                            putExtra(Intent.EXTRA_TITLE, "gamefluxer_database.zip")
                        }
                        exportLauncher.launch(intent)
                    }
                }
                
                if (currentConfig != null) {
                    item {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Plataformas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    item {
                        ActionCard(
                            icon = Icons.Default.AddCircle,
                            title = "Adicionar Plataforma",
                            subtitle = "Adicionar nova plataforma ao banco",
                            gradient = listOf(
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            showAddPlatformDialog = true
                        }
                    }
                    
                    items(currentConfig!!.platforms) { platform ->
                        PlatformCard(
                            platformName = platform.name,
                            gameCount = gamesMap[platform.name]?.size ?: 0,
                            onCardClick = {
                                onNavigateToPlatformGames(platform.name)
                            },
                            onImportJson = {
                                selectedPlatform = platform.name
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/json"
                                }
                                importJsonLauncher.launch(intent)
                            },
                            onRemovePlatform = {
                                viewModel.removePlatform(platform.name)
                            }
                        )
                    }
                }
                
                if (currentConfig == null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Nenhum banco de dados carregado",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Crie um novo ou importe um existente",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
    
    if (showCreateDialog) {
        CreateDatabaseDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { platformName ->
                viewModel.createNewDatabase(platformName)
                showCreateDialog = false
            }
        )
    }
    
    if (showAddPlatformDialog) {
        AddPlatformDialog(
            onDismiss = { showAddPlatformDialog = false },
            onConfirm = { platformName ->
                viewModel.addPlatform(platformName)
                showAddPlatformDialog = false
            }
        )
    }
}

@Composable
fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradient: List<androidx.compose.ui.graphics.Color>,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(gradient),
                    alpha = if (enabled) 1f else 0.5f
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
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
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PlatformCard(
    platformName: String,
    gameCount: Int,
    onCardClick: () -> Unit,
    onImportJson: () -> Unit,
    onRemovePlatform: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platformName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$gameCount jogos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Opções",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Importar JSON") },
                        onClick = {
                            showMenu = false
                            onImportJson()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Upload, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remover Plataforma") },
                        onClick = {
                            showMenu = false
                            onRemovePlatform()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null
            )
        }
    }
}

@Composable
fun CreateDatabaseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var platformName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(32.dp))
        },
        title = { Text("Criar Novo Banco", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Digite o nome da primeira plataforma:")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = platformName,
                    onValueChange = { platformName = it },
                    label = { Text("Nome da Plataforma") },
                    placeholder = { Text("Ex: Android, PC, PS2") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (platformName.isNotBlank()) onConfirm(platformName.trim()) },
                enabled = platformName.isNotBlank()
            ) {
                Text("Criar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun AddPlatformDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var platformName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(32.dp))
        },
        title = { Text("Adicionar Plataforma", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Digite o nome da nova plataforma:")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = platformName,
                    onValueChange = { platformName = it },
                    label = { Text("Nome da Plataforma") },
                    placeholder = { Text("Ex: Xbox, Nintendo Switch") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (platformName.isNotBlank()) onConfirm(platformName.trim()) },
                enabled = platformName.isNotBlank()
            ) {
                Text("Adicionar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}