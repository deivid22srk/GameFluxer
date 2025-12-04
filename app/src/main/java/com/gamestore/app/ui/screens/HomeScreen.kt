package com.gamestore.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamestore.app.ui.components.HorizontalGameSection
import com.gamestore.app.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    onGameClick: (String) -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val games by viewModel.games.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val currentPlatform by viewModel.currentPlatform.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()
    
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importDatabase(uri)
            }
        }
    }

    LaunchedEffect(importStatus) {
        importStatus?.let {
            viewModel.clearImportStatus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (currentPlatform == null || games.isEmpty()) {
            EmptyStateView(
                onImportClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                    }
                    launcher.launch(intent)
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Plataforma Atual",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentPlatform ?: "",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (categories.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Categorias",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(categories.size) { index ->
                                    val category = categories[index]
                                    FilterChip(
                                        selected = false,
                                        onClick = { viewModel.filterByCategory(category) },
                                        label = { Text(category) }
                                    )
                                }
                            }
                        }
                    }
                }

                val gamesByCategory = games.groupBy { it.category }.entries.take(5)
                
                items(gamesByCategory.size) { index ->
                    val entry = gamesByCategory.elementAt(index)
                    val category = entry.key
                    val categoryGames = entry.value.take(10)
                    
                    HorizontalGameSection(
                        title = category,
                        games = categoryGames,
                        onGameClick = onGameClick
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                }
                launcher.launch(intent)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Import Database")
        }

        importStatus?.let { status ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(status)
            }
        }
    }
}

@Composable
fun EmptyStateView(onImportClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Nenhum banco de dados importado",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Por favor, importe um banco de dados ZIP para começar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onImportClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Importar Banco de Dados")
            }
        }
    }
}

@Composable
fun LazyRow(
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}
