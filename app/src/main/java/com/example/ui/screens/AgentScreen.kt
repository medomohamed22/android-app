package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.WebSource
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AgentScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val isWebSearch by viewModel.isWebSearchEnabled.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    var promptText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() }
                val name = uri.lastPathSegment?.split('/')?.last() ?: "attachment.txt"
                if (text != null) {
                    viewModel.addAttachment(name, text)
                }
            } catch (e: Exception) {
                viewModel.postToast("فشل قراءة الملف: ${e.message}", true)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Chat scroll area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                WelcomeView(
                    onSelectPrompt = { prompt ->
                        promptText = prompt
                        viewModel.sendMessage(prompt)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.role == "user") {
                            UserMessageBubble(msg = msg)
                        } else {
                            AssistantMessageBubble(
                                msg = msg,
                                isRunning = isRunning && msg.id == messages.lastOrNull()?.id,
                                onCopyCode = { code ->
                                    clipboardManager.setText(AnnotatedString(code))
                                    viewModel.postToast("تم نسخ الكود", false)
                                },
                                onOpenSources = { sources ->
                                    viewModel.openSourcesSheet(sources)
                                },
                                onRetry = {
                                    viewModel.retryLastMessage()
                                },
                                onOpenSettings = {
                                    viewModel.openSettings()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Composer area
        ComposerBar(
            promptText = promptText,
            onPromptChange = { promptText = it },
            attachments = attachments,
            onRemoveAttachment = { viewModel.removeAttachment(it) },
            isWebSearch = isWebSearch,
            onToggleWebSearch = { viewModel.toggleWebSearch() },
            isRunning = isRunning,
            onSend = {
                if (isRunning) {
                    viewModel.stopAgent()
                } else {
                    val t = promptText
                    promptText = ""
                    viewModel.sendMessage(t)
                }
            },
            onAttachClick = {
                filePicker.launch(arrayOf("*/*"))
            }
        )
    }
}

@Composable
fun WelcomeView(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickCards = listOf(
        QuickActionData(
            icon = Icons.Default.Bolt,
            title = "صفحة هبوط عربية",
            category = "HTML & CSS",
            prompt = "أنشئ صفحة هبوط بسيطة وجميلة باللغة العربية باستخدام HTML وCSS داخل مساحة العمل الحالية"
        ),
        QuickActionData(
            icon = Icons.Default.DarkMode,
            title = "الوضع الليلي",
            category = "Dark Theme",
            prompt = "أضف وضعاً ليلياً (Dark Mode) إلى index.html مع زر تبديل وحفظ التفضيل في localStorage"
        ),
        QuickActionData(
            icon = Icons.Default.Build,
            title = "تحسين الكود البرمجي",
            category = "Optimization",
            prompt = "راجع app.js واقترح تحسينات ثم طبّق أهمها"
        ),
        QuickActionData(
            icon = Icons.Default.FolderOpen,
            title = "بنية المشروع",
            category = "Architecture",
            prompt = "اشرح بنية مساحة العمل الحالية وما يفعله كل ملف باختصار"
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status & Project Banner (matching Professional Polish Card)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .border(1.dp, AiWayBorder, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "حالة مساحة العمل",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AiWayTextMuted
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AiWayPrimarySoft2)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "جاهز للعمل",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AiWayPrimaryDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Ai Way Pro Studio",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Targeting Android SDK 34 · وكيل برمجي متكامل",
                    fontSize = 12.5.sp,
                    color = AiWayTextMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = AiWayPrimary,
                    trackColor = AiWayBorder
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("12 أداة برمجية متصلة", fontSize = 10.5.sp, color = AiWayTextMuted)
                    Text("100% مساحة العمل جاهزة", fontSize = 10.5.sp, color = AiWayTextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2x2 Action Cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val chunked = quickCards.chunked(2)
            chunked.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { item ->
                        Surface(
                            onClick = { onSelectPrompt(item.prompt) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .weight(1f)
                                .height(108.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AiWayPrimarySoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = AiWayPrimaryDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = item.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.category,
                                        fontSize = 10.5.sp,
                                        color = AiWayTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class QuickActionData(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val category: String,
    val prompt: String
)

@Composable
fun UserMessageBubble(msg: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            color = AiWayPrimary, // #6750A4
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = msg.content,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp
                )

                if (msg.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        msg.attachments.forEach { att ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(att.name, color = Color.White, fontSize = 11.5.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantMessageBubble(
    msg: ChatMessage,
    isRunning: Boolean,
    onCopyCode: (String) -> Unit,
    onOpenSources: (List<WebSource>) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var isActivityOpen by remember(msg.id) { mutableStateOf(msg.isActivityOpen) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(AiWayPrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AiWayPrimaryDark,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Ai Way Pro",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AiWayPrimary
            )
        }

        // Card bubble
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tool activities
                if (msg.activity.isNotEmpty()) {
                    ToolActivityCard(
                        activity = msg.activity,
                        isActivityOpen = isActivityOpen,
                        isRunning = isRunning,
                        onToggleOpen = { isActivityOpen = !isActivityOpen }
                    )
                }

                // Text / Markdown content
                if (msg.content.isNotBlank()) {
                    FormattedMarkdown(
                        content = msg.content,
                        onCopyCode = onCopyCode
                    )
                }

                // Error Card
                if (msg.error != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AiWayDangerSoft)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AiWayDanger, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg.error ?: "خطأ",
                                    color = Color(0xFF601410),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onRetry,
                                    colors = ButtonDefaults.buttonColors(containerColor = AiWayPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إعادة المحاولة", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = onOpenSettings,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("فتح إعدادات المزود", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Web Sources Strip
        if (msg.sources.isNotEmpty()) {
            Surface(
                onClick = { onOpenSources(msg.sources) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = AiWayPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تم البحث في ${msg.sources.size} من مواقع الويب",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AiWayTextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Metrics Bar
        if (msg.metrics != null) {
            MetricsBar(metrics = msg.metrics!!)
        }
    }
}

@Composable
fun ComposerBar(
    promptText: String,
    onPromptChange: (String) -> Unit,
    attachments: List<com.example.data.model.Attachment>,
    onRemoveAttachment: (String) -> Unit,
    isWebSearch: Boolean,
    onToggleWebSearch: () -> Unit,
    isRunning: Boolean,
    onSend: () -> Unit,
    onAttachClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Attachment chips
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    attachments.forEach { att ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = AiWayPrimarySoft,
                            onClick = { onRemoveAttachment(att.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, tint = AiWayPrimaryDark, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = att.name,
                                    fontSize = 11.5.sp,
                                    color = AiWayPrimaryDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Close, contentDescription = "إزالة", tint = AiWayPrimaryDark, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }

            // Main composer border box
            Surface(
                shape = RoundedCornerShape(26.dp),
                border = CardDefaults.outlinedCardBorder(),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    TextField(
                        value = promptText,
                        onValueChange = onPromptChange,
                        placeholder = {
                            Text("اكتب طلبك للوكيل البرمجي…", color = AiWayTextMuted, fontSize = 14.sp)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp, max = 160.dp),
                        maxLines = 6
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = onAttachClick,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "إرفاق ملف",
                                    tint = AiWayTextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Web Search Pill
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isWebSearch) AiWayPrimarySoft2 else Color.Transparent,
                                border = if (isWebSearch) null else CardDefaults.outlinedCardBorder(),
                                onClick = onToggleWebSearch,
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = if (isWebSearch) AiWayPrimaryDark else AiWayTextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "بحث ويب",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isWebSearch) AiWayPrimaryDark else AiWayTextMuted
                                    )
                                }
                            }
                        }

                        // Send / Stop button (using Professional Polish action button style)
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isRunning) AiWayDanger
                                    else ProfessionalAccentLight
                                )
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                contentDescription = if (isRunning) "إيقاف" else "إرسال",
                                tint = if (isRunning) Color.White else ProfessionalAccentOnLight,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
