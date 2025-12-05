package com.gamestore.app.ui.components

import android.os.Environment
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerDialog(
    currentPath: String?,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    var currentDirectory by remember { 
        mutableStateOf(
            currentPath?.let { File(it) } ?: getDefaultDirectory()
        ) 
    }
    var folders by remember { mutableStateOf(listOf<File>()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentDirectory) {
        isLoading = true
        folders = try {
            currentDirectory.listFiles { file ->
                file.isDirectory && file.canRead() && !file.isHidden
            }?.sortedBy { it.name.lowercase() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Selecionar Pasta",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Caminho atual
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Pasta atual:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentDirectory.absolutePath,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Divider()

                // Storage roots (Memória Interna e SD Card)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Memória Interna
                    OutlinedButton(
                        onClick = { 
                            currentDirectory = File("/storage/emulated/0")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Interna", style = MaterialTheme.typography.bodySmall)
                    }

                    // SD Card (se existir)
                    val sdCardPath = findSdCardPath()
                    if (sdCardPath != null) {
                        OutlinedButton(
                            onClick = { 
                                currentDirectory = File(sdCardPath)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SdCard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SD Card", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Divider()

                // Lista de pastas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Botão voltar (se não estiver na raiz)
                            if (currentDirectory.parent != null) {
                                item {
                                    ListItem(
                                        headlineContent = { Text("..") },
                                        leadingContent = {
                                            Icon(
                                                Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            currentDirectory.parentFile?.let { parent ->
                                                currentDirectory = parent
                                            }
                                        }
                                    )
                                    Divider()
                                }
                            }

                            // Lista de pastas
                            items(folders) { folder ->
                                ListItem(
                                    headlineContent = { Text(folder.name) },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        currentDirectory = folder
                                    }
                                )
                                Divider()
                            }

                            // Mensagem se vazio
                            if (folders.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Nenhuma pasta encontrada",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Divider()

                // Botões de ação
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = { onFolderSelected(currentDirectory.absolutePath) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Selecionar")
                    }
                }
            }
        }
    }
}

private fun getDefaultDirectory(): File {
    val externalStorage = Environment.getExternalStorageDirectory()
    val gameFluxerFolder = File(externalStorage, "GameFluxer")
    if (!gameFluxerFolder.exists()) {
        try {
            gameFluxerFolder.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return gameFluxerFolder
}

private fun findSdCardPath(): String? {
    val possiblePaths = listOf(
        "/storage/sdcard1",
        "/storage/extSdCard",
        "/storage/sdcard0/external_sdcard",
        "/mnt/extSdCard",
        "/mnt/sdcard/external_sd",
        "/storage/external_SD"
    )
    
    // Verifica também em /storage/
    val storageDir = File("/storage")
    if (storageDir.exists()) {
        storageDir.listFiles()?.forEach { file ->
            if (file.isDirectory && 
                !file.name.equals("emulated", ignoreCase = true) &&
                !file.name.equals("self", ignoreCase = true) &&
                file.canRead()) {
                return file.absolutePath
            }
        }
    }
    
    // Tenta os caminhos conhecidos
    for (path in possiblePaths) {
        val file = File(path)
        if (file.exists() && file.isDirectory && file.canRead()) {
            return path
        }
    }
    
    return null
}
