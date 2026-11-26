package com.example.cos30017developmentproject

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import com.example.cos30017developmentproject.data.remote.InvalidItemImageException
import com.example.cos30017developmentproject.data.remote.ItemImageFiles
import com.example.cos30017developmentproject.data.remote.ItemImageTooLargeException
import com.example.cos30017developmentproject.data.remote.StagedItemImage
import com.example.cos30017developmentproject.ui.home.ItemImageLoader
import com.example.cos30017developmentproject.ui.items.ItemValidation
import com.example.cos30017developmentproject.ui.items.ItemValidationError
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class EditItemActivity : AppCompatActivity() {
	private lateinit var repository: FirestoreItemRepository
	private lateinit var localRepository: LostAndFoundRepository
	private var itemId = ""
	private var itemType = ""
	private var isSaving = false
	private var selectedImage: StagedItemImage? = null
	private var originalImageUrl: String? = null
	private var legacyImageName = "sample_backpack"
	private var canUploadImage = false
	private var removeImage = false
	private var cameraUri: Uri? = null
	private var cameraPath: String? = null

	private val choosePhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
		if (uri != null) stageGalleryImage(uri)
	}
	private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
		val file = cameraPath?.let(::File)
		cameraPath = null
		cameraUri = null
		if (taken && file != null) {
			try {
				ItemImageFiles.validate(file)
				setSelectedImage(StagedItemImage(file, "image/jpeg"))
			} catch (exception: ItemImageTooLargeException) {
				file.delete()
				Toast.makeText(this, R.string.item_image_too_large, Toast.LENGTH_SHORT).show()
			} catch (exception: Exception) {
				file.delete()
				Log.e(TAG, "Unable to use captured item photo", exception)
				Toast.makeText(this, R.string.item_image_invalid, Toast.LENGTH_SHORT).show()
			}
		} else {
			file?.delete()
		}
	}
	private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if (granted) launchCamera()
		else Toast.makeText(this, R.string.profile_camera_denied, Toast.LENGTH_SHORT).show()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		itemId = intent.getStringExtra(ItemDetailsActivity.EXTRA_ITEM_ID).orEmpty()
		itemType = intent.getStringExtra(ItemDetailsActivity.EXTRA_ITEM_TYPE).orEmpty()
		if (itemId.isBlank() || itemType !in setOf(ItemDetailsActivity.TYPE_LOST, ItemDetailsActivity.TYPE_FOUND)) {
			finish()
			return
		}
		selectedImage = savedInstanceState?.getString(STATE_SELECTED_IMAGE_PATH)?.let { path ->
			File(path).takeIf(File::isFile)?.let { file ->
				StagedItemImage(file, savedInstanceState.getString(STATE_SELECTED_IMAGE_TYPE) ?: "image/jpeg")
			}
		}
		removeImage = savedInstanceState?.getBoolean(STATE_REMOVE_IMAGE) ?: false
		cameraUri = savedInstanceState?.getString(STATE_CAMERA_URI)?.let(Uri::parse)
		cameraPath = savedInstanceState?.getString(STATE_CAMERA_PATH)
		enableEdgeToEdge()
		setContentView(R.layout.activity_edit_item)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.edit_item_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
			WindowInsetsCompat.CONSUMED
		}
		repository = FirestoreItemRepository()
		localRepository = LostAndFoundRepository(AppDatabase.getInstance(this))
		findViewById<View>(R.id.edit_item_cancel).setOnClickListener {
			clearSelectedImage()
			clearCameraFile()
			finish()
		}
		findViewById<View>(R.id.edit_item_save).setOnClickListener { saveItem() }
		findViewById<View>(R.id.edit_item_take_photo).setOnClickListener { requestCamera() }
		findViewById<View>(R.id.edit_item_choose_gallery).setOnClickListener {
			choosePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
		}
		findViewById<View>(R.id.edit_item_remove_image).setOnClickListener {
			clearSelectedImage()
			removeImage = true
			renderSelectedImage()
		}
		findViewById<ImageView>(R.id.edit_item_image_preview).setOnClickListener {
			val source = selectedImage?.file ?: originalImageUrl.takeUnless { removeImage }
			ItemImageLoader.showPreview(this, source, legacyDrawable())
		}
		loadItem()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putString(STATE_SELECTED_IMAGE_PATH, selectedImage?.file?.absolutePath)
		outState.putString(STATE_SELECTED_IMAGE_TYPE, selectedImage?.contentType)
		outState.putBoolean(STATE_REMOVE_IMAGE, removeImage)
		outState.putString(STATE_CAMERA_URI, cameraUri?.toString())
		outState.putString(STATE_CAMERA_PATH, cameraPath)
		super.onSaveInstanceState(outState)
	}

	private fun loadItem() {
		lifecycleScope.launch {
			try {
				val currentUid = repository.currentUserUid
				val cloudItem = repository.getItem(itemId)
				val item = cloudItem?.takeIf { it.postedBy == currentUid }?.let {
					EditableItem(it.itemName, it.description, it.location, it.category, it.imageName, it.imageUrl, true)
				} ?: if (cloudItem == null) {
					val localId = itemId.toLongOrNull()
					val userId = SessionManager(this@EditItemActivity).userId
					when (itemType) {
						ItemDetailsActivity.TYPE_LOST -> localId?.let { id ->
							localRepository.getLostItem(id)?.takeIf { it.postedByUserId == userId }?.let {
								EditableItem(it.title, it.description, it.location, it.category, it.imageName, null, false)
							}
						}
						ItemDetailsActivity.TYPE_FOUND -> localId?.let { id ->
							localRepository.getFoundItem(id)?.takeIf { it.postedByUserId == userId }?.let {
								EditableItem(it.title, it.description, it.location, it.category, it.imageName, null, false)
							}
						}
						else -> null
					}
				} else null
				val editableItem = item ?: run {
					showPermissionDeniedAndFinish()
					return@launch
				}
				legacyImageName = editableItem.imageName
				originalImageUrl = editableItem.imageUrl
				canUploadImage = editableItem.isFirestoreItem
				findViewById<View>(R.id.edit_item_image_controls).visibility =
					if (canUploadImage) View.VISIBLE else View.GONE
				findViewById<EditText>(R.id.edit_item_name).setText(editableItem.title)
				findViewById<EditText>(R.id.edit_item_category).setText(editableItem.category)
				findViewById<EditText>(R.id.edit_item_location).setText(editableItem.location)
				findViewById<EditText>(R.id.edit_item_description).setText(editableItem.description)
				renderSelectedImage()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Unable to load item", e)
				Toast.makeText(this@EditItemActivity, R.string.item_save_failed, Toast.LENGTH_SHORT).show()
				finish()
			}
		}
	}

	private fun saveItem() {
		if (isSaving) return
		val title = input(R.id.edit_item_name)
		val category = input(R.id.edit_item_category)
		val location = input(R.id.edit_item_location)
		val description = input(R.id.edit_item_description)
		val validationError = ItemValidation.validate(title, category, location, description)
		if (validationError != null) {
			showValidationError(validationError)
			return
		}
		isSaving = true
		findViewById<MaterialButton>(R.id.edit_item_save).isEnabled = false
		lifecycleScope.launch {
			try {
				selectedImage?.let { ItemImageFiles.validate(it.file) }
				val currentUid = repository.currentUserUid
					?: run {
						showPermissionDeniedAndFinish()
						return@launch
					}
				val item = repository.getItem(itemId)
				val expectedType = if (itemType == ItemDetailsActivity.TYPE_FOUND) {
					FirestoreItemRepository.TYPE_FOUND
				} else {
					FirestoreItemRepository.TYPE_LOST
				}
				var cleanupError: Exception? = null
				if (item != null && (item.postedBy != currentUid || item.type != expectedType)) {
					showPermissionDeniedAndFinish()
					return@launch
				}
				if (item != null) {
					cleanupError = repository.updateItem(
						itemId, title, description, category, location,
						imageUri = selectedImage?.file?.let(Uri::fromFile),
						imageContentType = selectedImage?.contentType ?: "image/jpeg",
						removeImage = removeImage
					)
				} else {
					val localId = itemId.toLongOrNull()
						?: run {
							showPermissionDeniedAndFinish()
							return@launch
						}
					val userId = SessionManager(this@EditItemActivity).userId
						?: run {
							showPermissionDeniedAndFinish()
							return@launch
						}
					val updated = when (itemType) {
						ItemDetailsActivity.TYPE_LOST -> localRepository.updateLostItemOwned(
							localId, userId, title, description, location, category, legacyImageName
						)
						ItemDetailsActivity.TYPE_FOUND -> localRepository.updateFoundItemOwned(
							localId, userId, title, description, location, category, legacyImageName
						)
						else -> false
					}
					if (!updated) {
						showPermissionDeniedAndFinish()
						return@launch
					}
				}
				if (cleanupError != null) {
					Toast.makeText(this@EditItemActivity, R.string.item_image_cleanup_failed, Toast.LENGTH_SHORT).show()
				} else {
					Toast.makeText(this@EditItemActivity, R.string.item_updated, Toast.LENGTH_SHORT).show()
				}
				clearSelectedImage()
				setResult(RESULT_OK)
				finish()
			} catch (e: CancellationException) {
				throw e
			} catch (e: ItemImageTooLargeException) {
				Toast.makeText(this@EditItemActivity, R.string.item_image_too_large, Toast.LENGTH_SHORT).show()
			} catch (e: InvalidItemImageException) {
				Toast.makeText(this@EditItemActivity, R.string.item_image_invalid, Toast.LENGTH_SHORT).show()
			} catch (e: Exception) {
				Log.e(TAG, "Unable to update item", e)
				Toast.makeText(this@EditItemActivity, R.string.item_save_failed, Toast.LENGTH_SHORT).show()
			} finally {
				isSaving = false
				if (!isFinishing) findViewById<MaterialButton>(R.id.edit_item_save).isEnabled = true
			}
		}
	}

	private fun stageGalleryImage(uri: Uri) {
		lifecycleScope.launch {
			try {
				setSelectedImage(ItemImageFiles.stage(this@EditItemActivity, uri))
			} catch (exception: CancellationException) {
				throw exception
			} catch (exception: ItemImageTooLargeException) {
				Toast.makeText(this@EditItemActivity, R.string.item_image_too_large, Toast.LENGTH_SHORT).show()
			} catch (exception: InvalidItemImageException) {
				Toast.makeText(this@EditItemActivity, R.string.item_image_invalid, Toast.LENGTH_SHORT).show()
			} catch (exception: Exception) {
				Log.e(TAG, "Unable to use selected item photo", exception)
				Toast.makeText(this@EditItemActivity, R.string.item_image_invalid, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun setSelectedImage(image: StagedItemImage) {
		selectedImage?.file?.takeIf { it != image.file }?.delete()
		selectedImage = image
		removeImage = false
		renderSelectedImage()
	}

	private fun clearSelectedImage() {
		selectedImage?.file?.delete()
		selectedImage = null
	}

	private fun renderSelectedImage() {
		val preview = findViewById<ImageView>(R.id.edit_item_image_preview)
		val source = selectedImage?.file ?: originalImageUrl.takeUnless { removeImage }
		ItemImageLoader.load(preview, source, legacyDrawable(), ImageView.ScaleType.FIT_CENTER)
		preview.contentDescription = getString(R.string.item_image_description, input(R.id.edit_item_name).ifBlank {
			getString(R.string.item_name)
		})
		findViewById<View>(R.id.edit_item_remove_image).visibility =
			if (selectedImage != null || originalImageUrl != null) View.VISIBLE else View.GONE
	}

	private fun legacyDrawable(): Int =
		resources.getIdentifier(legacyImageName, "drawable", packageName).takeIf { it != 0 }
			?: R.drawable.sample_backpack

	private fun requestCamera() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
			launchCamera()
		} else {
			cameraPermission.launch(Manifest.permission.CAMERA)
		}
	}

	private fun launchCamera() {
		clearCameraFile()
		try {
			val directory = File(cacheDir, "item-images").apply { mkdirs() }
			val image = File(directory, "camera_${UUID.randomUUID()}.jpg")
			cameraPath = image.absolutePath
			val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", image)
			cameraUri = uri
			takePhoto.launch(uri)
		} catch (exception: Exception) {
			clearCameraFile()
			Log.e(TAG, "Unable to launch camera for item photo", exception)
			Toast.makeText(this, R.string.item_image_invalid, Toast.LENGTH_SHORT).show()
		}
	}

	private fun clearCameraFile() {
		cameraPath?.let { File(it).delete() }
		cameraPath = null
		cameraUri = null
	}

	private fun input(id: Int) = findViewById<EditText>(id).text.toString().trim()

	private fun showValidationError(error: ItemValidationError) {
		val message = when (error) {
			ItemValidationError.REQUIRED -> R.string.item_fields_required
			ItemValidationError.TITLE_TOO_LONG -> R.string.item_title_too_long
			ItemValidationError.CATEGORY_TOO_LONG -> R.string.item_category_too_long
			ItemValidationError.LOCATION_TOO_LONG -> R.string.item_location_too_long
			ItemValidationError.DESCRIPTION_TOO_LONG -> R.string.item_description_too_long
		}
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
	}

	private fun showPermissionDeniedAndFinish() {
		Toast.makeText(this, R.string.item_action_not_allowed, Toast.LENGTH_SHORT).show()
		finish()
	}

	private data class EditableItem(
		val title: String,
		val description: String,
		val location: String,
		val category: String,
		val imageName: String,
		val imageUrl: String?,
		val isFirestoreItem: Boolean
	)

	private companion object {
		const val TAG = "EditItemActivity"
		const val STATE_SELECTED_IMAGE_PATH = "edit_item.selected_image_path"
		const val STATE_SELECTED_IMAGE_TYPE = "edit_item.selected_image_type"
		const val STATE_REMOVE_IMAGE = "edit_item.remove_image"
		const val STATE_CAMERA_URI = "edit_item.camera_uri"
		const val STATE_CAMERA_PATH = "edit_item.camera_path"
	}
}
