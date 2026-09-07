package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.workspace.WorkspaceManager
import com.example.ui.MainViewModel
import com.example.ui.components.FileBadge
import com.example.ui.theme.*

data class FileTreeNode(
    val name: String,
    val path: String,
    val type: String, // "file" or "folder"
    val children: List<FileTreeNode> = emptyList()
)

@Composable
fun FilesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val revision by viewModel.workspaceRevision.collectAsState()
    val activePath by viewModel.activePath.collectAsState()
    val dirtyPaths by viewModel.dirtyPaths.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedItemForMenu by remember { mutableStateOf<String?>(null) }
    var menuAnchorOffset by remember { mutableStateOf(false) }

    val fileCount = remember(revision) {
        viewModel.workspace.files.values.count { it.type == "file" }
    }

    // File tree builder
    val treeNodes = remember(revision) {
        buildFileTree(viewModel.workspace)
    }

    // Upload launcher
    val uploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val list = mutableListOf<Pair<String, String>>()
        uris.forEach { uri ->
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                val name = uri.lastPathSegment?.split('/')?.last() ?: "uploaded_file.txt"
                if (text != null) {
                    list.add(Pair(name, text))
                }
            } catch (e: Exception) {
                viewModel.postToast("تعذر قراءة ${uri.lastPathSegment}: ${e.message}", true)
            }
        }
        if (list.isNotEmpty()) {
            viewModel.addFilesFromUpload(list)
        }
    }

    // Import Workspace JSON launcher
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) {
                    viewModel.importWorkspaceJson(text)
                }
            } catch (e: Exception) {
                viewModel.postToast("تعذر استيراد الملف: ${e.message}", true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "مساحة العمل",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        fileCount == 0 -> "لا توجد ملفات"
                        fileCount == 1 -> "ملف واحد"
                        fileCount == 2 -> "ملفان"
                        fileCount in 3..10 -> "$fileCount ملفات"
                        else -> "$fileCount ملفاً"
                    },
                    fontSize = 12.sp,
                    color = AiWayTextMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { uploadPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "رفع ملفات", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = { viewModel.promptNewFile() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "ملف جديد", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = { viewModel.promptNewFolder() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "مجلد جديد", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

        // Tree Area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (viewModel.workspace.files.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = AiWayBorderDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("مساحة العمل فارغة", color = AiWayTextMuted, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("أنشئ ملفاً جديداً أو ارفع ملفات أو اطلب من الوكيل إنشاء مشروع", color = AiWayTextMuted2, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(flattenTree(treeNodes, viewModel.workspace.collapsedFolders)) { (node, depth) ->
                        FileTreeRow(
                            node = node,
                            depth = depth,
                            isActive = node.path == activePath,
                            isDirty = dirtyPaths.contains(node.path),
                            isCollapsed = viewModel.workspace.collapsedFolders.contains(node.path),
                            onClick = {
                                if (node.type == "folder") {
                                    viewModel.toggleFolderCollapse(node.path)
                                } else {
                                    viewModel.openFile(node.path)
                                    viewModel.setView("editor")
                                }
                            },
                            onMoreClick = {
                                selectedItemForMenu = node.path
                            }
                        )
                    }
                }
            }
        }

        // Bottom Foot: Import / Export
        HorizontalDivider(color = AiWayBorder, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { importPicker.launch(arrayOf("application/json", "text/*")) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(AiWayBorderDark))
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(6.dp))
                Text("استيراد JSON", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    val json = viewModel.exportWorkspaceJson()
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "aiway-workspace.json")
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "تصدير مساحة العمل"))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AiWayPrimary)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير JSON", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Context Menu Dialog
    if (selectedItemForMenu != null) {
        val path = selectedItemForMenu!!
        val isFolder = viewModel.workspace.files[path]?.type == "folder"
        val isHtml = !isFolder && WorkspaceManager.extOf(path) in listOf("html", "htm")

        AlertDialog(
            onDismissRequest = { selectedItemForMenu = null },
            title = {
                Text(
                    text = path.split('/').last(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isFolder) {
                        TextButton(
                            onClick = {
                                selectedItemForMenu = null
                                viewModel.openFile(path)
                                viewModel.setView("editor")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = AiWayPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("فتح في المحرر", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        TextButton(
                            onClick = {
                                selectedItemForMenu = null
                                val content = viewModel.workspace.files[path]?.content ?: ""
                                clipboardManager.setText(AnnotatedString(content))
                                viewModel.postToast("تم نسخ المحتوى", false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = AiWayPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("نسخ المحتوى", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        if (isHtml) {
                            TextButton(
                                onClick = {
                                    selectedItemForMenu = null
                                    viewModel.openPreview(path)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp), tint = AiWayPrimary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("معاينة HTML", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                selectedItemForMenu = null
                                viewModel.promptNewFile(path)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp), tint = AiWayPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("ملف جديد هنا", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        TextButton(
                            onClick = {
                                selectedItemForMenu = null
                                viewModel.promptNewFolder(path)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp), tint = AiWayPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("مجلد جديد هنا", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            selectedItemForMenu = null
                            viewModel.promptRename(path)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = AiWayPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("إعادة تسمية / نقل", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    TextButton(
                        onClick = {
                            selectedItemForMenu = null
                            viewModel.promptDelete(path)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = AiWayDanger)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("حذف", color = AiWayDanger)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItemForMenu = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun FileTreeRow(
    node: FileTreeNode,
    depth: Int,
    isActive: Boolean,
    isDirty: Boolean,
    isCollapsed: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) AiWayPrimarySoft2 else Color.Transparent,
        modifier = Modifier.fillMaxWidth().height(42.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = (depth * 18 + 8).dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.type == "folder") {
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.Folder else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = AiWayPrimary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                FileBadge(path = node.path)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = node.name,
                fontSize = 13.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) AiWayPrimaryDark else MaterialTheme.colorScheme.onSurface,
                fontFamily = if (node.type == "file") FontFamily.Monospace else FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (isDirty) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(AiWayWarning)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "خيارات", tint = AiWayTextMuted2, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun buildFileTree(manager: WorkspaceManager): List<FileTreeNode> {
    class TempNode(val name: String, val path: String, var type: String) {
        val children = mutableMapOf<String, TempNode>()
    }

    val root = TempNode("", "", "folder")
    val sorted = manager.sortedFiles()

    sorted.forEach { (path, file) ->
        val segs = path.split('/')
        var curr = root
        segs.forEachIndexed { i, seg ->
            val subPath = segs.subList(0, i + 1).joinToString("/")
            val nodeType = if (i == segs.size - 1) file.type else "folder"
            curr = curr.children.getOrPut(seg) {
                TempNode(seg, subPath, nodeType)
            }
        }
    }

    fun convert(temp: TempNode): FileTreeNode {
        val sortedChildren = temp.children.values.sortedWith(
            compareBy<TempNode> { if (it.type == "folder") 0 else 1 }.thenBy { it.name }
        ).map { convert(it) }
        return FileTreeNode(temp.name, temp.path, temp.type, sortedChildren)
    }

    return root.children.values.sortedWith(
        compareBy<TempNode> { if (it.type == "folder") 0 else 1 }.thenBy { it.name }
    ).map { convert(it) }
}

private fun flattenTree(nodes: List<FileTreeNode>, collapsed: Set<String>, depth: Int = 0): List<Pair<FileTreeNode, Int>> {
    val list = mutableListOf<Pair<FileTreeNode, Int>>()
    nodes.forEach { node ->
        list.add(Pair(node, depth))
        if (node.type == "folder" && !collapsed.contains(node.path)) {
            list.addAll(flattenTree(node.children, collapsed, depth + 1))
        }
    }
    return list
}
