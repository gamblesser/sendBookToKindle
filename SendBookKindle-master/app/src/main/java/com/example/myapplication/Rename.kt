package com.example.myapplication

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption



public fun getFileNameFromPath(path: String): String {
    return path.substringAfterLast("/")
}
public fun getPathWithoutFileName(path: String): String {
    return path.substringBeforeLast("/")
}
public fun getFileExtension(path: String): String {
    val extension = path.substringAfterLast('.', "")
    return if (extension.isNotEmpty()) ".$extension" else ""
}
@RequiresApi(Build.VERSION_CODES.O)
public fun renameFile(oldPath: String, newPath: String): String? {
    val oldFilePath: Path = Paths.get(oldPath)
    val newFilePath: Path = Paths.get(newPath)

    if (!Files.exists(oldFilePath)) {
        Log.e("renameFile", "File not found at $oldPath")
        return null
    }

    return try {
        Files.move(oldFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING)
        newFilePath.toString()
    } catch (e: Exception) {
        Log.e("renameFile", "Error renaming file: ${e.message}")
        null
    }
}