package com.example.data.workspace

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

class AgentToolExecutor(private val workspace: WorkspaceManager) {

    fun execute(name: String, args: JSONObject): JSONObject {
        val result = JSONObject()
        try {
            when (name) {
                "list_files" -> {
                    val dir = args.optString("directory", "").ifEmpty { null }
                    val entries = workspace.listEntries(dir)
                    val arr = JSONArray()
                    entries.forEach { entry ->
                        val obj = JSONObject()
                        entry.forEach { (k, v) -> obj.put(k, v) }
                        arr.put(obj)
                    }
                    result.put("ok", true)
                    result.put("count", entries.size)
                    result.put("entries", arr)
                }
                "search_workspace" -> {
                    val query = args.optString("query")
                    if (query.isNullOrEmpty()) throw IllegalArgumentException("query is required")
                    val q = query.lowercase()
                    val prefix = workspace.normalizePath(args.optString("path_prefix", "")) ?: ""
                    val maxResults = args.optInt("max_results", 30).coerceIn(1, 100)

                    val results = JSONArray()
                    var scanned = 0
                    var truncated = false

                    val files = workspace.sortedFiles()
                    searchLoop@ for ((p, f) in files) {
                        if (f.type != "file") continue
                        if (prefix.isNotEmpty() && !p.startsWith(prefix)) continue
                        scanned++
                        val lines = f.content.split('\n')
                        for (i in lines.indices) {
                            if (lines[i].lowercase().contains(q)) {
                                val item = JSONObject()
                                item.put("path", p)
                                item.put("line", i + 1)
                                item.put("preview", lines[i].trim().take(160))
                                results.put(item)
                                if (results.length() >= maxResults) {
                                    truncated = true
                                    break@searchLoop
                                }
                            }
                        }
                    }

                    result.put("ok", true)
                    result.put("query", query)
                    result.put("files_scanned", scanned)
                    result.put("count", results.length())
                    result.put("truncated", truncated)
                    result.put("results", results)
                }
                "read_lines" -> {
                    val path = args.optString("path")
                    val (p, file) = workspace.getFile(path)
                    val lines = file.content.split('\n')
                    val total = lines.size
                    val startArg = args.optInt("start", 1).coerceIn(1, total)
                    var endArg = args.optInt("end", startArg).coerceIn(startArg, total)
                    var capped = false
                    val cap = 400
                    if (endArg - startArg + 1 > cap) {
                        endArg = startArg + cap - 1
                        capped = true
                    }
                    val contentLines = lines.subList(startArg - 1, endArg).mapIndexed { i, line ->
                        "${startArg + i}: $line"
                    }.joinToString("\n")

                    result.put("ok", true)
                    result.put("path", p)
                    result.put("start", startArg)
                    result.put("end", endArg)
                    result.put("total_lines", total)
                    result.put("content", contentLines)
                    if (capped) {
                        result.put("note", "Range capped at $cap lines; request the rest separately if needed.")
                    }
                }
                "find_symbol" -> {
                    val symbol = args.optString("symbol")
                    if (symbol.isNullOrEmpty()) throw IllegalArgumentException("symbol is required")
                    val prefix = workspace.normalizePath(args.optString("path_prefix", "")) ?: ""
                    val isIdent = Regex("^[\\w$-]+$").matches(symbol)
                    val pattern = if (isIdent) "(?<![\\w$])${Regex.escape(symbol)}(?![\\w$])" else Regex.escape(symbol)
                    val regex = Regex(pattern, RegexOption.IGNORE_CASE)

                    val matches = JSONArray()
                    val files = workspace.sortedFiles()
                    symbolLoop@ for ((p, f) in files) {
                        if (f.type != "file") continue
                        if (prefix.isNotEmpty() && !p.startsWith(prefix)) continue
                        val lines = f.content.split('\n')
                        for (i in lines.indices) {
                            if (regex.containsMatchIn(lines[i])) {
                                val a = max(0, i - 1)
                                val b = min(lines.size - 1, i + 1)
                                val ctx = lines.subList(a, b + 1).mapIndexed { k, l ->
                                    "${a + k + 1}: $l"
                                }.joinToString("\n")
                                val matchObj = JSONObject()
                                matchObj.put("path", p)
                                matchObj.put("line", i + 1)
                                matchObj.put("context", ctx)
                                matches.put(matchObj)
                                if (matches.length() >= 40) break@symbolLoop
                            }
                        }
                    }

                    result.put("ok", true)
                    result.put("symbol", symbol)
                    result.put("count", matches.length())
                    result.put("matches", matches)
                    if (matches.length() >= 40) {
                        result.put("note", "Result list capped at 40 matches")
                    }
                }
                "create_file" -> {
                    val path = args.optString("path")
                    val content = args.optString("content", "")
                    val p = workspace.createFile(path, content)
                    result.put("ok", true)
                    result.put("path", p)
                    result.put("lines", WorkspaceManager.lineCount(content))
                }
                "create_folder" -> {
                    val path = args.optString("path")
                    val p = workspace.createFolder(path)
                    result.put("ok", true)
                    result.put("path", p)
                }
                "edit_lines" -> {
                    val path = args.optString("path")
                    val (p, file) = workspace.getFile(path)
                    val lines = file.content.split('\n').toMutableList()
                    val total = lines.size
                    val s = args.optInt("start")
                    val e = args.optInt("end")
                    if (!(s >= 1 && e >= s && e <= total)) {
                        throw IllegalArgumentException("Invalid range $s-$e; file has $total lines. Re-read the file region first.")
                    }
                    val replacement = args.optString("replacement", "")
                    val repList = if (replacement.isEmpty()) emptyList() else replacement.trimEnd('\n').split('\n')
                    // remove elements from s-1 to e-1
                    for (k in e - 1 downTo s - 1) {
                        lines.removeAt(k)
                    }
                    lines.addAll(s - 1, repList)
                    val nextContent = lines.joinToString("\n")
                    workspace.writeFile(p, nextContent)

                    val replacedObj = JSONObject().put("start", s).put("end", e)
                    val newRangeObj = JSONObject().put("start", s).put("end", max(s - 1, s + repList.size - 1))
                    result.put("ok", true)
                    result.put("path", p)
                    result.put("replaced", replacedObj)
                    result.put("new_range", newRangeObj)
                    result.put("total_lines", lines.size)
                    result.put("line_shift", repList.size - (e - s + 1))
                }
                "replace_exact" -> {
                    val path = args.optString("path")
                    val (p, file) = workspace.getFile(path)
                    val oldText = args.optString("old_text")
                    if (oldText.isEmpty()) throw IllegalArgumentException("old_text must be a non-empty string")
                    val newText = args.optString("new_text", "")

                    val count = countOccurrences(file.content, oldText)
                    if (count == 0) {
                        throw IllegalStateException("old_text not found (0 matches). Re-read the lines with read_lines and copy the text exactly, including whitespace and indentation.")
                    }
                    if (count > 1) {
                        throw IllegalStateException("old_text matched $count times; include more surrounding context so it is unique.")
                    }

                    val idx = file.content.indexOf(oldText)
                    val startLine = WorkspaceManager.lineCount(file.content.substring(0, idx)).coerceAtLeast(1)
                    val next = file.content.substring(0, idx) + newText + file.content.substring(idx + oldText.length)
                    workspace.writeFile(p, next)

                    val changedRange = JSONObject()
                        .put("start", startLine)
                        .put("end", max(startLine, startLine + WorkspaceManager.lineCount(newText) - 1))
                    result.put("ok", true)
                    result.put("path", p)
                    result.put("changed_range", changedRange)
                    result.put("total_lines", WorkspaceManager.lineCount(next))
                }
                "apply_patch" -> {
                    val path = args.optString("path")
                    val (p, file) = workspace.getFile(path)
                    val replacements = args.optJSONArray("replacements")
                    if (replacements == null || replacements.length() == 0) {
                        throw IllegalArgumentException("replacements must be a non-empty array of {old_text,new_text}")
                    }

                    var content = file.content
                    val errors = mutableListOf<String>()
                    val ranges = JSONArray()

                    for (i in 0 until replacements.length()) {
                        val item = replacements.optJSONObject(i)
                        val oldText = item?.optString("old_text") ?: ""
                        if (oldText.isEmpty()) {
                            errors.add("#${i + 1}: old_text missing")
                            continue
                        }
                        val count = countOccurrences(content, oldText)
                        if (count != 1) {
                            errors.add("#${i + 1}: ${if (count == 0) "old_text not found" else "$count matches (must be unique)"}")
                            continue
                        }
                        val idx = content.indexOf(oldText)
                        val startLine = WorkspaceManager.lineCount(content.substring(0, idx)).coerceAtLeast(1)
                        val newText = item?.optString("new_text") ?: ""
                        content = content.substring(0, idx) + newText + content.substring(idx + oldText.length)
                        val rObj = JSONObject()
                            .put("start", startLine)
                            .put("end", max(startLine, startLine + WorkspaceManager.lineCount(newText) - 1))
                        ranges.put(rObj)
                    }

                    if (errors.isNotEmpty()) {
                        throw IllegalStateException("Patch rejected, nothing was applied: " + errors.joinToString("; "))
                    }

                    workspace.writeFile(p, content)
                    result.put("ok", true)
                    result.put("path", p)
                    result.put("applied", replacements.length())
                    result.put("changed_ranges", ranges)
                    result.put("total_lines", WorkspaceManager.lineCount(content))
                }
                "rename_path" -> {
                    val from = args.optString("from")
                    val to = args.optString("to")
                    val b = workspace.renamePath(from, to)
                    result.put("ok", true)
                    result.put("from", workspace.normalizePath(from))
                    result.put("to", b)
                }
                "delete_path" -> {
                    val path = args.optString("path")
                    val removed = workspace.deletePath(path)
                    val arr = JSONArray()
                    removed.forEach { arr.put(it) }
                    result.put("ok", true)
                    result.put("removed", arr)
                }
                "validate_file" -> {
                    val path = args.optString("path")
                    val (p, file) = workspace.getFile(path)
                    val issues = validateContent(p, file.content)
                    val arr = JSONArray()
                    issues.forEach { (line, msg) ->
                        val obj = JSONObject()
                        if (line != null) obj.put("line", line) else obj.put("line", JSONObject.NULL)
                        obj.put("message", msg)
                        arr.put(obj)
                    }
                    result.put("ok", true)
                    result.put("path", p)
                    result.put("valid", issues.isEmpty())
                    result.put("issues", arr)
                }
                "review_changed" -> {
                    val path = args.optString("path")
                    val (p, file) = workspace.getFile(path)
                    val lines = file.content.split('\n')
                    val total = lines.size
                    val s = args.optInt("start", 1).coerceIn(1, total)
                    val e = args.optInt("end", s).coerceIn(s, total)
                    val ctx = args.optInt("context", 3).coerceIn(0, 20)
                    val from = max(1, s - ctx)
                    val to = min(total, max(e + ctx, s + ctx))
                    val preview = (from..to).joinToString("\n") { n ->
                        val prefix = if (n in s..e) "$n *| " else "$n  | "
                        prefix + lines[n - 1]
                    }

                    result.put("ok", true)
                    result.put("path", p)
                    result.put("shown", JSONObject().put("start", from).put("end", to))
                    result.put("changed", JSONObject().put("start", s).put("end", e))
                    result.put("total_lines", total)
                    result.put("legend", "* marks modified lines")
                    result.put("content", preview)
                }
                else -> {
                    result.put("ok", false)
                    result.put("error", "Unknown tool: $name")
                }
            }
        } catch (e: Exception) {
            result.put("ok", false)
            result.put("error", e.message ?: e.toString())
        }
        return result
    }

