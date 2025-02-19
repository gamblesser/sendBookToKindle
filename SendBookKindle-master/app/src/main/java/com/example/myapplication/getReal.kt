import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream


import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore

fun getPathFromUri(context: Context, uri: Uri): String? {
    val isKitKatOrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

    if (isKitKatOrAbove && DocumentsContract.isDocumentUri(context, uri)) {
        when {
            isExternalStorageDocument(uri) -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]

                if ("primary".equals(type, ignoreCase = true)) {
                    return "${context.getExternalFilesDir(null)}/${split[1]}"
                }
                // handle non-primary volumes
            }
            isDownloadsDocument(uri) -> {
                val id = DocumentsContract.getDocumentId(uri)
                return if (id.startsWith("msf:")) {
                    // Handle special case where the ID is not a numerical ID
                    val fileName = getFileName(context, uri)
                    val cacheDir = context.cacheDir
                    val file = File(cacheDir, fileName)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val outputStream = FileOutputStream(file)
                    copyStream(inputStream!!, outputStream)
                    file.absolutePath
                } else {
                    val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), id.toLong()
                    )
                    getDataColumn(context, contentUri, null, null)
                }
            }
            isMediaDocument(uri) -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]

                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])

                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        }
    } else if ("content".equals(uri.scheme, ignoreCase = true)) {
        return getDataColumn(context, uri, null, null)
    } else if ("file".equals(uri.scheme, ignoreCase = true)) {
        return uri.path
    }

    return null
}

private fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
    var cursor: Cursor? = null
    val column = "_data"
    val projection = arrayOf(column)

    try {
        cursor = uri?.let {
            context.contentResolver.query(it, projection, selection, selectionArgs, null)
        }
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndexOrThrow(column)
            return cursor.getString(index)
        }
    } finally {
        cursor?.close()
    }
    return null
}

private fun getFileName(context: Context, uri: Uri): String {
    var fileName = "unknown"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    if (cursor != null && cursor.moveToFirst()) {
        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        if (nameIndex >= 0) {
            fileName = cursor.getString(nameIndex)
        }
        cursor.close()
    }
    return fileName
}

private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
    val buffer = ByteArray(1024)
    var bytesRead: Int
    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
    }
    inputStream.close()
    outputStream.close()
}

private fun isExternalStorageDocument(uri: Uri): Boolean {
    return "com.android.externalstorage.documents" == uri.authority
}

private fun isDownloadsDocument(uri: Uri): Boolean {
    return "com.android.providers.downloads.documents" == uri.authority
}

private fun isMediaDocument(uri: Uri): Boolean {
    return "com.android.providers.media.documents" == uri.authority
}