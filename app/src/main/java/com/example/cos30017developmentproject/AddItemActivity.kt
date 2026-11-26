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
import android.widget.RadioGroup
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
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import com.example.cos30017developmentproject.data.remote.InvalidItemImageException
import com.example.cos30017developmentproject.data.remote.ItemImageFiles
import com.example.cos30017developmentproject.data.remote.ItemImageTooLargeException
import com.example.cos30017developmentproject.data.remote.StagedItemImage
import com.example.cos30017developmentproject.ui.home.ItemImageLoader
import com.example.cos30017developmentproject.ui.items.ItemTypeOption
import com.example.cos30017developmentproject.ui.items.ItemTypeSelection
import com.example.cos30017developmentproject.ui.items.ItemValidation
import com.example.cos30017developmentproject.ui.items.ItemValidationError
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class AddItemActivity : AppCompatActivity() {
	private lateinit var repository: FirestoreItemRepository
	private lateinit var sessionManager: SessionManager
	private var isSaving = false
	private var selectedImage: StagedItemImage? = null
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
		sessionManager = SessionManager(this)
		if (sessionManager.userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
			finish()
			return
		}
		enableEdgeToEdge()
		setContentView(R.layout.activity_add_item)
		selectedImage = savedInstanceState?.getString(STATE_SELECTED_IMAGE_PATH)?.let { path ->
			File(path).takeIf(File::isFile)?.let { file ->
				StagedItemImage(file, savedInstanceState.getString(STATE_SELECTED_IMAGE_TYPE) ?: "image/jpeg")
			}
		}
		cameraUri = savedInstanceState?.getString(STATE_CAMERA_URI)?.let(Uri::parse)
		cameraPath = savedInstanceState?.getString(STATE_CAMERA_PATH)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.add_item_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
			WindowInsetsCompat.CONSUMED
		}
		repository = FirestoreItemRepository()
		findViewById<View>(R.id.add_item_cancel).setOnClickListener {
			clearSelectedImage()
			clearCameraFile()
			finish()
		}
		findViewById<View>(R.id.add_item_save).setOnClickListener { saveItem() }
		findViewById<View>(R.id.add_item_take_photo).setOnClickListener { requestCamera() }
		findViewById<View>(R.id.add_item_choose_gallery).setOnClickListener {
			choosePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
		}
		findViewById<View>(R.id.add_item_remove_image).setOnClickListener {
			clearSelectedImage()
			renderSelectedImage()
		}
		findViewById<ImageView>(R.id.add_item_image_preview).setOnClickListener {
			ItemImageLoader.showPreview(this, selectedImage?.file, R.drawable.sample_backpack)
		}
		renderSelectedImage()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putString(STATE_SELECTED_IMAGE_PATH, selectedImage?.file?.absolutePath)
		outState.putString(STATE_SELECTED_IMAGE_TYPE, selectedImage?.contentType)
		outState.putString(STATE_CAMERA_URI, cameraUri?.toString())
		outState.putString(STATE_CAMERA_PATH, cameraPath)
		super.onSaveInstanceState(outState)
	}

	private fun saveItem() {
		if (isSaving) return
		val typeSelection = ItemTypeSelection.fromCheckedButton(
			findViewById<RadioGroup>(R.id.add_item_type).checkedRadioButtonId,
			R.id.add_item_type_lost,
			R.id.add_item_type_found
		)
		if (typeSelection == null) {
			Toast.makeText(this, R.string.item_type_required, Toast.LENGTH_SHORT).show()
			return
		}
		val title = input(R.id.add_item_name)
		val category = input(R.id.add_item_category)
		val location = input(R.id.add_item_location)
		val description = input(R.id.add_item_description)
		val validationError = ItemValidation.validate(title, category, location, description)
		if (validationError != null) {
			showValidationError(validationError)
			return
		}
		if (sessionManager.userId == null || repository.currentUserUid == null) {
			startActivity(Intent(this, AuthActivity::class.java))
			finish()
			return
		}
		val type = when (typeSelection) {
			ItemTypeOption.LOST -> FirestoreItemRepository.TYPE_LOST
			ItemTypeOption.FOUND -> FirestoreItemRepository.TYPE_FOUND
		}
		isSaving = true
		findViewById<MaterialButton>(R.id.add_item_save).isEnabled = false
		lifecycleScope.launch {
			try {
				selectedImage?.let { ItemImageFiles.validate(it.file) }
				repository.createItem(
					title, description, category, location, type,
					imageUri = selectedImage?.file?.let(Uri::fromFile),
					imageContentType = selectedImage?.contentType ?: "image/jpeg"
				)
				clearSelectedImage()
				Toast.makeText(this@AddItemActivity, R.string.item_posted, Toast.LENGTH_SHORT).show()
				finish()
			} catch (e: CancellationException) {
				throw e
			} catch (e: ItemImageTooLargeException) {
				Toast.makeText(this@AddItemActivity, R.string.item_image_too_large, Toast.LENGTH_SHORT).show()
			} catch (e: InvalidItemImageException) {
				Toast.makeText(this@AddItemActivity, R.string.item_image_invalid, Toast.LENGTH_SHORT).show()
			} catch (e: Exception) {
				Log.e(TAG, "Unable to create item", e)
				Toast.makeText(this@AddItemActivity, R.string.item_save_failed, Toast.LENGTH_SHORT).show()
			} finally {
				isSaving = false
				if (!isFinishing) findViewById<MaterialButton>(R.id.add_item_save).isEnabled = true
			}
		}
	}

	private fun stageGalleryImage(uri: Uri) {
		lifecycleScope.launch {
			try {
				setSelectedImage(ItemImageFiles.stage(this@AddItemActivity, uri))
			} catch (exception: CancellationException) {
				throw exception
			} catch (exception: ItemImageTooLargeException) {
				Toast.makeText(this@AddItemActivity, R.string.item_image_too_large, Toast.LENGTH_SHORT).show()
			} catch (exception: InvalidItemImageException) {
				Toast.makeText(this@AddItemActivity, R.string.item_image_invalid, Toast.LENGTH_SHORT).show()
			} catch (exception: Exception) {
				Log.e(TAG, "Unable to use selected item photo", exception)
				Toast.makeText(this@AddItemActivity, R.string.item_image_invalid, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun setSelectedImage(image: StagedItemImage) {
		selectedImage?.file?.takeIf { it != image.file }?.delete()
		selectedImage = image
		renderSelectedImage()
	}

	private fun clearSelectedImage() {
		selectedImage?.file?.delete()
		selectedImage = null
	}

	private fun renderSelectedImage() {
		val preview = findViewById<ImageView>(R.id.add_item_image_preview)
		ItemImageLoader.load(preview, selectedImage?.file, R.drawable.sample_backpack, ImageView.ScaleType.FIT_CENTER)
		preview.contentDescription = getString(R.string.item_image_description, getString(R.string.item_name))
		findViewById<View>(R.id.add_item_remove_image).visibility =
			if (selectedImage == null) View.GONE else View.VISIBLE
	}

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

	private companion object {
		const val TAG = "AddItemActivity"
		const val STATE_SELECTED_IMAGE_PATH = "add_item.selected_image_path"
		const val STATE_SELECTED_IMAGE_TYPE = "add_item.selected_image_type"
		const val STATE_CAMERA_URI = "add_item.camera_uri"
		const val STATE_CAMERA_PATH = "add_item.camera_path"
	}
}
