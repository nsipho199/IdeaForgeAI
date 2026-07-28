package com.ideaforge.ai.core.extensions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun Context.copyToClipboard(text: String, label: String = "Copied Text") {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(Intent.createChooser(intent, "Share via"))
}

fun Context.shareFile(file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        this,
        "${packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(Intent.createChooser(intent, "Share APK"))
}

fun Context.installApk(file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        this,
        "${packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

fun Context.getDownloadDirectory(): File {
    val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "IdeaForgeAI")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun Long.formatFileSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var bytes = this.toDouble()
    var unitIndex = 0
    while (bytes >= 1024 && unitIndex < units.size - 1) {
        bytes /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", bytes, units[unitIndex])
}

fun Long.formatDuration(): String {
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return when {
        hours > 0 -> String.format(Locale.US, "%dh %dm %ds", hours, minutes, seconds)
        minutes > 0 -> String.format(Locale.US, "%dm %ds", minutes, seconds)
        else -> String.format(Locale.US, "%ds", seconds)
    }
}

fun Long.formatTimestamp(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

fun String.isValidIdea(): Boolean = length in com.ideaforge.ai.core.constants.AppConstants.MIN_IDE_LENGTH..com.ideaforge.ai.core.constants.AppConstants.MAX_IDE_LENGTH
