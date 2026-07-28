package com.ideaforge.ai.core.build

import android.content.Context
import android.util.Log
import java.io.File

private const val TAG = "SnapshotManager"
private const val SNAPSHOT_DIR = "build_snapshots"

data class BuildSnapshot(
    val id: String,
    val buildAttempt: Int,
    val timestamp: Long,
    val files: Map<String, String>,
    val errorLogs: String
)

class SnapshotManager(private val context: Context) {

    private val snapshotDir: File
        get() = File(context.filesDir, SNAPSHOT_DIR).also { it.mkdirs() }

    private var currentSnapshot: BuildSnapshot? = null

    fun createSnapshot(
        buildAttempt: Int,
        files: Map<String, String>,
        errorLogs: String
    ): BuildSnapshot {
        val snapshot = BuildSnapshot(
            id = "snap_${buildAttempt}_${System.currentTimeMillis()}",
            buildAttempt = buildAttempt,
            timestamp = System.currentTimeMillis(),
            files = files.toMap(),
            errorLogs = errorLogs
        )
        currentSnapshot = snapshot
        persistSnapshot(snapshot)
        Log.i(TAG, "Snapshot created: attempt=$buildAttempt, ${files.size} files, ${errorLogs.length} chars error log")
        return snapshot
    }

    fun getLatestSnapshot(): BuildSnapshot? = currentSnapshot

    fun rollbackTo(snapshot: BuildSnapshot): Map<String, String> {
        Log.i(TAG, "Rolling back to snapshot attempt=${snapshot.buildAttempt} from ${snapshot.timestamp}")
        currentSnapshot = snapshot
        return snapshot.files.toMap()
    }

    fun rollbackToLatest(): Map<String, String>? {
        val snap = currentSnapshot ?: return null
        return rollbackTo(snap)
    }

    fun clearSnapshots() {
        snapshotDir.listFiles()?.forEach { it.delete() }
        currentSnapshot = null
        Log.d(TAG, "All snapshots cleared")
    }

    fun getSnapshotHistory(): List<BuildSnapshot> {
        return snapshotDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { loadSnapshot(it) }
            ?: emptyList()
    }

    private fun persistSnapshot(snapshot: BuildSnapshot) {
        try {
            val file = File(snapshotDir, "${snapshot.id}.json")
            val sb = StringBuilder()
            sb.appendLine("{")
            sb.appendLine("  \"id\": \"${snapshot.id}\",")
            sb.appendLine("  \"buildAttempt\": ${snapshot.buildAttempt},")
            sb.appendLine("  \"timestamp\": ${snapshot.timestamp},")
            sb.appendLine("  \"errorLogs\": ${jsonEscape(snapshot.errorLogs)},")
            sb.appendLine("  \"files\": {")
            snapshot.files.entries.forEachIndexed { idx, (path, content) ->
                sb.append("    \"${jsonEscape(path)}\": ${jsonEscape(content)}")
                if (idx < snapshot.files.size - 1) sb.appendLine(",") else sb.appendLine()
            }
            sb.appendLine("  }")
            sb.appendLine("}")
            file.writeText(sb.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist snapshot: ${e.message}")
        }
    }

    private fun loadSnapshot(file: File): BuildSnapshot? {
        return try {
            val text = file.readText()
            val id = extractJsonString(text, "id") ?: return null
            val attempt = extractJsonInt(text, "buildAttempt") ?: return null
            val timestamp = extractJsonLong(text, "timestamp") ?: return null
            val errorLogs = extractJsonString(text, "errorLogs") ?: ""
            val files = extractJsonMap(text, "files")
            BuildSnapshot(id, attempt, timestamp, files, errorLogs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load snapshot ${file.name}: ${e.message}")
            null
        }
    }

    private fun jsonEscape(s: String): String {
        return "\"" + s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }

    private fun extractJsonString(text: String, key: String): String? {
        val regex = Regex("""\s*"${key}":\s*"(.*?)"(?=[,\n}])""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(text) ?: return null
        val raw = match.groupValues[1]
        return raw.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun extractJsonInt(text: String, key: String): Int? {
        val regex = Regex("""\s*"${key}":\s*(\d+)""")
        return regex.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractJsonLong(text: String, key: String): Long? {
        val regex = Regex("""\s*"${key}":\s*(\d+)""")
        return regex.find(text)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractJsonMap(text: String, key: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = Regex(""""\s*"${key}":\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(text) ?: return result
        val inner = match.groupValues[1]
        val entryRegex = Regex(""""([^"]+)":\s*"((?:[^"\\]|\\.)*)"""")
        entryRegex.findAll(inner).forEach { m ->
            val k = m.groupValues[1]
            val v = m.groupValues[2].replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
            result[k] = v
        }
        return result
    }
}
