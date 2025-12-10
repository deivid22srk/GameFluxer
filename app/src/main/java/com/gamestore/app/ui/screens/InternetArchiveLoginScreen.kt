package com.gamestore.app.ui.screens

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamestore.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternetArchiveLoginScreen(
    onBackClick: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    var isLoading by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Login - Internet Archive", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (isLoggedIn) {
                        IconButton(
                            onClick = {
                                webView?.let { wv ->
                                    val cookieManager = CookieManager.getInstance()
                                    val cookies = cookieManager.getCookie("https://archive.org")
                                    if (cookies != null) {
                                        viewModel.setInternetArchiveCookies(cookies)
                                        onBackClick()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Salvar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                
                                val cookieManager = CookieManager.getInstance()
                                val cookies = cookieManager.getCookie("https://archive.org")
                                
                                if (cookies != null && cookies.contains("logged-in-sig")) {
                                    isLoggedIn = true
                                }
                            }
                        }
                        
                        loadUrl("https://archive.org/account/login")
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Carregando...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }
}
