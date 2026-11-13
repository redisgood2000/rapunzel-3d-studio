package com.rapunzel.studio

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class CharacterAppearance(
    val versionCode: Int,
    val versionName: String,
    val hair: FloatArray,
    val hairHighlight: FloatArray,
    val hairShadow: FloatArray,
    val skin: FloatArray,
    val skinLight: FloatArray,
    val dress: FloatArray,
    val dressLight: FloatArray,
    val dressDark: FloatArray
)

object ResourceManager {
    private const val RESOURCE_URL =
        "https://raw.githubusercontent.com/redisgood2000/rapunzel-3d-studio/main/resources/rapunzel-character.json"
    private const val FILE_NAME = "rapunzel-character.json"
    private val executor = Executors.newSingleThreadExecutor()

    fun load(context: Context): CharacterAppearance {
        val file = File(context.filesDir, FILE_NAME)
        val json = if (file.exists()) file.readText() else
            context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
        return parse(json)
    }

    fun checkForUpdates(
        context: Context,
        callback: (updated: Boolean, appearance: CharacterAppearance?, message: String) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val current = load(appContext)
                connection = (URL(RESOURCE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("User-Agent", "Rapunzel-3D-Studio-Android")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Resource server returned HTTP ${connection.responseCode}")
                }
                val remoteJson = connection.inputStream.bufferedReader().use { it.readText() }
                val remote = parse(remoteJson)
                if (remote.versionCode <= current.versionCode) {
                    callback(false, current, "Rapunzel resources are already up to date (${current.versionName}).")
                } else {
                    // Validate the entire pack before replacing the active copy.
                    val target = File(appContext.filesDir, FILE_NAME)
                    val temp = File(appContext.filesDir, "$FILE_NAME.tmp")
                    temp.writeText(remoteJson)
                    if (temp.length() <= 0L) throw IllegalStateException("Downloaded resource pack is empty")
                    if (target.exists() && !target.delete()) throw IllegalStateException("Could not replace old resource pack")
                    if (!temp.renameTo(target)) {
                        temp.delete()
                        throw IllegalStateException("Could not install downloaded resource pack")
                    }
                    callback(true, remote, "Rapunzel resources updated to ${remote.versionName}.")
                }
            } catch (e: Exception) {
                callback(false, null, "Resource update failed: ${e.message ?: "unknown error"}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parse(source: String): CharacterAppearance {
        val root = JSONObject(source)
        require(root.getInt("schemaVersion") == 1) { "Unsupported resource schema" }
        val colors = root.getJSONObject("colors")
        fun color(name: String): FloatArray {
            val array = colors.getJSONArray(name)
            require(array.length() == 3) { "Invalid color: $name" }
            return FloatArray(3) { i ->
                array.getDouble(i).toFloat().also { require(it in 0f..1f) { "Color values must be between 0 and 1" } }
            }
        }
        val version = root.getInt("versionCode")
        require(version > 0) { "Invalid resource version" }
        return CharacterAppearance(
            version, root.getString("versionName"),
            color("hair"), color("hairHighlight"), color("hairShadow"),
            color("skin"), color("skinLight"),
            color("dress"), color("dressLight"), color("dressDark")
        )
    }
}
