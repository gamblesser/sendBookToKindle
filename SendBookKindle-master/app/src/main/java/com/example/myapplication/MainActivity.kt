package com.example.myapplication

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.atwa.filepicker.core.FilePicker
import com.google.gson.Gson
import getBookDetailsByISBN
import getPathFromUri


import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.Long
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

data class UserData(val field1: String, val field2: String,val field3: String,val convert:Boolean,val rename:Boolean  )

class MainActivity : AppCompatActivity() {
    private val apiKey = "your google api key"
    private val filePicker = FilePicker.getInstance(this)
    private lateinit var selectFileButton: Button
    private fun saveData(field1: String, field2: String,field3: String,convert:Boolean,rename: Boolean) {
        val userData = UserData(field1, field2, field3,convert,rename )
        val gson = Gson()
        val jsonString = gson.toJson(userData)
        val file = File(filesDir, "user_data.json")
        file.writeText(jsonString)
    }
    private fun loadData(): UserData? {
        val file = File(filesDir, "user_data.json")
        if (file.exists()) {
            val jsonString = file.readText()
            val gson = Gson()
            val userData = gson.fromJson(jsonString, UserData::class.java)
            return userData

        }
        return null
    }

    fun extractBeforeYandex(input: String): String {
        val delimiter = "@yandex.ru"
        val index = input.indexOf(delimiter)
        return if (index != -1) {
            input.substring(0, index)
        } else {
            ""
        }
    }

    fun getRealPathFromUri(context: Context, uri: Uri): String? {
        Log.d("getRealPathFromUri", "URI: $uri, Scheme: ${uri.scheme}")

        return when {
            DocumentsContract.isDocumentUri(context, uri) -> {
                when {
                    isExternalStorageDocument(uri) -> {
                        val docId = DocumentsContract.getDocumentId(uri)
                        val split = docId.split(":").toTypedArray()
                        val type = split[0]
                        if ("primary".equals(type, ignoreCase = true)) {
                            "${Environment.getExternalStorageDirectory()}/${split[1]}"
                        } else {
                            "/storage/$type/${split[1]}"
                        }
                    }
                    isDownloadsDocument(uri) -> {
                        val id = DocumentsContract.getDocumentId(uri)
                        if (id.startsWith("raw:")) {
                            id.substring(4)
                        } else {
                            val contentUri = ContentUris.withAppendedId(
                                Uri.parse("content://downloads/public_downloads"), id.toLongOrNull() ?: return null
                            )
                            getDataColumn(context, contentUri, null, null)
                        }
                    }
                    isMediaDocument(uri) -> {
                        val docId = DocumentsContract.getDocumentId(uri)
                        val split = docId.split(":").toTypedArray()
                        val type = split[0]
                        val contentUri: Uri? = when (type) {
                            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            else -> null
                        }
                        val selection = "_id=?"
                        val selectionArgs = arrayOf(split[1])
                        getDataColumn(context, contentUri, selection, selectionArgs)
                    }
                    else -> null
                }
            }
            "content".equals(uri.scheme, ignoreCase = true) -> {
                getDataColumn(context, uri, null, null) ?: saveFileToCache(context, uri)
            }
            "file".equals(uri.scheme, ignoreCase = true) -> {
                uri.path
            }
            else -> null
        }
    }

