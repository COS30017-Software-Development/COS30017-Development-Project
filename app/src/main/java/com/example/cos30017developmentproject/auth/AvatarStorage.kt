package com.example.cos30017developmentproject.auth

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object AvatarStorage {
	private const val AVATAR_DIRECTORY = "avatars"

	fun fileFor(context: Context, path: String?): File? {
		if (path.isNullOrBlank()) return null
		val directory = File(context.filesDir, AVATAR_DIRECTORY).canonicalFile
		val file = File(path).canonicalFile
		return file.takeIf { it.path.startsWith(directory.path + File.separator) && it.isFile }
	}

	suspend fun save(context: Context, userId: Long, source: Uri): String = withContext(Dispatchers.IO) {
		val directory = File(context.filesDir, AVATAR_DIRECTORY).apply { mkdirs() }
		val destination = File(directory, "avatar_${userId}_${UUID.randomUUID()}.jpg")
		val inputStream = if (source.scheme == "file") source.path?.let(::File)?.inputStream()
		else context.contentResolver.openInputStream(source)
		inputStream?.use { input ->
			destination.outputStream().use { output -> input.copyTo(output) }
		} ?: throw IllegalStateException("Unable to read the cropped avatar.")
		destination.absolutePath
	}

	suspend fun stageForCrop(context: Context, source: Uri): File = withContext(Dispatchers.IO) {
		val directory = File(context.cacheDir, AVATAR_DIRECTORY).apply { mkdirs() }
		val destination = File(directory, "source_${UUID.randomUUID()}.jpg")
		val inputStream = if (source.scheme == "file") source.path?.let(::File)?.inputStream()
		else context.contentResolver.openInputStream(source)
		inputStream?.use { input ->
			destination.outputStream().use { output -> input.copyTo(output) }
		} ?: throw IllegalStateException("Unable to read the selected avatar.")
		destination
	}

	fun delete(context: Context, path: String?) {
		fileFor(context, path)?.delete()
	}
}
