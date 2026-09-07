package com.example.data.workspace

import com.example.data.model.WorkspaceFile
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

class WorkspaceManager {
    private val filesLock = Any()
    val files: MutableMap<String, WorkspaceFile> = mutableMapOf()
    val openTabs: MutableList<String> = mutableListOf()
    var activePath: String? = null
    val dirtyPaths: MutableSet<String> = mutableSetOf()
    val collapsedFolders: MutableSet<String> = mutableSetOf()

    init {
        seedWorkspace()
    }

    fun seedWorkspace() {
        synchronized(filesLock) {
            files.clear()
            openTabs.clear()
            dirtyPaths.clear()
            val now = System.currentTimeMillis()

            files["index.html"] = WorkspaceFile(
                type = "file",
                mtime = now,
                content = """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>مشروعي</title>
  <link rel="stylesheet" href="styles.css">
</head>
<body>
  <header class="hero">
    <h1>مرحباً بك في AiWay</h1>
    <p>اطلب من الوكيل تعديل هذه الصفحة أو إضافة ميزات جديدة.</p>
    <button id="counter">النقرات: 0</button>
  </header>
  <script src="app.js"></script>
</body>
</html>
"""
            )

            files["styles.css"] = WorkspaceFile(
                type = "file",
                mtime = now,
                content = """:root {
  --primary: #1a73e8;
  --text: #0d2140;
}

body {
  margin: 0;
  font-family: system-ui, sans-serif;
  color: var(--text);
  background: #f5f8fc;
}

.hero {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 24px;
}

.hero h1 {
  font-size: 2rem;
  margin-bottom: 8px;
}

#counter {
  margin-top: 16px;
  padding: 12px 24px;
  border: 0;
  border-radius: 12px;
  background: var(--primary);
  color: #fff;
  font-size: 1rem;
  cursor: pointer;
}
"""
            )

            files["app.js"] = WorkspaceFile(
                type = "file",
                mtime = now,
                content = """// نقطة البداية للتطبيق
const button = document.getElementById('counter');
let clicks = 0;

function render() {
  button.textContent = 'النقرات: ' + clicks;
}

button.addEventListener('click', () => {
  clicks += 1;
  render();
});

render();
"""
            )

            openTabs.add("index.html")
            activePath = "index.html"
        }
    }

    fun normalizePath(p: String?): String? {
        if (p == null) return null
        var path = p.trim().replace('\\', '/')
            .replace(Regex("^(\\./)+"), "")
            .replace(Regex("^/+"), "")
            .replace(Regex("/{2,}"), "/")
            .replace(Regex("/+$"), "")
        if (path.isEmpty()) return null
        val segs = path.split('/')
        if (segs.any { it.isEmpty() || it == "." || it == ".." }) return null
        if (Regex("[<>:\"|?*\\x00-\\x1f]").containsMatchIn(path)) return null
        if (path.length > 400) return null
        return path
    }

    fun assertPath(p: String?): String {
        val n = normalizePath(p) ?: throw IllegalArgumentException("Invalid path: \"$p\"")
        return n
    }

    fun ensureParents(p: String) {
        val parts = p.split('/')
        for (i in 1 until parts.size) {
            val dir = parts.subList(0, i).joinToString("/")
            val f = files[dir]
            if (f == null) {
                files[dir] = WorkspaceFile(type = "folder")
            } else if (f.type != "folder") {
                throw IllegalStateException("\"$dir\" is a file, not a folder")
            }
        }
    }

    fun getFile(path: String): Pair<String, WorkspaceFile> {
        val p = assertPath(path)
        val f = files[p] ?: throw NoSuchElementException("File not found: \"$p\"")
        if (f.type != "file") throw IllegalStateException("\"$p\" is a folder, not a file")
        return Pair(p, f)
    }

    fun createFile(path: String, content: String = ""): String {
        synchronized(filesLock) {
            val p = assertPath(path)
            if (files.containsKey(p)) {
                val isFile = files[p]?.type == "file"
                throw IllegalStateException("\"$p\" already exists${if (isFile) " — edit the existing file instead of creating it" else ""}")
            }
            ensureParents(p)
            files[p] = WorkspaceFile(type = "file", content = content, mtime = System.currentTimeMillis())
            return p
        }
    }