    private fun countOccurrences(hay: String, needle: String): Int {
        var n = 0
        var i = 0
        while (true) {
            val idx = hay.indexOf(needle, i)
            if (idx == -1) break
            n++
            i = idx + needle.length
            if (n > 50) break
        }
        return n
    }

    private fun validateContent(path: String, content: String): List<Pair<Int?, String>> {
        val issues = mutableListOf<Pair<Int?, String>>()
        val ext = WorkspaceManager.extOf(path)
        val lines = content.split('\n')
        lines.forEachIndexed { i, l ->
            if (Regex("^(<{7}|={7}|>{7})(\\s|$)").containsMatchIn(l)) {
                issues.add(Pair(i + 1, "Merge conflict marker"))
            }
        }
        if (ext == "json") {
            try {
                JSONObject(content)
            } catch (e: Exception) {
                try {
                    JSONArray(content)
                } catch (e2: Exception) {
                    issues.add(Pair(null, "Invalid JSON: ${e2.message}"))
                }
            }
            return issues
        }
        return issues
    }

    companion object {
        fun getToolMetadata(tool: String): Pair<String, String> {
            return when (tool) {
                "list_files" -> Pair("استعراض الملفات", "list")
                "search_workspace" -> Pair("البحث في مساحة العمل", "search")
                "read_lines" -> Pair("قراءة الأسطر المطلوبة", "file")
                "find_symbol" -> Pair("تحديد موقع الكود", "code")
                "create_file" -> Pair("إنشاء ملف", "filePlus")
                "create_folder" -> Pair("إنشاء مجلد", "folderPlus")
                "edit_lines" -> Pair("تطبيق تعديل محدد", "edit")
                "replace_exact" -> Pair("تطبيق تعديل محدد", "edit")
                "apply_patch" -> Pair("تطبيق رقعة", "patch")
                "rename_path" -> Pair("إعادة تسمية", "rename")
                "delete_path" -> Pair("حذف", "trash")
                "validate_file" -> Pair("التحقق من الملف", "check")
                "review_changed" -> Pair("مراجعة التغييرات", "eye")
                "web" -> Pair("البحث في الويب", "globe")
                else -> Pair(tool, "zap")
            }
        }

        fun getToolDetail(name: String, args: JSONObject): String {
            return when (name) {
                "list_files" -> args.optString("directory", "/").ifEmpty { "/" }
                "search_workspace" -> "\"${args.optString("query")}\""
                "read_lines", "edit_lines", "review_changed" ->
                    "${args.optString("path")}:${args.opt("start") ?: "?"}-${args.opt("end") ?: "?"}"
                "find_symbol" -> args.optString("symbol")
                "rename_path" -> "${args.optString("from")} → ${args.optString("to")}"
                else -> args.optString("path")
            }
        }

        fun getToolDefinitionsJson(): JSONArray {
            val arr = JSONArray()

            fun fn(name: String, desc: String, props: JSONObject, req: List<String>) {
                val pObj = JSONObject()
                pObj.put("type", "object")
                pObj.put("properties", props)
                val reqArr = JSONArray()
                req.forEach { reqArr.put(it) }
                pObj.put("required", reqArr)

                val fnObj = JSONObject()
                fnObj.put("name", name)
                fnObj.put("description", desc)
                fnObj.put("parameters", pObj)

                val item = JSONObject()
                item.put("type", "function")
                item.put("function", fnObj)
                arr.put(item)
            }

            // list_files
            val p1 = JSONObject().put("directory", JSONObject().put("type", "string").put("description", "Folder path. Omit for the whole workspace."))
            fn("list_files", "List workspace entries with path, type, line count and character count. Optionally restrict to one directory.", p1, emptyList())

            // search_workspace
            val p2 = JSONObject()
                .put("query", JSONObject().put("type", "string"))
                .put("path_prefix", JSONObject().put("type", "string").put("description", "Only search paths starting with this prefix"))
                .put("max_results", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 100))
            fn("search_workspace", "Case-insensitive text search across all workspace files. Returns file path, line number and a short preview per match. Use this BEFORE reading files.", p2, listOf("query"))

            // read_lines
            val p3 = JSONObject()
                .put("path", JSONObject().put("type", "string"))
                .put("start", JSONObject().put("type", "integer"))
                .put("end", JSONObject().put("type", "integer"))
            fn("read_lines", "Read a 1-based inclusive line range of a file, returned with line numbers. Read the smallest useful range (max 400 lines per call).", p3, listOf("path", "start", "end"))

            // find_symbol
            val p4 = JSONObject()
                .put("symbol", JSONObject().put("type", "string"))
                .put("path_prefix", JSONObject().put("type", "string"))
            fn("find_symbol", "Locate a function, class, CSS selector, variable or any identifier/text and return small contextual matches with line numbers.", p4, listOf("symbol"))

            // create_file
            val p5 = JSONObject()
                .put("path", JSONObject().put("type", "string"))
                .put("content", JSONObject().put("type", "string"))
            fn("create_file", "Create a NEW file with the given content. Fails if the path already exists — edit existing files instead. Missing parent folders are created.", p5, listOf("path", "content"))

            // create_folder
            val p6 = JSONObject().put("path", JSONObject().put("type", "string"))
            fn("create_folder", "Create a folder (and any missing parents).", p6, listOf("path"))

            // edit_lines
            val p7 = JSONObject()
                .put("path", JSONObject().put("type", "string"))
                .put("start", JSONObject().put("type", "integer"))
                .put("end", JSONObject().put("type", "integer"))
                .put("replacement", JSONObject().put("type", "string"))
            fn("edit_lines", "Replace an exact 1-based inclusive line range with replacement text (may contain more or fewer lines; an empty string deletes the range). Line numbers after the range shift.", p7, listOf("path", "start", "end", "replacement"))

            // replace_exact
            val p8 = JSONObject()
                .put("path", JSONObject().put("type", "string"))
                .put("old_text", JSONObject().put("type", "string"))
                .put("new_text", JSONObject().put("type", "string"))
            fn("replace_exact", "Replace old_text with new_text. old_text must occur EXACTLY once in the file (0 or multiple matches return an error). Include enough context to be unique.", p8, listOf("path", "old_text", "new_text"))

            // apply_patch
            val patchItem = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("old_text", JSONObject().put("type", "string")).put("new_text", JSONObject().put("type", "string")))
                .put("required", JSONArray().put("old_text").put("new_text"))
            val p9 = JSONObject()
                .put("path", JSONObject().put("type", "string"))
                .put("replacements", JSONObject().put("type", "array").put("items", patchItem))
            fn("apply_patch", "Apply several unique exact replacements to one file atomically without rewriting untouched content. Each old_text must occur exactly once; if any fails nothing is applied.", p9, listOf("path", "replacements"))

            // rename_path
            val p10 = JSONObject()
                .put("from", JSONObject().put("type", "string"))
                .put("to", JSONObject().put("type", "string"))
            fn("rename_path", "Rename or move a file or folder.", p10, listOf("from", "to"))

            // delete_path
            val p11 = JSONObject().put("path", JSONObject().put("type", "string"))
            fn("delete_path", "Delete a file, or a folder recursively.", p11, listOf("path"))

            // validate_file
            val p12 = JSONObject().put("path", JSONObject().put("type", "string"))
            fn("validate_file", "Lightweight structural validation: balanced {} [] (), JSON parsing for .json, merge-conflict markers. Returns a list of issues.", p12, listOf("path"))

            // review_changed
            val p13 = JSONObject()
                .put("path", JSONObject().put("type", "string"))
                .put("start", JSONObject().put("type", "integer"))
                .put("end", JSONObject().put("type", "integer"))
                .put("context", JSONObject().put("type", "integer").put("description", "Context lines, default 3"))
            fn("review_changed", "Return a modified line range plus a few lines of context (with line numbers) to verify an edit.", p13, listOf("path", "start", "end"))

            return arr
        }
    }
}
