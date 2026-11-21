package com.example.cos30017developmentproject

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
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
import com.example.cos30017developmentproject.auth.AuthValidator
import com.example.cos30017developmentproject.auth.AvatarStorage
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.auth.UserRepository
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class EditProfileActivity : AppCompatActivity() {
	private lateinit var repository: UserRepository
	private var userId: Long? = null
	private var originalAvatarPath: String? = null
	private var pendingAvatarUri: Uri? = null
	private var pendingAvatarFilePath: String? = null
	private var cameraUri: Uri? = null
	private var cameraFilePath: String? = null

	private val choosePhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
		if (uri != null) startCrop(uri)
	}

	private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
		val uri = cameraUri
		if (taken && uri != null) startCrop(uri) else clearCameraFile()
	}

	private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if (granted) launchCamera() else Toast.makeText(this, R.string.profile_camera_denied, Toast.LENGTH_SHORT).show()
	}

	private val cropPhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == RESULT_OK) {
			val cropped = UCrop.getOutput(result.data ?: Intent())
			if (cropped != null) {
				pendingAvatarUri = cropped
				previewAvatar(cropped)
			} else showAvatarError()
		} else if (result.resultCode == UCrop.RESULT_ERROR) {
			showAvatarError()
		} else clearPendingAvatar()
		clearCameraFile()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		userId = SessionManager(this).userId
		if (userId == null) {
			finish()
			return
		}
		enableEdgeToEdge()
		setContentView(R.layout.activity_edit_profile)
		repository = UserRepository(AppDatabase.getInstance(this).userDao())
		pendingAvatarUri = savedInstanceState?.getString(STATE_PENDING_AVATAR_URI)?.let(Uri::parse)
		pendingAvatarFilePath = savedInstanceState?.getString(STATE_PENDING_AVATAR_PATH)
		cameraUri = savedInstanceState?.getString(STATE_CAMERA_URI)?.let(Uri::parse)
		cameraFilePath = savedInstanceState?.getString(STATE_CAMERA_PATH)
		originalAvatarPath = savedInstanceState?.getString(STATE_ORIGINAL_AVATAR_PATH)
		ViewCompat.setAccessibilityHeading(findViewById(R.id.profile_edit_title), true)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profile_edit_page)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
			WindowInsetsCompat.CONSUMED
		}
		findViewById<View>(R.id.profile_cancel).setOnClickListener { discardChangesAndFinish() }
		findViewById<View>(R.id.profile_save).setOnClickListener { saveProfile() }
		findViewById<View>(R.id.profile_avatar_input).setOnClickListener { showAvatarChoices() }
		findViewById<View>(R.id.profile_change_avatar).setOnClickListener { showAvatarChoices() }
		lifecycleScope.launch {
			val user = repository.getUser(userId ?: return@launch) ?: return@launch
			findViewById<EditText>(R.id.profile_name_input).setText(user.username)
			if (originalAvatarPath == null) originalAvatarPath = user.avatarPath
			pendingAvatarUri?.let(::previewAvatar) ?: showStoredAvatar(originalAvatarPath)
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putString(STATE_PENDING_AVATAR_URI, pendingAvatarUri?.toString())
		outState.putString(STATE_PENDING_AVATAR_PATH, pendingAvatarFilePath)
		outState.putString(STATE_CAMERA_URI, cameraUri?.toString())
		outState.putString(STATE_CAMERA_PATH, cameraFilePath)
		outState.putString(STATE_ORIGINAL_AVATAR_PATH, originalAvatarPath)
		super.onSaveInstanceState(outState)
	}

	private fun showAvatarChoices() {
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.profile_avatar_picker_title)
			.setItems(arrayOf(getString(R.string.profile_take_photo), getString(R.string.profile_choose_gallery))) { _, which ->
				if (which == 0) requestCamera() else choosePhoto.launch(
					PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
				)
			}
			.show()
	}

	private fun requestCamera() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
		else cameraPermission.launch(Manifest.permission.CAMERA)
	}

	private fun launchCamera() {
		clearCameraFile()
		val directory = File(cacheDir, "avatars").apply { mkdirs() }
		val image = File(directory, "camera_${UUID.randomUUID()}.jpg")
		cameraFilePath = image.absolutePath
		cameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", image)
		cameraUri?.let(takePhoto::launch)
	}

	private fun startCrop(source: Uri) {
		clearPendingAvatar()
		val destination = File(File(cacheDir, "avatars").apply { mkdirs() }, "crop_${UUID.randomUUID()}.jpg")
		pendingAvatarFilePath = destination.absolutePath
		val destinationUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", destination)
		val options = UCrop.Options().apply {
			setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
			setCompressionQuality(90)
			setToolbarColor(ContextCompat.getColor(this@EditProfileActivity, R.color.black))
			setActiveControlsWidgetColor(ContextCompat.getColor(this@EditProfileActivity, R.color.brand_red_accessible))
		}
		cropPhoto.launch(
			UCrop.of(source, destinationUri)
				.withAspectRatio(1f, 1f)
				.withMaxResultSize(600, 600)
				.withOptions(options)
				.getIntent(this)
		)
	}

	private fun saveProfile() {
		val name = findViewById<EditText>(R.id.profile_name_input).text.toString()
		val nameLayout = findViewById<TextInputLayout>(R.id.profile_name_input_layout)
		if (!AuthValidator.isNameValid(name)) {
			nameLayout.error = getString(R.string.auth_name_error)
			return
		}
		nameLayout.error = null
		findViewById<View>(R.id.profile_save).isEnabled = false
		lifecycleScope.launch {
			var savedAvatarPath: String? = originalAvatarPath
			try {
				pendingAvatarUri?.let { savedAvatarPath = AvatarStorage.save(this@EditProfileActivity, userId ?: return@launch, it) }
				if (repository.updateProfile(userId ?: return@launch, name, savedAvatarPath)) {
					if (savedAvatarPath != originalAvatarPath) AvatarStorage.delete(this@EditProfileActivity, originalAvatarPath)
					clearPendingAvatar()
					Toast.makeText(this@EditProfileActivity, R.string.profile_name_saved, Toast.LENGTH_SHORT).show()
					finish()
				} else {
					if (savedAvatarPath != originalAvatarPath) AvatarStorage.delete(this@EditProfileActivity, savedAvatarPath)
					findViewById<View>(R.id.profile_save).isEnabled = true
				}
			} catch (_: Exception) {
				if (savedAvatarPath != originalAvatarPath) AvatarStorage.delete(this@EditProfileActivity, savedAvatarPath)
				showAvatarError()
				findViewById<View>(R.id.profile_save).isEnabled = true
			}
		}
	}

	private fun showStoredAvatar(path: String?) {
		val file = AvatarStorage.fileFor(this, path)
		if (file == null) showDefaultAvatar() else previewAvatar(Uri.fromFile(file))
	}

	private fun previewAvatar(uri: Uri) {
		val avatar = findViewById<ImageView>(R.id.profile_avatar_input)
		avatar.setPadding(0, 0, 0, 0)
		avatar.imageTintList = null
		avatar.setImageURI(uri)
	}

	private fun showDefaultAvatar() {
		val avatar = findViewById<ImageView>(R.id.profile_avatar_input)
		val padding = (30 * resources.displayMetrics.density).toInt()
		avatar.setPadding(padding, padding, padding, padding)
		avatar.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
		avatar.setImageResource(R.drawable.ic_profile)
	}

	private fun showAvatarError() {
		clearPendingAvatar()
		showStoredAvatar(originalAvatarPath)
		Toast.makeText(this, R.string.profile_avatar_error, Toast.LENGTH_SHORT).show()
	}

	private fun discardChangesAndFinish() {
		clearPendingAvatar()
		clearCameraFile()
		finish()
	}

	private fun clearPendingAvatar() {
		pendingAvatarFilePath?.let { File(it).delete() }
		pendingAvatarFilePath = null
		pendingAvatarUri = null
	}

	private fun clearCameraFile() {
		cameraFilePath?.let { File(it).delete() }
		cameraFilePath = null
		cameraUri = null
	}

	companion object {
		private const val STATE_PENDING_AVATAR_URI = "profile.pending_avatar_uri"
		private const val STATE_PENDING_AVATAR_PATH = "profile.pending_avatar_path"
		private const val STATE_CAMERA_URI = "profile.camera_uri"
		private const val STATE_CAMERA_PATH = "profile.camera_path"
		private const val STATE_ORIGINAL_AVATAR_PATH = "profile.original_avatar_path"
	}
}
