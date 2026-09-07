package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.workspace.WorkspaceManager
import com.example.ui.MainViewModel
import com.example.ui.components.FileBadge
import com.example.ui.theme.*

@Composable
fun EditorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val activePath by viewModel.activePath.collectAsState()
    val openTabs by viewModel.openTabs.collectAsState()
    val dirtyPaths by viewModel.dirtyPaths.collectAsState()
    val editorText by viewModel.editorText.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tabs Strip
        if (openTabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                openTabs.forEach { path ->
                    val isActive = path == activePath
                    val isDirty = dirtyPaths.contains(path)
                    val baseName = path.split('/').last()

                    Surface(
                        onClick = { viewModel.openFile(path) },
                        color = if (isActive) AiWayPrimarySoft2 else Color.Transparent,
                        modifier = Modifier.height(46.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileBadge(path = path)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = baseName,
                                fontSize = 13.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) AiWayPrimaryDark else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            if (isDirty) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(AiWayWarning)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.closeTab(path) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق التبويب",
                                    tint = AiWayTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    VerticalDivider(color = AiWayBorder, modifier = Modifier.height(28.dp))
                }
            }
            HorizontalDivider(color = AiWayBorder, thickness = 1.dp)
        }

        // Toolbar
        if (activePath != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = activePath ?: "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AiWayTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Save
                    IconButton(
                        onClick = { viewModel.saveActiveFile() },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "حفظ", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
                    }

                    // Copy
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(editorText))
                            viewModel.postToast("تم نسخ محتوى الملف", false)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
                    }

                    // Download / Share
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, activePath?.split('/')?.last() ?: "code.txt")
                                putExtra(Intent.EXTRA_TEXT, editorText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الملف"))
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
                    }

                    // Preview (if HTML)
                    val isHtml = WorkspaceManager.extOf(activePath) in listOf("html", "htm")
                    if (isHtml) {
                        IconButton(
                            onClick = { viewModel.openPreview(activePath) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = "معاينة", tint = AiWayPrimary, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color = AiWayBorder, thickness = 1.dp)
        }

        // Editor Body
        if (activePath == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 380.dp)
                        .border(1.dp, AiWayBorder, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AiWayPrimarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = AiWayPrimaryDark,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "محرر الأكواد جاهز",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "اختر ملفاً من شجرة الملفات أو أنشئ ملفاً جديداً للبدء",
                            color = AiWayTextMuted,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.promptNewFile() },
                            colors = ButtonDefaults.buttonColors(containerColor = AiWayPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إنشاء ملف جديد", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val lines = remember(editorText) {
                WorkspaceManager.lineCount(editorText).coerceAtLeast(1)
            }
            val gutterNumbers = remember(lines) {
                (1..lines).joinToString("\n")
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Line numbers gutter
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant) // #F3EDF7
                            .padding(top = 12.dp, bottom = 40.dp, end = 10.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Text(
                            text = gutterNumbers,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            color = AiWayTextMuted2
                        )
                    }

                    VerticalDivider(color = AiWayBorder, modifier = Modifier.fillMaxHeight())

                    // Code text field
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 12.dp, bottom = 40.dp, start = 14.dp, end = 16.dp)
                    ) {
                        BasicTextField(
                            value = editorText,
                            onValueChange = { viewModel.onEditorTextChanged(it) },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.5.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(AiWayPrimary),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Status bar
            HorizontalDivider(color = AiWayBorder, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val lang = when (WorkspaceManager.extOf(activePath)) {
                    "html", "htm" -> "HTML"
                    "css", "scss" -> "CSS"
                    "js", "mjs" -> "JavaScript"
                    "ts" -> "TypeScript"
                    "json" -> "JSON"
                    "py" -> "Python"
                    "kt" -> "Kotlin"
                    "md" -> "Markdown"
                    else -> "Plain Text"
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AiWayPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = lang,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "$lines سطر · ${editorText.length} حرف",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = AiWayTextMuted
                )
            }
        }
    }
}
