package com.ifunnysaver

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class ReceiveShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handleIncomingShare(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled error", e)
            Toast.makeText(this, "Crash: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun handleIncomingShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) {
            finish()
            return
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        Log.i(TAG, "Received text: $text")
        
        if (!text.isNullOrBlank() && text.contains("ifunny.co")) {
            val url = extractUrl(text)
            if (url != null) {
                Log.i(TAG, "Extracted URL: $url")
                downloadIFunnyVideo(url)
                return
            }
        }

        // Fallback to stream (direct media bytes)
        val streamUri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (streamUri != null) {
            Log.i(TAG, "Fallback to stream URI: $streamUri")
            saveUriToPhotos(streamUri)
            finish()
            return
        }

        Toast.makeText(this, "No iFunny link or video stream found", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun extractUrl(text: String): String? {
        val regex = "(https?://ifunny\\.co/[^\\s?]+)".toRegex()
        return regex.find(text)?.value
    }

    private fun downloadIFunnyVideo(pageUrl: String) {
        Toast.makeText(this, "Downloading from iFunny...", Toast.LENGTH_SHORT).show()
        
        thread {
            try {
                val videoUrl = fetchVideoUrlFromPage(pageUrl)
                if (videoUrl != null) {
                    Log.i(TAG, "Found video source: $videoUrl")
                    saveUrlToPhotos(videoUrl)
                    runOnUiThread {
                        Toast.makeText(this, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Could not find video on page.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                runOnUiThread {
                    Toast.makeText(this, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun fetchVideoUrlFromPage(pageUrl: String): String? {
        val connection = URL(pageUrl).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val html = connection.inputStream.bufferedReader().use { it.readText() }
        
        val metaRegex = "property=\"og:video\"\\s+content=\"([^\"]+)\"".toRegex()
        val match = metaRegex.find(html)
        if (match != null) return match.groupValues[1]
        
        val mp4Regex = "(https?://[\\w./-]+\\.mp4)".toRegex()
        return mp4Regex.find(html)?.value
    }

    private fun saveUrlToPhotos(videoUrl: String) {
        val resolver = contentResolver
        val fileName = "ifunny_${System.currentTimeMillis()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                // Saving to DCIM makes it show up in the main Photos/Gallery view
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/iFunny")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Video.Media.getContentUri(
            if (Build.VERSION.SDK_INT >= 29) MediaStore.VOLUME_EXTERNAL_PRIMARY else "external"
        )
        val destUri = resolver.insert(collection, values) ?: throw IOException("Failed to create MediaStore entry")

        val urlConnection = URL(videoUrl).openConnection() as HttpURLConnection
        urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0")
        
        urlConnection.inputStream.use { input ->
            resolver.openOutputStream(destUri)?.use { output ->
                input.copyTo(output)
            }
        }

        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)
        }
    }

    private fun saveUriToPhotos(sourceUri: Uri) {
        val resolver = contentResolver
        val fileName = "ifunny_stream_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/iFunny")
            }
        }
        val collection = MediaStore.Video.Media.getContentUri(
            if (Build.VERSION.SDK_INT >= 29) MediaStore.VOLUME_EXTERNAL_PRIMARY else "external"
        )
        val destUri = resolver.insert(collection, values) ?: return
        resolver.openInputStream(sourceUri)?.use { input ->
            resolver.openOutputStream(destUri)?.use { output ->
                input.copyTo(output)
            }
        }
    }

    companion object {
        private const val TAG = "IFunnySaver"
    }
}
