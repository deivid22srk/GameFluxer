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
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddPlatformDialog by remember { mutableStateOf(false) }
    var showAddDatabaseDialog by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf<String?>(null) }
    var selectedPlatformForDatabase by remember { mutableStateOf<String?>(null) }
    
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
                        fontWeight = FontWeight.Bold
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
        }
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
                            platform = platform,
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
                            onAddDatabase = {
                                selectedPlatformForDatabase = platform.name
                                showAddDatabaseDialog = true
                            },
                            onRemoveDatabase = { dbName ->
                                viewModel.removeDatabaseFromPlatform(platform.name, dbName)
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
    
    if (showAddDatabaseDialog && selectedPlatformForDatabase != null) {
        AddDatabaseDialog(
            platformName = selectedPlatformForDatabase!!,
            onDismiss = { showAddDatabaseDialog = false },
            onConfirm = { dbName ->
                val dbPath = "databases/${selectedPlatformForDatabase!!.lowercase().replace(" ", "_")}_${dbName.lowercase().replace(" ", "_")}.json"
                viewModel.addDatabaseToPlatform(selectedPlatformForDatabase!!, dbName, dbPath)
                showAddDatabaseDialog = false
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
    platform: com.gamestore.app.data.model.Platform,
    gameCount: Int,
    onCardClick: () -> Unit,
    onImportJson: () -> Unit,
    onAddDatabase: () -> Unit,
    onRemoveDatabase: (String) -> Unit,
    onRemovePlatform: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCardClick)
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
                        text = platform.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$gameCount jogos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (platform.databases.isNotEmpty()) {
                        Text(
                            text = "${platform.databases.size} database(s) secundário(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Recolher" else "Expandir"
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
                        if (platform.databases.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Gerenciar Databases") },
                                onClick = {
                                    showMenu = false
                                    onCardClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                }
                            )
                        }
                        Divider()
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
            }
            
            if (isExpanded && platform.databases.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Databases Secundários:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    platform.databases.forEach { db ->
                        DatabaseItem(
                            databaseName = db.name,
                            onRemove = { onRemoveDatabase(db.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DatabaseItem(
    databaseName: String,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = databaseName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remover",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
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

@Composable
fun AddDatabaseDialog(
    platformName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var databaseName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.secondary)
        },
        title = { Text("Adicionar Database", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Adicionar database secundário para $platformName:")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Útil para ter catálogos diferentes (ex: Premium, Básico, Retro, etc)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = databaseName,
                    onValueChange = { databaseName = it },
                    label = { Text("Nome do Database") },
                    placeholder = { Text("Ex: Premium, Básico, Retro") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (databaseName.isNotBlank()) onConfirm(databaseName.trim()) },
                enabled = databaseName.isNotBlank()
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