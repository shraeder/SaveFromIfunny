package com.ifunnysaver

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    private data class RemoteMedia(val url: String, val mimeType: String)

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
                downloadIFunnyMedia(url)
                return
            }
        }

        // Fallback to stream (direct media bytes)
        val streamUri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (streamUri != null) {
            Log.i(TAG, "Fallback to stream URI: $streamUri")
            saveSharedMedia(streamUri)
            return
        }

        Toast.makeText(this, "No iFunny link, image, or video stream found", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun extractUrl(text: String): String? {
        val regex = "(https?://ifunny\\.co/[^\\s?]+)".toRegex()
        return regex.find(text)?.value
    }

    private fun downloadIFunnyMedia(pageUrl: String) {
        Toast.makeText(this, "Downloading from iFunny...", Toast.LENGTH_SHORT).show()
        
        thread {
            try {
                val media = fetchMediaFromPage(pageUrl)
                if (media != null) {
                    Log.i(TAG, "Found media source: ${media.url} (${media.mimeType})")
                    saveRemoteMediaToPhotos(media)
                    runOnUiThread {
                        Toast.makeText(this, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Could not find media on page.", Toast.LENGTH_LONG).show()
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

    private fun fetchMediaFromPage(pageUrl: String): RemoteMedia? {
        val connection = URL(pageUrl).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val html = connection.inputStream.bufferedReader().use { it.readText() }

        val videoMeta = findFirstMetaContent(
            html,
            listOf("og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream")
        )
        if (videoMeta != null) {
            return RemoteMedia(videoMeta, "video/mp4")
        }
        
        val imageMeta = findFirstMetaContent(html, listOf("og:image", "twitter:image"))
        if (imageMeta != null) {
            return RemoteMedia(imageMeta, inferImageMimeType(imageMeta))
        }
        
        val mp4Regex = "(https?://[\\w./-]+\\.mp4)".toRegex()
        mp4Regex.find(html)?.value?.let { return RemoteMedia(it, "video/mp4") }

        val imageRegex = "(https?://[^\"'\\s>]+\\.(?:jpg|jpeg|png|webp))".toRegex(RegexOption.IGNORE_CASE)
        return imageRegex.find(html)?.value?.let { RemoteMedia(it, inferImageMimeType(it)) }
    }

    private fun findFirstMetaContent(html: String, propertyNames: List<String>): String? {
        val metaTagRegex = "<meta\\b[^>]*>".toRegex(RegexOption.IGNORE_CASE)
        val attributeRegex = "([a-zA-Z:-]+)\\s*=\\s*([\"'])(.*?)\\2".toRegex()

        for (tag in metaTagRegex.findAll(html)) {
            var propertyValue: String? = null
            var contentValue: String? = null

            for (attribute in attributeRegex.findAll(tag.value)) {
                val attributeName = attribute.groupValues[1].lowercase()
                val attributeValue = attribute.groupValues[3]
                when (attributeName) {
                    "property", "name" -> propertyValue = attributeValue
                    "content" -> contentValue = attributeValue
                }
            }

            if (propertyValue != null && contentValue != null && propertyNames.any { it.equals(propertyValue, ignoreCase = true) }) {
                return contentValue
            }
        }

        return null
    }

    private fun inferImageMimeType(url: String): String {
        return when {
            url.contains(".png", ignoreCase = true) -> "image/png"
            url.contains(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }
    }

    private fun saveRemoteMediaToPhotos(media: RemoteMedia) {
        when {
            media.mimeType.startsWith("image/") -> saveImageUrlToPhotos(media.url, media.mimeType)
            media.mimeType.startsWith("video/") -> saveVideoUrlToPhotos(media.url)
            else -> throw IOException("Unsupported remote media type: ${media.mimeType}")
        }
    }

    private fun saveVideoUrlToPhotos(videoUrl: String) {
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

    private fun saveImageUrlToPhotos(imageUrl: String, mimeType: String) {
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        connection.inputStream.use { input ->
            val originalBitmap = BitmapFactory.decodeStream(input)
                ?: throw IOException("Failed to decode downloaded image")
            val croppedBitmap = cropBottomPixels(originalBitmap, 20)
            saveBitmapToPhotos(croppedBitmap, mimeType)
        }
    }

    private fun saveSharedMedia(sourceUri: Uri) {
        val mimeType = contentResolver.getType(sourceUri)?.lowercase().orEmpty()

        thread {
            try {
                when {
                    mimeType.startsWith("image/") -> saveImageToPhotos(sourceUri, mimeType)
                    mimeType.startsWith("video/") || mimeType.isBlank() -> saveVideoUriToPhotos(sourceUri)
                    else -> throw IOException("Unsupported shared media type: $mimeType")
                }

                runOnUiThread {
                    Toast.makeText(this, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save shared media", e)
                runOnUiThread {
                    Toast.makeText(this, "Save error: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun saveVideoUriToPhotos(sourceUri: Uri) {
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

    private fun saveImageToPhotos(sourceUri: Uri, mimeType: String) {
        val resolver = contentResolver
        val croppedBitmap = resolver.openInputStream(sourceUri)?.use { input ->
            val originalBitmap = BitmapFactory.decodeStream(input)
                ?: throw IOException("Failed to decode shared image")
            cropBottomPixels(originalBitmap, 20)
        } ?: throw IOException("Failed to read shared image")

        saveBitmapToPhotos(croppedBitmap, mimeType)
    }

    private fun saveBitmapToPhotos(bitmap: Bitmap, mimeType: String) {
        val resolver = contentResolver
        val fileExtension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val compressFormat = when (fileExtension) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> Bitmap.CompressFormat.JPEG
        }
        val outputMimeType = when (fileExtension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val fileName = "ifunny_image_${System.currentTimeMillis()}.$fileExtension"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, outputMimeType)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/iFunny")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.getContentUri(
            if (Build.VERSION.SDK_INT >= 29) MediaStore.VOLUME_EXTERNAL_PRIMARY else "external"
        )
        val destUri = resolver.insert(collection, values) ?: throw IOException("Failed to create MediaStore entry")
        var shouldPublish = false

        try {
            resolver.openOutputStream(destUri)?.use { output ->
                if (!bitmap.compress(compressFormat, 100, output)) {
                    throw IOException("Failed to write cropped image")
                }
            } ?: throw IOException("Failed to open output stream")
            shouldPublish = true
        } catch (e: Exception) {
            resolver.delete(destUri, null, null)
            throw e
        } finally {
            bitmap.recycle()
            if (Build.VERSION.SDK_INT >= 29) {
                if (shouldPublish) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(destUri, values, null, null)
                }
            }
        }
    }

    private fun cropBottomPixels(source: Bitmap, pixelsToCrop: Int): Bitmap {
        val safeCropHeight = (source.height - pixelsToCrop).coerceAtLeast(1)
        val croppedBitmap = Bitmap.createBitmap(source, 0, 0, source.width, safeCropHeight)
        if (croppedBitmap != source) {
            source.recycle()
        }
        return croppedBitmap
    }

    companion object {
        private const val TAG = "IFunnySaver"
    }
}
