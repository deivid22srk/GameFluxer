package com.gamestore.app.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamestore.app.data.model.Game
import com.gamestore.app.ui.viewmodel.DatabaseManagerViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformGamesScreen(
    platformName: String,
    onBackClick: () -> Unit,
    viewModel: DatabaseManagerViewModel = viewModel()
) {
    val gamesMap by viewModel.gamesMap.collectAsState()
    val games = gamesMap[platformName] ?: emptyList()
    
    var searchQuery by remember { mutableStateOf("") }
    var showAddGameDialog by remember { mutableStateOf(false) }
    var showEditGameDialog by remember { mutableStateOf(false) }
    var selectedGame by remember { mutableStateOf<Game?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var gameToDelete by remember { mutableStateOf<Game?>(null) }
    
    val filteredGames = remember(games, searchQuery) {
        if (searchQuery.isBlank()) {
            games
        } else {
            games.filter { game ->
                game.name.contains(searchQuery, ignoreCase = true) ||
                game.developer.contains(searchQuery, ignoreCase = true) ||
                game.category.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        platformName, 
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
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedGame = null
                    showAddGameDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Jogo")
            }
        }
    ) { paddingValues ->
        Column(
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
                )
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.padding(16.dp)
            )
            
            if (filteredGames.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Nenhum jogo encontrado" else "Nenhum resultado para \"$searchQuery\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Adicione jogos usando o botão +" else "Tente outra pesquisa",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "${filteredGames.size} ${if (filteredGames.size == 1) "jogo" else "jogos"}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(filteredGames) { game ->
                        GameListItem(
                            game = game,
                            onEdit = {
                                selectedGame = game
                                showEditGameDialog = true
                            },
                            onDelete = {
                                gameToDelete = game
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    if (showAddGameDialog) {
        GameEditorDialog(
            platformName = platformName,
            onDismiss = { showAddGameDialog = false },
            onConfirm = { game ->
                viewModel.addGame(platformName, game)
                showAddGameDialog = false
            }
        )
    }
    
    if (showEditGameDialog && selectedGame != null) {
        GameEditorDialog(
            platformName = platformName,
            game = selectedGame,
            onDismiss = { showEditGameDialog = false },
            onConfirm = { game ->
                viewModel.updateGame(platformName, selectedGame!!.id, game)
                showEditGameDialog = false
            }
        )
    }
    
    if (showDeleteConfirmDialog && gameToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Excluir Jogo?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Tem certeza que deseja excluir \"${gameToDelete!!.name}\"? Esta ação não pode ser desfeita.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGame(platformName, gameToDelete!!.id)
                        showDeleteConfirmDialog = false
                        gameToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Pesquisar jogos...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Limpar")
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
fun GameListItem(
    game: Game,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = game.version,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = game.size,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                if (game.category.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = game.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GameEditorDialog(
    platformName: String,
    game: Game? = null,
    onDismiss: () -> Unit,
    onConfirm: (Game) -> Unit
) {
    var name by remember { mutableStateOf(game?.name ?: "") }
    var description by remember { mutableStateOf(game?.description ?: "") }
    var version by remember { mutableStateOf(game?.version ?: "1.0") }
    var size by remember { mutableStateOf(game?.size ?: "") }
    var rating by remember { mutableStateOf(game?.rating?.toString() ?: "0.0") }
    var developer by remember { mutableStateOf(game?.developer ?: "") }
    var category by remember { mutableStateOf(game?.category ?: "") }
    var iconUrl by remember { mutableStateOf(game?.iconUrl ?: "") }
    var downloadUrl by remember { mutableStateOf(game?.downloadUrl ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (game == null) Icons.Default.Add else Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { 
            Text(
                if (game == null) "Adicionar Jogo" else "Editar Jogo", 
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descrição") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = version,
                            onValueChange = { version = it },
                            label = { Text("Versão") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = size,
                            onValueChange = { size = it },
                            label = { Text("Tamanho") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = rating,
                        onValueChange = { rating = it },
                        label = { Text("Avaliação (0-5)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = developer,
                        onValueChange = { developer = it },
                        label = { Text("Desenvolvedor") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Categoria") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = iconUrl,
                        onValueChange = { iconUrl = it },
                        label = { Text("URL do Ícone") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = downloadUrl,
                        onValueChange = { downloadUrl = it },
                        label = { Text("URL de Download *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newGame = Game(
                        id = game?.id ?: UUID.randomUUID().toString(),
                        name = name,
                        description = description,
                        version = version,
                        size = size,
                        rating = rating.toFloatOrNull() ?: 0f,
                        developer = developer,
                        category = category,
                        platform = platformName,
                        iconUrl = iconUrl.ifBlank { null },
                        bannerUrl = game?.bannerUrl,
                        screenshots = game?.screenshots ?: "",
                        downloadUrl = downloadUrl,
                        releaseDate = game?.releaseDate
                    )
                    onConfirm(newGame)
                },
                enabled = name.isNotBlank() && downloadUrl.isNotBlank()
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}