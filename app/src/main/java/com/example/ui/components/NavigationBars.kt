package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun AiWayTopBar(
    currentModel: String,
    modelsList: List<String>,
    onOpenHistory: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectModel: (String) -> Unit,
    onFetchModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isModelMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Start: Menu / History Button (48dp touch target)
                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "سجل المحادثات",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Center: Brand & Model Picker Pill
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = { isModelMenuExpanded = true },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(AiWayPrimarySoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "AI",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AiWayPrimaryDark
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Ai Way Pro",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "✦",
                                        color = AiWayPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = currentModel.split('/').last().ifEmpty { "gpt-4o-mini" },
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AiWayTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = AiWayTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Model Selector Dropdown Menu
                    DropdownMenu(
                        expanded = isModelMenuExpanded,
                        onDismissRequest = { isModelMenuExpanded = false },
                        modifier = Modifier
                            .widthIn(min = 240.dp, max = 320.dp)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        val combinedModels = remember(modelsList, currentModel) {
                            (listOf(currentModel) + modelsList).distinct().filter { it.isNotBlank() }
                        }

                        Text(
                            text = "النماذج المتاحة",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AiWayTextMuted,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )

                        combinedModels.take(20).forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = m,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            fontWeight = if (m == currentModel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (m == currentModel) AiWayPrimary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (m == currentModel) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = AiWayPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectModel(m)
                                    isModelMenuExpanded = false
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = AiWayPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("جلب قائمة النماذج", fontSize = 13.sp, color = AiWayPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            },
                            onClick = {
                                isModelMenuExpanded = false
                                onFetchModels()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = AiWayPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("إعدادات المزود", fontSize = 13.sp, color = AiWayPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            },
                            onClick = {
                                isModelMenuExpanded = false
                                onOpenSettings()
                            }
                        )
                    }
                }

                // End: New Chat & Settings Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "محادثة جديدة",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        }
    }
}

@Composable
fun AiWayBottomNav(
    selectedView: String,
    onSelectView: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = MaterialTheme.colorScheme.surfaceVariant, // #F3EDF7
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selectedView == "agent",
            onClick = { onSelectView("agent") },
            icon = {
                Icon(
                    imageVector = if (selectedView == "agent") Icons.Filled.SmartToy else Icons.Outlined.SmartToy,
                    contentDescription = "الوكيل"
                )
            },
            label = {
                Text(
                    text = "الوكيل",
                    fontSize = 11.5.sp,
                    fontWeight = if (selectedView == "agent") FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = AiWayTextMuted,
                unselectedTextColor = AiWayTextMuted,
                indicatorColor = AiWayPrimarySoft2 // #E8DEF8 pill highlight
            )
        )

        NavigationBarItem(
            selected = selectedView == "editor",
            onClick = { onSelectView("editor") },
            icon = {
                Icon(
                    imageVector = if (selectedView == "editor") Icons.Filled.Code else Icons.Outlined.Code,
                    contentDescription = "المحرر"
                )
            },
            label = {
                Text(
                    text = "المحرر",
                    fontSize = 11.5.sp,
                    fontWeight = if (selectedView == "editor") FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = AiWayTextMuted,
                unselectedTextColor = AiWayTextMuted,
                indicatorColor = AiWayPrimarySoft2 // #E8DEF8 pill highlight
            )
        )

        NavigationBarItem(
            selected = selectedView == "files",
            onClick = { onSelectView("files") },
            icon = {
                Icon(
                    imageVector = if (selectedView == "files") Icons.Filled.Folder else Icons.Outlined.Folder,
                    contentDescription = "الملفات"
                )
            },
            label = {
                Text(
                    text = "الملفات",
                    fontSize = 11.5.sp,
                    fontWeight = if (selectedView == "files") FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = AiWayTextMuted,
                unselectedTextColor = AiWayTextMuted,
                indicatorColor = AiWayPrimarySoft2 // #E8DEF8 pill highlight
            )
        )
    }
}
