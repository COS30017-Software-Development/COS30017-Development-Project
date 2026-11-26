package com.example.cos30017developmentproject.data.remote

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class StagedItemImage(
	val file: File,
	val contentType: String
)

class ItemImageTooLargeException : IllegalArgumentException()
class InvalidItemImageException : IllegalArgumentException()

object ItemImageFiles {
	suspend fun stage(context: Context, uri: Uri): StagedItemImage = withContext(Dispatchers.IO) {
		val resolver = context.contentResolver
		val contentType = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
		val directory = File(context.cacheDir, "item-images").apply { mkdirs() }
		val file = File(directory, "selected_${UUID.randomUUID()}")
		try {
			val input = resolver.openInputStream(uri) ?: throw InvalidItemImageException()
			input.use { source ->
				file.outputStream().use { destination ->
					val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
					var totalBytes = 0L
					while (true) {
						val read = source.read(buffer)
						if (read < 0) break
						totalBytes += read
						if (!ItemImageSize.isWithinLimit(totalBytes)) throw ItemImageTooLargeException()
						destination.write(buffer, 0, read)
					}
				}
			}
			validate(file)
			StagedItemImage(file, contentType)
		} catch (exception: Exception) {
			file.delete()
			throw exception
		}
	}

	fun validate(file: File) {
		if (!file.isFile || file.length() == 0L) throw InvalidItemImageException()
		if (!ItemImageSize.isWithinLimit(file.length())) throw ItemImageTooLargeException()
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeFile(file.absolutePath, bounds)
		if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw InvalidItemImageException()
	}
}

object ItemImageSize {
	const val MAX_BYTES = 5L * 1024L * 1024L

	fun isWithinLimit(size: Long): Boolean = size in 1..MAX_BYTES
}
