package com.codetrio.spatialflow.ui.explore

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.viewmodel.AccountViewModel
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSignInScreen(
    accountViewModel: AccountViewModel,
    onSignInSuccess: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    isOnboarding: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign In with Google", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val cookieManager = CookieManager.getInstance()
                                val cookies = cookieManager.getCookie("https://music.youtube.com")
                                if (cookies != null && cookies.contains("SAPISID") && cookies.contains("HSID") && !isLoading) {
                                    isLoading = true
                                    scope.launch {
                                        // Save credentials and set cookie
                                        accountViewModel.onLoggedIn(cookies)
                                        
                                        // Fetch user profile to get username
                                        val result = YouTubeMusic.accountProfile()
                                        val username = result.getOrNull()?.name ?: "Connected User"
                                        
                                        if (!isOnboarding) {
                                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Login Successful: $username")
                                        }
                                        onSignInSuccess()
                                    }
                                }
                            }
                        }
                        loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https://music.youtube.com/")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