    fun createFolder(path: String): String {
        synchronized(filesLock) {
            val p = assertPath(path)
            val existing = files[p]
            if (existing != null) {
                if (existing.type == "folder") return p
                throw IllegalStateException("\"$p\" already exists as a file")
            }
            ensureParents(p)
            files[p] = WorkspaceFile(type = "folder", mtime = System.currentTimeMillis())
            return p
        }
    }

    fun writeFile(path: String, content: String) {
        synchronized(filesLock) {
            val (p, _) = getFile(path)
            files[p] = WorkspaceFile(type = "file", content = content, mtime = System.currentTimeMillis())
        }
    }

    fun deletePath(path: String): List<String> {
        synchronized(filesLock) {
            val p = assertPath(path)
            if (!files.containsKey(p)) throw NoSuchElementException("Path not found: \"$p\"")
            val removed = mutableListOf<String>()
            val keys = files.keys.toList()
            for (k in keys) {
                if (k == p || k.startsWith("$p/")) {
                    files.remove(k)
                    removed.add(k)
                }
            }
            removed.forEach {
                openTabs.remove(it)
                dirtyPaths.remove(it)
            }
            if (activePath != null && !files.containsKey(activePath)) {
                activePath = openTabs.firstOrNull { files[it]?.type == "file" }
            }
            return removed
        }
    }

    fun renamePath(from: String, to: String): String {
        synchronized(filesLock) {
            val a = assertPath(from)
            val b = assertPath(to)
            if (!files.containsKey(a)) throw NoSuchElementException("Path not found: \"$a\"")
            if (a == b) return b
            if (files.containsKey(b)) throw IllegalStateException("Target already exists: \"$b\"")
            if (b.startsWith("$a/")) throw IllegalArgumentException("Cannot move a folder into itself")
            ensureParents(b)

            val moved = mutableMapOf<String, String>()
            val keys = files.keys.toList()
            for (k in keys) {
                if (k == a || k.startsWith("$a/")) {
                    val nk = b + k.substring(a.length)
                    files[nk] = files[k]!!
                    files.remove(k)
                    moved[k] = nk
                }
            }
            for (i in openTabs.indices) {
                val old = openTabs[i]
                if (moved.containsKey(old)) {
                    openTabs[i] = moved[old]!!
                }
            }
            if (activePath != null && moved.containsKey(activePath)) {
                activePath = moved[activePath]
            }
            val newDirty = mutableSetOf<String>()
            dirtyPaths.forEach { d ->
                newDirty.add(moved[d] ?: d)
            }
            dirtyPaths.clear()
            dirtyPaths.addAll(newDirty)
            return b
        }
    }

    fun sortedFiles(): List<Map.Entry<String, WorkspaceFile>> {
        synchronized(filesLock) {
            return files.entries.sortedBy { it.key }
        }
    }

    fun listEntries(dir: String?): List<Map<String, Any>> {
        synchronized(filesLock) {
            val d = if (!dir.isNullOrBlank()) assertPath(dir) else ""
            if (d.isNotEmpty() && files[d]?.type != "folder") {
                throw NoSuchElementException("Folder not found: \"$d\"")
            }
            return sortedFiles().filter { (p, _) ->
                d.isEmpty() || p.startsWith("$d/")
            }.map { (p, f) ->
                if (f.type == "file") {
                    mapOf(
                        "path" to p,
                        "type" to "file",
                        "lines" to lineCount(f.content),
                        "chars" to f.content.length
                    )
                } else {
                    mapOf("path" to p, "type" to "folder")
                }
            }
        }
    }

