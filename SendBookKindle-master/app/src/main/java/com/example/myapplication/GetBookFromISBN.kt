import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.CoroutineContext

public fun getBookDetailsByISBN(apiKey: String, isbn: String, callback: (String?) -> Unit) {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn&key=$apiKey")
        .build()

    // Запуск в фоновом потоке с использованием корутин
    GlobalScope.launch(Dispatchers.IO) {
        val result = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val responseBody = response.body?.string() ?: return@use null
                val json = JSONObject(responseBody)
                val items = json.optJSONArray("items") ?: return@use null
                if (items.length() == 0) return@use null
                val volumeInfo = items.getJSONObject(0).getJSONObject("volumeInfo")
                val title = volumeInfo.getString("title")
                val publishedDate = volumeInfo.optString("publishedDate", "Unknown").substring(0,4)
                val authorsArray = volumeInfo.optJSONArray("authors")
                val authors = if (authorsArray != null) {
                    (0 until authorsArray.length()).joinToString(", ") { authorsArray.getString(it) }
                } else {
                    "Unknown"
                }
                 "$title\n$authors\n$publishedDate"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        withContext(Dispatchers.Main) {
            callback(result)
        }
    }
}