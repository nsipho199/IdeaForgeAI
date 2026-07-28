package com.ideaforge.ai.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    fun getAppStorageDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "IdeaForgeAI")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getApksDir(context: Context): File {
        val dir = File(getAppStorageDir(context), "apks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getProjectsDir(context: Context): File {
        val dir = File(getAppStorageDir(context), "projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getLogsDir(context: Context): File {
        val dir = File(getAppStorageDir(context), "logs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveToFile(context: Context, dir: File, fileName: String, data: ByteArray): File {
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(data) }
        return file
    }

    fun deleteFile(file: File): Boolean {
        return if (file.exists()) file.delete() else false
    }

    fun getDirectorySize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return size
    }

    fun clearDirectory(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) clearDirectory(file)
            file.delete()
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String = "application/octet-stream") {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }
}