    fun workspaceSummary(): String {
        synchronized(filesLock) {
            val entries = sortedFiles().filter { it.value.type == "file" }
            val folders = sortedFiles().filter { it.value.type == "folder" }.map { it.key }
            val lines = entries.take(200).map { (p, f) ->
                "- $p (${lineCount(f.content)} lines, ${f.content.length} chars)"
            }
            return "WORKSPACE METADATA (use search_workspace / read_lines to inspect contents; do not assume file contents):\n" +
                    "Files: ${entries.size}${if (entries.size > 200) " (first 200 listed)" else ""}\n" +
                    (if (lines.isNotEmpty()) lines.joinToString("\n") else "(workspace is empty)") +
                    (if (folders.isNotEmpty()) "\nFolders: ${folders.joinToString(", ")}" else "") +
                    "\nActive editor file: ${activePath ?: "none"}"
        }
    }

    fun buildPreviewDoc(path: String): String {
        synchronized(filesLock) {
            val (_, file) = getFile(path)
            val dir = if (path.contains('/')) path.substringBeforeLast('/') else ""
            var html = file.content

            fun resolveRelative(baseDir: String, rel: String): String {
                val clean = rel.split(Regex("[?#]"))[0]
                val parts = if (baseDir.isNotEmpty()) baseDir.split('/').toMutableList() else mutableListOf()
                for (seg in clean.split('/')) {
                    if (seg == "..") {
                        if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                    } else if (seg.isNotEmpty() && seg != ".") {
                        parts.add(seg)
                    }
                }
                return parts.joinToString("/")
            }

            fun isExternal(u: String): Boolean {
                return Regex("^(?:[a-z][a-z0-9+.-]*:|//|#)", RegexOption.IGNORE_CASE).containsMatchIn(u.trim())
            }

            // Replace <link rel="stylesheet" href="...">
            val linkRegex = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
            html = linkRegex.replace(html) { matchResult ->
                val tag = matchResult.value
                if (!Regex("rel\\s*=\\s*[\"']?stylesheet", RegexOption.IGNORE_CASE).containsMatchIn(tag)) {
                    return@replace tag
                }
                val hrefMatch = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(tag)
                val href = hrefMatch?.groupValues?.get(1)
                if (href == null || isExternal(href)) return@replace tag
                val p = resolveRelative(dir, href)
                val f = files[p]
                if (f == null || f.type != "file") {
                    "<!-- AiWay: missing local stylesheet $p -->"
                } else {
                    "<style data-aiway-src=\"$p\">\n${f.content.replace("</style", "<\\/style", ignoreCase = true)}\n</style>"
                }
            }

            // Replace <script src="...">
            val scriptRegex = Regex("<script\\b([^>]*?)\\s*src\\s*=\\s*[\"']([^\"']+)[\"']([^>]*)>\\s*</script\\s*>", RegexOption.IGNORE_CASE)
            html = scriptRegex.replace(html) { matchResult ->
                val pre = matchResult.groupValues[1]
                val src = matchResult.groupValues[2]
                val post = matchResult.groupValues[3]
                if (isExternal(src)) return@replace matchResult.value
                val p = resolveRelative(dir, src)
                val f = files[p]
                if (f == null || f.type != "file") {
                    "<!-- AiWay: missing local script $p -->"
                } else {
                    val attrs = ("$pre $post").replace(Regex("\\s+"), " ").trim()
                    "<script${if (attrs.isNotEmpty()) " $attrs" else ""} data-aiway-src=\"$p\">\n${f.content.replace("</script", "<\\/script", ignoreCase = true)}\n</script>"
                }
            }

            val inject = "<style data-aiway-base>html,body{max-width:100%;overflow-x:hidden}img,video,iframe,canvas{max-width:100%}</style>"
            val headRegex = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
            val headMatch = headRegex.find(html)
            html = if (headMatch != null) {
                html.substring(0, headMatch.range.last + 1) + inject + html.substring(headMatch.range.last + 1)
            } else {
                inject + html
            }
            return html
        }
    }

    companion object {
        fun lineCount(s: String?): Int = if (s.isNullOrEmpty()) 0 else s.split('\n').size
        fun extOf(p: String?): String {
            val b = p?.split('/')?.lastOrNull() ?: return ""
            val idx = b.lastIndexOf('.')
            return if (idx > 0) b.substring(idx + 1).lowercase() else ""
        }
    }
}