    private fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            cursor = uri?.let { context.contentResolver.query(it, projection, selection, selectionArgs, null) }
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            Log.e("getDataColumn", "Error getting data column: ${e.message}")
        } finally {
            cursor?.close()
        }
        return null
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

    private fun saveFileToCache(context: Context, uri: Uri): String? {
        val fileName = getFileName(context, uri)
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            Log.e("saveFileToCache", "Error saving file to cache: ${e.message}")
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        return result ?: "temp_file"

    }



    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val button = findViewById<Button>(R.id.filepick)
        val saving = findViewById<Button>(R.id.saving)
        val convert = findViewById<Switch>(R.id.convert)
        val textFrom = findViewById<EditText>(R.id.emailFrom)
        val textTo = findViewById<EditText>(R.id.emailTo)
        val passwording = findViewById<EditText>(R.id.password)
        val rename = findViewById<Switch>(R.id.rename)
        if (File(filesDir, "user_data.json").totalSpace == 0L ) {
            saveData("","","",convert.isChecked,rename.isChecked)
            }
        val database = loadData()
        convert.isChecked = database?.convert.toString().toBoolean()
        rename.isChecked = database?.rename.toString().toBoolean()




        if (intent?.action == Intent.ACTION_SEND_MULTIPLE && intent.type != null) {
            handleSendMultipleFiles(intent)
        }else {
            handleIncomingShare(intent)

        }



        rename.setOnClickListener {
            val database = loadData()
            saveData(database?.field1.toString(),database?.field2.toString(),database?.field3.toString(),database?.convert.toString().toBoolean(),!database?.rename.toString().toBoolean())

        }
        convert.setOnClickListener {
            val database = loadData()
            saveData(database?.field1.toString(),database?.field2.toString(),database?.field3.toString(),!database?.convert.toString().toBoolean(),database?.rename.toString().toBoolean())
        }
        button.setOnClickListener {

            pickFile()



        }

        saving.setOnClickListener {
            saveData(textFrom.text.toString(),textTo.text.toString(),passwording.text.toString(),database?.convert.toString().toBoolean(),database?.rename.toString().toBoolean())


        }


    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleSendMultipleFiles(intent: Intent) {
        val convert = findViewById<Switch>(R.id.convert)
        val fileUris: ArrayList<Uri>? = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        if (fileUris != null) {
            for (uri: Uri in fileUris) {

                        val database = loadData()
                        val to = database?.field2.toString()//"GexpirienceSoda_1epELY@kindle.com"
                        var subject = "Convert"
                        val bodyText = ""
                        val from = database?.field1.toString()//"GexpirienceSoda@yandex.ru"
                        val host = "smtp.yandex.com"
                        val port = "465"
                        val username = extractBeforeYandex(from)
                        val password = database?.field3.toString()
                        var filePath = "Not found"
                        val timedpath = uri.toString()
                        val boolvar = timedpath.contains(".documents/")
                        var newsName = ""
                if (convert.isChecked() == false){
                    subject = ""
                }
                        if (boolvar) {
                            filePath = getPathFromUri(uri)

                        } else {
                            filePath = com.starry.file_utils.FileUtils(this).getPath(uri).toString()
                        }

                val rename = findViewById<Switch>(R.id.rename)
                var oldFilePath = filePath
                var nameFilePath = getFileNameFromPath(filePath)
                if (rename.isChecked()) {
                    showInputDialog(this, "Choose name for file", "$nameFilePath") { userInput ->
                        val isbn = extractIsbn(userInput)
                        if (isbn != false){
                            getBookDetailsByISBN(apiKey, isbn.toString()) { bookDetails ->
                                newsName = bookDetails.toString()
                                val pathWithout = getPathWithoutFileName(filePath)
                                val formated = getFileExtension(filePath)
                                val newFile = "$pathWithout/$newsName$formated"
                                filePath = renameFile(filePath,newFile).toString()
                                EmailSender.sendEmailWithAttachment(
                                    to,
                                    subject,
                                    bodyText,
                                    filePath,
                                    from,
                                    host,
                                    port,
                                    username,
                                    password,
                                    oldFilePath
                                )
                            }
                        }else
                        {
                            newsName = userInput
                        }
                        val pathWithout = getPathWithoutFileName(filePath)
                        val formated = getFileExtension(filePath)
                        val newFile = "$pathWithout/$newsName$formated"
                        if (newsName == ""){
                            println("nothing")
                        }else {
                            filePath = renameFile(filePath, newFile).toString()
                            EmailSender.sendEmailWithAttachment(
                                to,
                                subject,
                                bodyText,
                                filePath,
                                from,
                                host,
                                port,
                                username,
                                password,
                                oldFilePath
                            )
                        }
                    }

                }else {
                    EmailSender.sendEmailWithAttachment(
                        to,
                        subject,
                        bodyText,
                        filePath,
                        from,
                        host,
                        port,
                        username,
                        password,
                        oldFilePath
                    )
                }

                    }
                }


            }



    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleIncomingShare(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                val convert = findViewById<Switch>(R.id.convert)
                val database = loadData()
                val to = database?.field2.toString()//"GexpirienceSoda_1epELY@kindle.com"
                var subject = "Convert"
                val bodyText = ""
                val from = database?.field1.toString()//"GexpirienceSoda@yandex.ru"
                val host = "smtp.yandex.com"
                val port = "465"
                val username = extractBeforeYandex(from)
                val password = database?.field3.toString()
                var filePath = "Not found"
                val timedpath = uri.toString()
                val boolvar = timedpath.contains(".documents/")
                var newsName = ""
                if (convert.isChecked() == false){
                    subject = ""
                }
                if (boolvar) {
                    filePath =getPathFromUri(uri)
                } else {
                     filePath = com.starry.file_utils.FileUtils(this).getPath(uri).toString()
                }
                val rename = findViewById<Switch>(R.id.rename)
                var oldFilePath = filePath
                var nameFilePath = getFileNameFromPath(filePath)
                if (rename.isChecked()) {
                    showInputDialog(this, "Choose name for file", "$nameFilePath") { userInput ->
                        val isbn = extractIsbn(userInput)

                        if (isbn !=false){
                            getBookDetailsByISBN(apiKey, isbn.toString()) { bookDetails ->
                                newsName = bookDetails.toString()
                                val pathWithout = getPathWithoutFileName(filePath)
                                val formated = getFileExtension(filePath)
                                val newFile = "$pathWithout/$newsName$formated"
                                filePath = renameFile(filePath,newFile).toString()
                                EmailSender.sendEmailWithAttachment(
                                    to,
                                    subject,
                                    bodyText,
                                    filePath,
                                    from,
                                    host,
                                    port,
                                    username,
                                    password,
                                    oldFilePath
                                )
                            }
                        }else
                        {
                            newsName = userInput
                        }
                        val pathWithout = getPathWithoutFileName(filePath)
                        val formated = getFileExtension(filePath)
                        val newFile = "$pathWithout/$newsName$formated"
                        if (newsName == ""){
                            println("nothing")
                        }else {
                            filePath = renameFile(filePath, newFile).toString()
                            EmailSender.sendEmailWithAttachment(
                                to,
                                subject,
                                bodyText,
                                filePath,
                                from,
                                host,
                                port,
                                username,
                                password,
                                oldFilePath
                            )
                        }
                    }

                }else {
                    EmailSender.sendEmailWithAttachment(
                        to,
                        subject,
                        bodyText,
                        filePath,
                        from,
                        host,
                        port,
                        username,
                        password,
                        oldFilePath
                    )
                }
            }
        }
    }
    private fun showInputDialog(context: Context, title: String, message: String, callback: (String) -> Unit) {
        val input = EditText(context)

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val userInput = input.text.toString()
                callback(userInput)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()

        dialog.show()
    }
    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, 1)
    }
    private fun extractIsbn(input: String): Any {
        val prefix = "ISBN_"
        return if (input.startsWith(prefix, ignoreCase = true)) {
            input.substring(prefix.length)
        } else {
            false
        }
    }
    private fun transformPath(inputPath: String): String {
        // Замена "/storage/emulated/" на "/data/user/"
        var transformedPath = inputPath.replaceFirst("/storage/emulated/", "/data/user/")
        // Замена "Download/" на "com.example.myapplication/cache/"
        transformedPath = transformedPath.replaceFirst("Download/", "com.example.myapplication/cache/")
        return transformedPath
    }
    // Обработка результата выбора файлов
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            val fileUris = mutableListOf<Uri>()

            data?.let { intent ->
                if (intent.clipData != null) {
                    val count = intent.clipData!!.itemCount
                    for (i in 0 until count) {
                        val fileUri = intent.clipData!!.getItemAt(i).uri
                        fileUris.add(fileUri)
                    }
                } else if (intent.data != null) {
                    val fileUri = intent.data!!
                    fileUris.add(fileUri)
                }
            }
            val convert = findViewById<Switch>(R.id.convert)
            val filePaths = fileUris.map { uri -> getPathFromUri(this,uri).toString() }
            for (item: String in filePaths) {
                val database = loadData()
                val to = database?.field2.toString()//"GexpirienceSoda_1epELY@kindle.com"
                println(to)
                var subject = "Convert"
                val bodyText = ""
                val from = database?.field1.toString()//"GexpirienceSoda@yandex.ru"
                val host = "smtp.yandex.com"
                val port = "465"
                val username = extractBeforeYandex(from)
                val password = database?.field3.toString()
                var filePath =item

                var newsName = ""
                if (convert.isChecked() == false){
                    subject = ""
                }
                val rename = findViewById<Switch>(R.id.rename)
                var oldFilePath = filePath
                var nameFilePath = getFileNameFromPath(filePath)
                if (rename.isChecked()) {
                    showInputDialog(this, "Choose name for file", "$nameFilePath") { userInput ->
                        val isbn = extractIsbn(userInput)
                        if (isbn !=false){
                            getBookDetailsByISBN(apiKey, isbn.toString()) { bookDetails ->
                                newsName = bookDetails.toString()
                                val pathWithout = getPathWithoutFileName(filePath)
                                val formated = getFileExtension(filePath)
                                val newFile = "$pathWithout/$newsName$formated"
                                filePath = renameFile(filePath,newFile).toString()
                                EmailSender.sendEmailWithAttachment(
                                    to,
                                    subject,
                                    bodyText,
                                    filePath,
                                    from,
                                    host,
                                    port,
                                    username,
                                    password,
                                    oldFilePath
                                )
                            }

                        }else
                        {
                            newsName = userInput
                        }

                        val pathWithout = getPathWithoutFileName(filePath)
                        val formated = getFileExtension(filePath)
                        val newFile = "$pathWithout/$newsName$formated"
                        if (newsName == ""){
                            println("nothing")
                        }else {
                            filePath = renameFile(filePath, newFile).toString()
                            EmailSender.sendEmailWithAttachment(
                                to,
                                subject,
                                bodyText,
                                filePath,
                                from,
                                host,
                                port,
                                username,
                                password,
                                oldFilePath
                            )
                        }
                    }

                }else {
                    EmailSender.sendEmailWithAttachment(
                        to,
                        subject,
                        bodyText,
                        filePath,
                        from,
                        host,
                        port,
                        username,
                        password,
                        oldFilePath
                    )
                }
            }

            // Дальнейшая обработка массива путей filePaths
        }
    }

    // Функция для получения пути файла из Uri
    private fun getPathFromUri(uri: Uri): String {
        var path = ""
        contentResolver.query(uri, null, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                path = "/data/user/0/com.example.myapplication/cache/"+cursor.getString(index)
            }
        }

        return path
    }
}



