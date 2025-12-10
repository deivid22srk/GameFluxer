package com.gamestore.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gamestore.app.data.model.Download
import com.gamestore.app.data.model.DownloadStatus
import com.gamestore.app.ui.viewmodel.DownloadViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    viewModel: DownloadViewModel = viewModel()
) {
    val downloads by viewModel.allDownloads.collectAsState()
    val activeDownloads = downloads.filter { 
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED 
    }
    val completedDownloads = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val pausedDownloads = downloads.filter { it.status == DownloadStatus.PAUSED }
    val failedDownloads = downloads.filter { 
        it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED 
    }
    var showDeleteDialog by remember { mutableStateOf<Download?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Downloads",
                            fontWeight = FontWeight.Bold
                        )
                        if (downloads.isNotEmpty()) {
                            Text(
                                "${downloads.size} ${if (downloads.size == 1) "item" else "itens"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (completedDownloads.isNotEmpty()) {
                        FilledTonalIconButton(
                            onClick = { viewModel.clearCompletedDownloads() }
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Limpar concluídos")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { paddingValues ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Nenhum download ativo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Os downloads que você iniciar aparecerão aqui",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeDownloads.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Em Andamento",
                            icon = Icons.Default.Download,
                            count = activeDownloads.size,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(activeDownloads, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) },
                            onCancel = { viewModel.cancelDownload(download.id) },
                            onDelete = { showDeleteDialog = download },
                            onInstall = { viewModel.installApk(LocalContext.current, download.filePath) }
                        )
                    }
                }

                if (pausedDownloads.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = "Pausados",
                            icon = Icons.Default.Pause,
                            count = pausedDownloads.size,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    items(pausedDownloads, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) },
                            onCancel = { viewModel.cancelDownload(download.id) },
                            onDelete = { showDeleteDialog = download },
                            onInstall = { viewModel.installApk(LocalContext.current, download.filePath) }
                        )
                    }
                }

                if (completedDownloads.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = "Concluídos",
                            icon = Icons.Default.CheckCircle,
                            count = completedDownloads.size,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    items(completedDownloads, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) },
                            onCancel = { viewModel.cancelDownload(download.id) },
                            onDelete = { showDeleteDialog = download },
                            onInstall = { viewModel.installApk(LocalContext.current, download.filePath) }
                        )
                    }
                }

                if (failedDownloads.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = "Com Problemas",
                            icon = Icons.Default.Error,
                            count = failedDownloads.size,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    items(failedDownloads, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) },
                            onCancel = { viewModel.cancelDownload(download.id) },
                            onDelete = { showDeleteDialog = download },
                            onInstall = { viewModel.installApk(LocalContext.current, download.filePath) }
                        )
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { download ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { 
                Text(
                    "Excluir download?",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text("O arquivo de ${download.gameName} será removido permanentemente.") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDownload(download)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun DownloadItemCard(
    download: Download,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onInstall: () -> Unit
) {
    val progress = if (download.totalBytes > 0) {
        download.downloadedBytes.toFloat() / download.totalBytes.toFloat()
    } else 0f
    
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (download.status) {
                DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = download.gameIconUrl,
                        contentDescription = download.gameName,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    
                    if (download.status == DownloadStatus.DOWNLOADING && progress > 0) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = animatedProgress,
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp,
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.gameName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = getStatusBackgroundColor(download.status)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                getStatusIcon(download.status),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = getStatusColor(download.status)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = getStatusText(download),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = getStatusColor(download.status)
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    when (download.status) {
                        DownloadStatus.DOWNLOADING -> {
                            FilledTonalIconButton(
                                onClick = onPause,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = "Pausar",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            FilledIconButton(
                                onClick = onResume,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Retomar",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (download.filePath.endsWith(".apk", ignoreCase = true)) {
                                    FilledIconButton(
                                        onClick = onInstall,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Android,
                                            contentDescription = "Instalar",
                                            tint = MaterialTheme.colorScheme.onTertiary
                                        )
                                    }
                                }
                                IconButton(onClick = onDelete) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Excluir",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Excluir",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        DownloadStatus.QUEUED -> {
                            IconButton(onClick = onCancel) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancelar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (download.status == DownloadStatus.DOWNLOADING || 
                download.status == DownloadStatus.PAUSED ||
                download.status == DownloadStatus.QUEUED) {
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Column {
                    LinearProgressIndicator(
                        progress = animatedProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when (download.status) {
                            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = formatBytes(download.downloadedBytes),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Baixado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (download.totalBytes > 0) {
                                    "${(progress * 100).roundToInt()}%"
                                } else {
                                    "0%"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Progresso",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatBytes(download.totalBytes),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (download.status == DownloadStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(12.dp))
                
                if (download.filePath.endsWith(".apk", ignoreCase = true)) {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Instalar APK",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatBytes(download.totalBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun getStatusColor(status: DownloadStatus): Color {
    return when (status) {
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.QUEUED -> MaterialTheme.colorScheme.secondary
    }
}

@Composable
fun getStatusBackgroundColor(status: DownloadStatus): Color {
    return when (status) {
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
        DownloadStatus.QUEUED -> MaterialTheme.colorScheme.secondaryContainer
    }
}

fun getStatusIcon(status: DownloadStatus): androidx.compose.ui.graphics.vector.ImageVector {
    return when (status) {
        DownloadStatus.DOWNLOADING -> Icons.Default.Download
        DownloadStatus.PAUSED -> Icons.Default.Pause
        DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
        DownloadStatus.FAILED -> Icons.Default.Error
        DownloadStatus.CANCELLED -> Icons.Default.Cancel
        DownloadStatus.QUEUED -> Icons.Default.HourglassEmpty
    }
}

fun getStatusText(download: Download): String {
    return when (download.status) {
        DownloadStatus.DOWNLOADING -> "Baixando"
        DownloadStatus.PAUSED -> "Pausado"
        DownloadStatus.COMPLETED -> "Concluído"
        DownloadStatus.FAILED -> "Falhou"
        DownloadStatus.CANCELLED -> "Cancelado"
        DownloadStatus.QUEUED -> "Na fila"
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
