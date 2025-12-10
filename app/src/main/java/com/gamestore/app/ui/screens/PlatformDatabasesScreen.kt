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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamestore.app.data.model.DatabaseEntry
import com.gamestore.app.ui.viewmodel.DatabaseManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformDatabasesScreen(
    platformName: String,
    onBackClick: () -> Unit,
    onNavigateToGames: (String, String?) -> Unit,
    viewModel: DatabaseManagerViewModel = viewModel()
) {
    val currentConfig by viewModel.currentConfig.collectAsState()
    val gamesMap by viewModel.gamesMap.collectAsState()
    
    val platform = currentConfig?.platforms?.find { it.name == platformName }
    var showAddDatabaseDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var databaseToDelete by remember { mutableStateOf<DatabaseEntry?>(null) }
    
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importJsonForPlatform(platformName, uri)
            }
        }
    }
    
    val importJsonForDatabaseLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && databaseToDelete != null) {
            result.data?.data?.let { uri ->
                viewModel.importJsonForDatabase(platformName, databaseToDelete!!.name, uri)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Databases - $platformName", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDatabaseDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Database")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                DatabaseCard(
                    databaseName = "Database Principal",
                    gameCount = gamesMap[platformName]?.size ?: 0,
                    isPrimary = true,
                    onViewGames = { onNavigateToGames(platformName, null) },
                    onImportJson = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                        }
                        importJsonLauncher.launch(intent)
                    },
                    onDelete = {}
                )
            }
            
            if (platform != null && platform.databases.isNotEmpty()) {
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Databases Secundários",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(platform.databases) { database ->
                    DatabaseCard(
                        databaseName = database.name,
                        gameCount = gamesMap["$platformName:${database.name}"]?.size ?: 0,
                        isPrimary = false,
                        onViewGames = { onNavigateToGames(platformName, database.name) },
                        onImportJson = {
                            databaseToDelete = database
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                            }
                            importJsonForDatabaseLauncher.launch(intent)
                        },
                        onDelete = {
                            databaseToDelete = database
                            showDeleteDialog = true
                        }
                    )
                }
            }
            
            if (platform?.databases?.isEmpty() == true) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Adicione databases secundários",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use o botão + para criar catálogos diferentes (Premium, Básico, etc)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (showAddDatabaseDialog) {
        AddSecondaryDatabaseDialog(
            platformName = platformName,
            onDismiss = { showAddDatabaseDialog = false },
            onConfirm = { dbName ->
                val dbPath = "databases/${platformName.lowercase().replace(" ", "_")}_${dbName.lowercase().replace(" ", "_")}.json"
                viewModel.addDatabaseToPlatform(platformName, dbName, dbPath)
                showAddDatabaseDialog = false
            }
        )
    }
    
    if (showDeleteDialog && databaseToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Excluir Database?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Tem certeza que deseja excluir o database \"${databaseToDelete!!.name}\"? Todos os jogos deste database serão perdidos.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeDatabaseFromPlatform(platformName, databaseToDelete!!.name)
                        showDeleteDialog = false
                        databaseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun DatabaseCard(
    databaseName: String,
    gameCount: Int,
    isPrimary: Boolean,
    onViewGames: () -> Unit,
    onImportJson: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = if (isPrimary) {
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            } else {
                                listOf(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                )
                            }
                        )
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isPrimary) Icons.Default.Storage else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = databaseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$gameCount jogos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (!isPrimary) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onViewGames,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver Jogos")
                }
                OutlinedButton(
                    onClick = onImportJson,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Importar JSON")
                }
            }
        }
    }
}

@Composable
fun AddSecondaryDatabaseDialog(
    platformName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var databaseName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        },
        title = { Text("Adicionar Database Secundário", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Adicionar novo database para $platformName:")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Útil para ter catálogos separados: Premium, Básico, Retro, etc.",
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