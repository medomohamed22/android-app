package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityItem
import com.example.data.model.Metrics
import com.example.data.workspace.WorkspaceManager
import com.example.ui.theme.*

@Composable
fun FileBadge(path: String, modifier: Modifier = Modifier) {
    val ext = WorkspaceManager.extOf(path).ifEmpty { "file" }
    val color = when (ext) {
        "html", "htm" -> Color(0xFFE34C26)
        "css", "scss" -> Color(0xFF2965F1)
        "js", "mjs", "ts", "jsx", "tsx" -> Color(0xFFB7950B)
        "json" -> Color(0xFF6750A4)
        "py" -> Color(0xFF3572A5)
        "kt", "kts" -> Color(0xFF7F52FF)
        "md" -> Color(0xFF49454F)
        "svg", "xml" -> Color(0xFFE65100)
        else -> Color(0xFF49454F)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ext.take(4).uppercase(),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
fun ToolActivityCard(
    activity: List<ActivityItem>,
    isActivityOpen: Boolean,
    isRunning: Boolean,
    onToggleOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (activity.isEmpty()) return

    val n = activity.size
    val countLabel = when {
        n == 1 -> "عملية واحدة"
        n == 2 -> "عمليتان"
        n in 3..10 -> "$n عمليات"
        else -> "$n عملية"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AiWayBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) // #F3EDF7
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleOpen() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AiWayPrimarySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = AiWayPrimaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "أدوات الوكيل البرمجية",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "($countLabel)",
                    fontSize = 12.sp,
                    color = AiWayTextMuted
                )
                if (isRunning) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "جارٍ المعالجة…",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AiWayPrimary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val rotation by animateFloatAsState(targetValue = if (isActivityOpen) 180f else 0f, label = "arrow")
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AiWayTextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }

            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = AiWayPrimary,
                    trackColor = AiWayBorder
                )
            }

            AnimatedVisibility(visible = isActivityOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    activity.forEach { item ->
                        ActivityRow(item = item)
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityRow(item: ActivityItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AiWayBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (item.tool) {
            "list_files" -> Icons.Default.List
            "search_workspace" -> Icons.Default.Search
            "read_lines" -> Icons.Default.Description
            "find_symbol" -> Icons.Default.Code
            "create_file" -> Icons.Default.NoteAdd
            "create_folder" -> Icons.Default.CreateNewFolder
            "edit_lines", "replace_exact" -> Icons.Default.Edit
            "apply_patch" -> Icons.Default.Tune
            "rename_path" -> Icons.Default.DriveFileRenameOutline
            "delete_path" -> Icons.Default.DeleteOutline
            "validate_file" -> Icons.Default.CheckCircleOutline
            "review_changed" -> Icons.Default.Visibility
            "web" -> Icons.Default.Public
            else -> Icons.Default.Bolt
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AiWayPrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AiWayPrimaryDark,
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = item.label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = item.detail,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            color = AiWayTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(6.dp))

        when (item.status) {
            "running" -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AiWayPrimary
                )
            }
            "done" -> {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(AiWaySuccessSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = AiWaySuccess,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(AiWayDangerSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = AiWayDanger,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FormattedMarkdown(
    content: String,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parts = remember(content) {
        content.split(Regex("(?=```)|(?<=```[\\s\\S]*?```)"))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            if (part.startsWith("```")) {
                val lines = part.removePrefix("```").removeSuffix("```").split('\n', limit = 2)
                val lang = lines.getOrNull(0)?.trim() ?: ""
                val code = lines.getOrNull(1) ?: ""
                CodeSnippetCard(lang = lang, code = code, onCopy = onCopyCode)
            } else if (part.isNotBlank()) {
                Text(
                    text = renderStyledText(part),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun CodeSnippetCard(lang: String, code: String, onCopy: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AiWayBorder, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = lang.ifEmpty { "code" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AiWayTextMuted
                )
                Surface(
                    onClick = { onCopy(code) },
                    shape = RoundedCornerShape(999.dp),
                    color = AiWayPrimarySoft2
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = AiWayPrimaryDark)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AiWayPrimaryDark)
                    }
                }
            }
            HorizontalDivider(color = AiWayBorder, thickness = 1.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1D1B20)) // Professional dark code background
                    .padding(14.dp)
            ) {
                Text(
                    text = code.trimEnd(),
                    color = Color(0xFFE6E0E9),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

private fun renderStyledText(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(`[^`]+`)|(\\*\\*[^*]+\\*\\*)|(#{1,4}\\s.*)")
        val matches = regex.findAll(text)

        matches.forEach { m ->
            if (m.range.first > cursor) {
                append(text.substring(cursor, m.range.first))
            }
            val v = m.value
            when {
                v.startsWith("`") && v.endsWith("`") -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            background = AiWayPrimarySoft,
                            color = AiWayPrimaryDark
                        )
                    )
                    append(v.removeSurrounding("`"))
                    pop()
                }
                v.startsWith("**") && v.endsWith("**") -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(v.removeSurrounding("**"))
                    pop()
                }
                v.startsWith("#") -> {
                    val clean = v.replace(Regex("^#+\\s*"), "")
                    pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = AiWayPrimary))
                    append(clean)
                    pop()
                }
                else -> append(v)
            }
            cursor = m.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

@Composable
fun MetricsBar(metrics: Metrics, modifier: Modifier = Modifier) {
    val durationText = if (metrics.elapsed < 1000) "${metrics.elapsed}ms" else String.format("%.1fث", metrics.elapsed / 1000.0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(13.dp), tint = AiWayTextMuted2)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = metrics.model.split('/').last().take(18),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AiWayTextMuted2
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Numbers, contentDescription = null, modifier = Modifier.size(13.dp), tint = AiWayTextMuted2)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "${if (metrics.approx) "~" else ""}${metrics.tokens} رمز",
                fontSize = 11.sp,
                color = AiWayTextMuted2
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(13.dp), tint = AiWayTextMuted2)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "${metrics.requests} طلب${if (metrics.tavily > 0) " + ${metrics.tavily} بحث" else ""}",
                fontSize = 11.sp,
                color = AiWayTextMuted2
            )
        }

        if (metrics.tools > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(13.dp), tint = AiWayTextMuted2)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${metrics.tools} أدوات",
                    fontSize = 11.sp,
                    color = AiWayTextMuted2
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(13.dp), tint = AiWayTextMuted2)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = durationText,
                fontSize = 11.sp,
                color = AiWayTextMuted2
            )
        }
    }
}
