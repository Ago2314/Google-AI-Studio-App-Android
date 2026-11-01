package com.example.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.AIStudioBottomBar
import com.example.ui.components.AIStudioTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.SettingsDialog
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AIStudioScreen(
    viewModel: AIStudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var initialLoadCompleted by remember { mutableStateOf(false) }

    // Launcher for handling file uploads in AI Studio web
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val clipData = data.clipData!!
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else {
            null
        }
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    // Lifecycle observer: pause JS timers and background web rendering when app is paused
    DisposableEffect(lifecycleOwner, webViewInstance) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webViewInstance?.onPause()
                    webViewInstance?.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (uiState.activeTab == AppTab.WEB_STUDIO) {
                        webViewInstance?.onResume()
                        webViewInstance?.resumeTimers()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Hibernate/Resume WebView when switching tabs to save RAM & battery
    LaunchedEffect(uiState.activeTab) {
        val webView = webViewInstance ?: return@LaunchedEffect
        if (uiState.activeTab == AppTab.WEB_STUDIO) {
            webView.onResume()
            webView.resumeTimers()
        } else {
            webView.onPause()
            webView.pauseTimers()
        }
    }

    // Deep Prompt Injection into Web Studio via evaluateJavascript
    LaunchedEffect(uiState.pendingPromptToInject, webViewInstance) {
        val promptText = uiState.pendingPromptToInject ?: return@LaunchedEffect
        val webView = webViewInstance ?: return@LaunchedEffect

        val escapedPrompt = JSONObject.quote(promptText)
        val jsInjection = """
            (function(text) {
                try {
                    let el = document.querySelector('textarea, div[contenteditable="true"], ms-text-box textarea, input[type="text"]');
                    if (el) {
                        if (el.tagName.toLowerCase() === 'textarea' || el.tagName.toLowerCase() === 'input') {
                            el.value = text;
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                        } else {
                            el.innerText = text;
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                        el.focus();
                        return "INJECTED";
                    }
                    return "NO_ELEMENT";
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })($escapedPrompt);
        """.trimIndent()

        webView.evaluateJavascript(jsInjection) {
            viewModel.showSnackbar("Prompt injected into Web Studio input")
            viewModel.clearPendingPromptToInject()
        }
    }

    // Web-to-Native History Extractor function
    val performWebSync = {
        val webView = webViewInstance
        if (webView == null) {
            viewModel.showSnackbar("Web Studio loading...")
        } else {
            val script = """
                (function() {
                    let results = [];
                    let elements = document.querySelectorAll('ms-nav-item, nav a, .recent-item, [role="treeitem"], mat-list-item, div.chat-history-item, .prompt-title');
                    elements.forEach(function(el) {
                        let text = (el.innerText || el.textContent || "").trim();
                        if (text.length > 2 && text.length < 80 && !results.includes(text)) {
                            results.push(text);
                        }
                    });
                    return JSON.stringify(results);
                })();
            """.trimIndent()

            webView.evaluateJavascript(script) { jsonResult ->
                try {
                    val raw = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                        JSONTokener(jsonResult).nextValue().toString()
                    } else {
                        jsonResult
                    }
                    val array = JSONArray(raw)
                    val titles = mutableListOf<String>()
                    for (i in 0 until array.length()) {
                        titles.add(array.getString(i))
                    }
                    viewModel.syncWebHistory(titles)
                } catch (e: Exception) {
                    viewModel.showSnackbar("Web Studio history scanned")
                }
            }
        }
    }

    // Hardware Back Button navigation handling
    BackHandler(enabled = true) {
        when {
            uiState.activeTab != AppTab.WEB_STUDIO -> {
                viewModel.switchTab(AppTab.WEB_STUDIO)
            }
            webViewInstance?.canGoBack() == true -> {
                webViewInstance?.goBack()
            }
            else -> {
                (context as? Activity)?.finish()
            }
        }
    }

    // Observe snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_studio_main_scaffold"),
        topBar = {
            AIStudioTopBar(
                activeTab = uiState.activeTab,
                pageTitle = uiState.pageTitle,
                currentUrl = uiState.currentUrl,
                isLoading = uiState.isLoading,
                progress = uiState.progress,
                canGoBack = uiState.canGoBack,
                canGoForward = uiState.canGoForward,
                isDesktopMode = uiState.isDesktopMode,
                onWebBack = { webViewInstance?.goBack() },
                onWebForward = { webViewInstance?.goForward() },
                onRefresh = { webViewInstance?.reload() },
                onToggleDesktop = {
                    val isDesktop = viewModel.toggleDesktopMode()
                    webViewInstance?.let { webView ->
                        updateUserAgent(webView, isDesktop)
                        webView.reload()
                    }
                },
                onOpenInBrowser = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uiState.currentUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        viewModel.showSnackbar("Unable to open browser")
                    }
                },
                onShareUrl = {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, uiState.pageTitle)
                            putExtra(Intent.EXTRA_TEXT, uiState.currentUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share AI Studio Link"))
                    } catch (e: Exception) {
                        viewModel.showSnackbar("Unable to share link")
                    }
                },
                onClearData = { viewModel.showClearDataDialog(true) },
                onOpenSettings = { viewModel.showSettings(true) }
            )
        },
        bottomBar = {
            AIStudioBottomBar(
                currentTab = uiState.activeTab,
                onTabSelected = { viewModel.switchTab(it) },
                onOpenSettings = { viewModel.showSettings(true) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.activeTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "TabCrossfade"
        ) { tab ->
            when (tab) {
                AppTab.WEB_STUDIO -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OfflineBanner(
                            isOnline = uiState.isOnline,
                            onRetry = { webViewInstance?.reload() }
                        )

                        val pullRefreshState = rememberPullToRefreshState()

                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = {
                                viewModel.setRefreshing(true)
                                webViewInstance?.reload()
                            },
                            state = pullRefreshState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("pull_to_refresh_box")
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("ai_studio_webview"),
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )

                                            configureWebView(this)
                                            updateUserAgent(this, uiState.isDesktopMode)

                                            webChromeClient = object : WebChromeClient() {
                                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                    super.onProgressChanged(view, newProgress)
                                                    viewModel.onProgressChanged(newProgress)
                                                    if (newProgress >= 90) {
                                                        initialLoadCompleted = true
                                                    }
                                                    viewModel.updateNavigationState(
                                                        canGoBack = view?.canGoBack() == true,
                                                        canGoForward = view?.canGoForward() == true
                                                    )
                                                }

                                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                                    super.onReceivedTitle(view, title)
                                                    viewModel.onPageFinished(view?.url ?: "", title)
                                                }

                                                override fun onShowFileChooser(
                                                    webView: WebView?,
                                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                                    fileChooserParams: FileChooserParams?
                                                ): Boolean {
                                                    fileUploadCallback?.onReceiveValue(null)
                                                    fileUploadCallback = filePathCallback

                                                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                                        type = "*/*"
                                                        addCategory(Intent.CATEGORY_OPENABLE)
                                                    }

                                                    return try {
                                                        filePickerLauncher.launch(intent)
                                                        true
                                                    } catch (e: Exception) {
                                                        fileUploadCallback = null
                                                        false
                                                    }
                                                }
                                            }

                                            webViewClient = object : WebViewClient() {
                                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                    super.onPageStarted(view, url, favicon)
                                                    url?.let { viewModel.onPageStarted(it) }
                                                    viewModel.updateNavigationState(
                                                        canGoBack = view?.canGoBack() == true,
                                                        canGoForward = view?.canGoForward() == true
                                                    )
                                                }

                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    initialLoadCompleted = true
                                                    url?.let { viewModel.onPageFinished(it, view?.title) }
                                                    viewModel.updateNavigationState(
                                                        canGoBack = view?.canGoBack() == true,
                                                        canGoForward = view?.canGoForward() == true
                                                    )
                                                }

                                                override fun onReceivedError(
                                                    view: WebView?,
                                                    request: WebResourceRequest?,
                                                    error: WebResourceError?
                                                ) {
                                                    super.onReceivedError(view, request, error)
                                                    if (request?.isForMainFrame == true) {
                                                        val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                            error?.description?.toString() ?: "Failed to load page"
                                                        } else {
                                                            "Failed to load page"
                                                        }
                                                        viewModel.onErrorReceived(desc)
                                                    }
                                                }

                                                override fun shouldOverrideUrlLoading(
                                                    view: WebView?,
                                                    request: WebResourceRequest?
                                                ): Boolean {
                                                    val url = request?.url?.toString() ?: return false
                                                    return if (isInternalDomain(url)) {
                                                        false
                                                    } else {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                            ctx.startActivity(intent)
                                                            true
                                                        } catch (e: Exception) {
                                                            false
                                                        }
                                                    }
                                                }
                                            }

                                            loadUrl(uiState.currentUrl)
                                            webViewInstance = this
                                        }
                                    },
                                    update = { webView ->
                                        webViewInstance = webView
                                    }
                                )

                                // Initial splash / loading overlay
                                if (!initialLoadCompleted && uiState.isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                            .testTag("splash_loading_overlay"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(44.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(20.dp))

                                            Text(
                                                text = "Google AI Studio",
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Text(
                                                text = "Initializing workspace...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Spacer(modifier = Modifier.height(24.dp))

                                            CircularProgressIndicator(
                                                modifier = Modifier.size(36.dp),
                                                strokeWidth = 3.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // Network error state banner / retry view
                                if (uiState.errorMessage != null && !uiState.isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(54.dp)
                                            )

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Text(
                                                text = "Unable to connect to AI Studio",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Text(
                                                text = uiState.errorMessage ?: "Please verify your internet connection.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )

                                            Spacer(modifier = Modifier.height(20.dp))

                                            Button(
                                                onClick = { webViewInstance?.reload() },
                                                modifier = Modifier.testTag("error_retry_button")
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Retry")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AppTab.NATIVE_PLAYGROUND -> {
                    NativePlaygroundScreen(
                        messages = uiState.chatMessages,
                        apiKey = uiState.geminiApiKey,
                        isGenerating = uiState.isGeneratingNative,
                        selectedModel = uiState.selectedModel,
                        pendingAttachments = uiState.pendingAttachments,
                        onAddAttachment = { uri -> viewModel.addAttachment(context, uri) },
                        onRemoveAttachment = { uriStr -> viewModel.removeAttachment(uriStr) },
                        onModelChange = { viewModel.setSelectedModel(it) },
                        onSendMessage = { prompt, sys ->
                            viewModel.sendNativeChatMessage(prompt, sys)
                        },
                        onClearChat = { viewModel.clearNativeChat() },
                        onOpenSettings = { viewModel.showSettings(true) },
                        onShowSnackbar = { viewModel.showSnackbar(it) }
                    )
                }

                AppTab.PROMPT_LIBRARY -> {
                    PromptLibraryScreen(
                        prompts = uiState.savedPrompts,
                        onSavePrompt = { viewModel.savePrompt(it) },
                        onDeletePrompt = { viewModel.deletePrompt(it) },
                        onUseInPlayground = { prompt ->
                            viewModel.sendNativeChatMessage(prompt.content, prompt.systemInstruction)
                            viewModel.switchTab(AppTab.NATIVE_PLAYGROUND)
                        },
                        onInjectIntoWeb = { prompt ->
                            viewModel.injectPromptIntoWeb(prompt)
                        },
                        onRequestWebSync = {
                            performWebSync()
                        },
                        onShowSnackbar = { viewModel.showSnackbar(it) }
                    )
                }
            }
        }
    }

    // Settings Dialog
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            currentApiKey = uiState.geminiApiKey,
            isDesktopMode = uiState.isDesktopMode,
            isAmoledTheme = uiState.isAmoledTheme,
            selectedModel = uiState.selectedModel,
            onSaveApiKey = { key -> viewModel.saveApiKey(key) },
            onClearApiKey = { viewModel.clearApiKey() },
            onToggleDesktopMode = {
                val isDesktop = viewModel.toggleDesktopMode()
                webViewInstance?.let { webView ->
                    updateUserAgent(webView, isDesktop)
                    webView.reload()
                }
            },
            onToggleAmoledTheme = { viewModel.toggleAmoledTheme() },
            onSelectModel = { viewModel.setSelectedModel(it) },
            onClearCache = {
                clearWebData(context, webViewInstance)
                viewModel.showSnackbar("Cache and browsing data cleared")
            },
            onDismiss = { viewModel.showSettings(false) },
            onShowSnackbar = { msg -> viewModel.showSnackbar(msg) }
        )
    }

    // Clear Data Confirmation Dialog
    if (uiState.showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearDataDialog(false) },
            title = { Text("Clear Browsing Data?") },
            text = { Text("This will clear all cached web resources, cookies, and local web storage for Google AI Studio.") },
            confirmButton = {
                Button(
                    onClick = {
                        clearWebData(context, webViewInstance)
                        viewModel.showClearDataDialog(false)
                        viewModel.showSnackbar("Browsing data cleared successfully")
                        webViewInstance?.reload()
                    },
                    modifier = Modifier.testTag("confirm_clear_data_button")
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearDataDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Cleanup WebView when leaving composition
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }
}

/**
 * Configure standard WebView settings for modern web apps like Google AI Studio.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun configureWebView(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = false
        javaScriptCanOpenWindowsAutomatically = true
    }

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webView, true)
    }
}

/**
 * Switch User-Agent between clean Mobile Chrome and Desktop Chrome.
 * Removing "; wv" and "Version/4.0" prevents Google sign-in "disallowed_useragent" restrictions.
 */
private fun updateUserAgent(webView: WebView, isDesktop: Boolean) {
    if (isDesktop) {
        webView.settings.userAgentString = DESKTOP_USER_AGENT
    } else {
        val defaultUa = WebSettings.getDefaultUserAgent(webView.context)
        val cleanMobileUa = defaultUa
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
        webView.settings.userAgentString = cleanMobileUa
    }
}

/**
 * Checks if a URL belongs to the internal Google AI Studio / Google Auth ecosystem.
 */
private fun isInternalDomain(url: String): Boolean {
    val lower = url.lowercase()
    return lower.startsWith("https://aistudio.google.com") ||
            lower.contains("accounts.google.com") ||
            lower.contains("accounts.youtube.com") ||
            lower.contains("google.com/accounts") ||
            lower.contains("myaccount.google.com") ||
            lower.contains("alkalimakersuite") ||
            lower.contains("makersuite.google.com") ||
            lower.contains("apis.google.com") ||
            lower.contains("gstatic.com") ||
            lower.contains("googleusercontent.com")
}

/**
 * Clears web cache, cookies, and local database storage.
 */
private fun clearWebData(context: Context, webView: WebView?) {
    webView?.clearCache(true)
    webView?.clearHistory()
    webView?.clearFormData()
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
}
