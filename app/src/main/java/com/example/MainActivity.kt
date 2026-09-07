package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.components.AiWayBottomNav
import com.example.ui.components.AiWayTopBar
import com.example.ui.dialogs.*
import com.example.ui.screens.AgentScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.theme.AiWayTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiWayTheme {
                val viewModel: MainViewModel = viewModel()
                val selectedView by viewModel.selectedView.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val chatSessions by viewModel.chatSessions.collectAsState()
                val currentSessionId by viewModel.currentSessionId.collectAsState()

                val showSettings by viewModel.showSettingsDialog.collectAsState()
                val showHistory by viewModel.showHistoryDrawer.collectAsState()
                val showPreview by viewModel.showPreviewDialog.collectAsState()
                val previewHtml by viewModel.previewHtml.collectAsState()
                val previewTitle by viewModel.previewTitle.collectAsState()
                val showSources by viewModel.showSourcesSheet.collectAsState()
                val sourcesList by viewModel.sourcesSheetList.collectAsState()
                val inputDialogState by viewModel.inputDialog.collectAsState()

                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    viewModel.toastEvent.collectLatest { (message, _) ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        AiWayTopBar(
                            currentModel = settings.model,
                            modelsList = settings.models,
                            onOpenHistory = { viewModel.openHistoryDrawer() },
                            onNewChat = { viewModel.startNewChat() },
                            onOpenSettings = { viewModel.openSettings() },
                            onSelectModel = { viewModel.selectModel(it) },
                            onFetchModels = {
                                coroutineScope.launch {
                                    try {
                                        val list = viewModel.fetchModelsList()
                                        viewModel.postToast("تم جلب ${list.size} نموذجاً بنجاح", false)
                                    } catch (e: Exception) {
                                        viewModel.postToast("فشل جلب النماذج: ${e.message}", true)
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        AiWayBottomNav(
                            selectedView = selectedView,
                            onSelectView = { viewModel.setView(it) }
                        )
                    },
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    },
                    contentWindowInsets = WindowInsets.statusBars
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Crossfade(targetState = selectedView, label = "ScreenTransition") { view ->
                            when (view) {
                                "agent" -> AgentScreen(viewModel = viewModel)
                                "editor" -> EditorScreen(viewModel = viewModel)
                                "files" -> FilesScreen(viewModel = viewModel)
                            }
                        }
                    }

                    // Modals
                    if (showSettings) {
                        SettingsDialog(
                            currentSettings = settings,
                            onDismiss = { viewModel.closeSettings() },
                            onSave = { viewModel.saveSettings(it) },
                            onFetchModels = { viewModel.fetchModelsList() },
                            onTestConnection = { viewModel.testApiConnection() }
                        )
                    }

                    if (showHistory) {
                        HistoryDrawerModal(
                            sessions = chatSessions,
                            currentSessionId = currentSessionId,
                            onDismiss = { viewModel.closeHistoryDrawer() },
                            onSelectSession = { viewModel.openChatSession(it) },
                            onDeleteSession = { viewModel.deleteChatSession(it) },
                            onNewChat = { viewModel.startNewChat() },
                            onOpenSettings = {
                                viewModel.closeHistoryDrawer()
                                viewModel.openSettings()
                            }
                        )
                    }

                    if (showPreview) {
                        PreviewDialog(
                            html = previewHtml,
                            title = previewTitle,
                            onDismiss = { viewModel.closePreview() },
                            onRefresh = { viewModel.refreshPreview() }
                        )
                    }

                    if (showSources) {
                        SourcesBottomSheetModal(
                            sources = sourcesList,
                            onDismiss = { viewModel.closeSourcesSheet() }
                        )
                    }

                    if (inputDialogState != null) {
                        GenericInputDialog(
                            state = inputDialogState!!,
                            onDismiss = { viewModel.dismissInputDialog() }
                        )
                    }
                }
            }
        }
    }
}
