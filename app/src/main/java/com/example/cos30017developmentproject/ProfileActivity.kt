package com.example.cos30017developmentproject

import android.content.Intent
import android.content.res.ColorStateList
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.auth.UserRepository
import com.example.cos30017developmentproject.auth.AvatarStorage
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
	private lateinit var sessionManager: SessionManager
	private lateinit var repository: UserRepository
	private var userId: Long? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		userId = sessionManager.userId
		if (userId == null) {
			openLogin()
			return
		}
		enableEdgeToEdge()
		setContentView(R.layout.activity_profile)
		repository = UserRepository(AppDatabase.getInstance(this).userDao())
		val drawer = findViewById<DrawerLayout>(R.id.profile_drawer)
		val sideNavigation = findViewById<NavigationView>(R.id.side_navigation)
		val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
		bottomNavigation.isItemActiveIndicatorEnabled = false
		bottomNavigation.selectedItemId = R.id.nav_profile
		ViewCompat.setAccessibilityHeading(findViewById(R.id.profile_title), true)
		ViewCompat.setOnApplyWindowInsetsListener(drawer) { _, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			findViewById<View>(R.id.profile_content).setPadding(bars.left, bars.top, bars.right, bars.bottom)
			sideNavigation.setPadding(0, bars.top, 0, bars.bottom)
			WindowInsetsCompat.CONSUMED
		}
		ViewCompat.requestApplyInsets(drawer)
		findViewById<ImageButton>(R.id.menu_button).setOnClickListener { drawer.openDrawer(GravityCompat.START) }
		findViewById<View>(R.id.profile_edit).setOnClickListener {
			startActivity(Intent(this, EditProfileActivity::class.java))
		}
		findViewById<View>(R.id.profile_logout).setOnClickListener {
			sessionManager.logout()
			openLogin()
		}
		bottomNavigation.setOnItemSelectedListener { item ->
			when (item.itemId) {
				R.id.nav_profile -> true
				R.id.nav_home, R.id.nav_items -> {
					openMain(item.itemId)
					false
				}
				else -> {
					showPreviewMessage(item.title.toString())
					false
				}
			}
		}
		sideNavigation.setNavigationItemSelectedListener { item ->
			drawer.closeDrawer(GravityCompat.START)
			when (item.itemId) {
				R.id.drawer_profile -> true
				R.id.drawer_home -> openMain(R.id.nav_home)
				R.id.drawer_lost, R.id.drawer_found, R.id.drawer_search -> openMain(R.id.nav_items)
				else -> showPreviewMessage(item.title.toString())
			}
			false
		}
		sideNavigation.setCheckedItem(R.id.drawer_profile)
	}

	override fun onResume() {
		super.onResume()
		loadProfile()
	}

	private fun loadProfile() {
		val id = userId ?: return
		lifecycleScope.launch {
			val user = repository.getUser(id)
			if (user == null) {
				sessionManager.logout()
				openLogin()
				return@launch
			}
			findViewById<TextView>(R.id.profile_name).text = user.username
			findViewById<TextView>(R.id.profile_email).text = user.email
			findViewById<TextView>(R.id.profile_student_id).text = user.studentId
			showAvatar(user.avatarPath)
		}
	}

	private fun showAvatar(path: String?) {
		val avatar = findViewById<ImageView>(R.id.profile_avatar)
		val file = AvatarStorage.fileFor(this, path)
		if (file == null) {
			val padding = (20 * resources.displayMetrics.density).toInt()
			avatar.setPadding(padding, padding, padding, padding)
			avatar.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
			avatar.setImageResource(R.drawable.ic_profile)
			avatar.setOnClickListener(null)
			avatar.isClickable = false
		} else {
			avatar.setPadding(0, 0, 0, 0)
			avatar.imageTintList = null
			val uri = Uri.fromFile(file)
			avatar.setImageURI(uri)
			avatar.setOnClickListener { showAvatarViewer(uri, avatar) }
			avatar.isClickable = true
		}
	}

	private fun showAvatarViewer(uri: Uri, sourceAvatar: View) {
		val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
		val content = layoutInflater.inflate(R.layout.dialog_avatar_viewer, null)
		val scrim = content.findViewById<View>(R.id.avatar_viewer_scrim)
		val viewerImage = content.findViewById<ImageView>(R.id.avatar_viewer_image)
		val sourceLocation = IntArray(2)
		sourceAvatar.getLocationOnScreen(sourceLocation)
		val sourceSize = minOf(sourceAvatar.width, sourceAvatar.height)
		viewerImage.setImageURI(uri)
		dialog.setCancelable(false)
		val closeViewer = {
			closeAvatarViewer(dialog, scrim, viewerImage, sourceLocation, sourceSize)
		}
		content.setOnClickListener { closeViewer() }
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			val backCallback = OnBackInvokedCallback { closeViewer() }
			dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(
				OnBackInvokedDispatcher.PRIORITY_DEFAULT,
				backCallback
			)
			dialog.setOnDismissListener {
				dialog.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
			}
		} else {
			dialog.setOnKeyListener { _, keyCode, event ->
				if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
					if (event.action == android.view.KeyEvent.ACTION_UP) closeViewer()
					true
				} else false
				}
		}
		dialog.setContentView(content)
		dialog.show()
		dialog.window?.apply {
			setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
			setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
			clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
		}
		content.post {
			val expandedSize = (content.width * AVATAR_VIEWER_WIDTH_RATIO).toInt()
			val expandedX = (content.width - expandedSize) / 2f
			val expandedY = (content.height - expandedSize) / 2f
			viewerImage.layoutParams = FrameLayout.LayoutParams(sourceSize, sourceSize)
			viewerImage.x = sourceLocation[0].toFloat()
			viewerImage.y = sourceLocation[1].toFloat()
			viewerImage.pivotX = 0f
			viewerImage.pivotY = 0f
			scrim.alpha = 0f
			viewerImage.animate()
				.x(expandedX)
				.y(expandedY)
				.scaleX(expandedSize.toFloat() / sourceSize)
				.scaleY(expandedSize.toFloat() / sourceSize)
				.setDuration(AVATAR_VIEWER_ANIMATION_DURATION)
				.start()
			scrim.animate().alpha(1f).setDuration(AVATAR_VIEWER_ANIMATION_DURATION).start()
		}
	}

	private fun closeAvatarViewer(
		dialog: Dialog,
		scrim: View,
		viewerImage: ImageView,
		sourceLocation: IntArray,
		sourceSize: Int
	) {
		if (!dialog.isShowing || viewerImage.getTag(R.id.avatar_viewer_image) == true) return
		viewerImage.setTag(R.id.avatar_viewer_image, true)
		viewerImage.animate()
			.x(sourceLocation[0].toFloat())
			.y(sourceLocation[1].toFloat())
			.scaleX(1f)
			.scaleY(1f)
			.setDuration(AVATAR_VIEWER_ANIMATION_DURATION)
			.withEndAction { dialog.dismiss() }
			.start()
		scrim.animate().alpha(0f).setDuration(AVATAR_VIEWER_ANIMATION_DURATION).start()
	}

	private companion object {
		const val AVATAR_VIEWER_WIDTH_RATIO = 0.9f
		const val AVATAR_VIEWER_ANIMATION_DURATION = 280L
	}

	private fun openMain(navigationId: Int) {
		startActivity(Intent(this, MainActivity::class.java).apply {
			putExtra(MainActivity.EXTRA_SELECTED_NAVIGATION, navigationId)
			addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
		})
		finish()
	}

	private fun showPreviewMessage(destination: String) {
		Toast.makeText(this, getString(R.string.preview_message, destination), Toast.LENGTH_SHORT).show()
	}

	private fun openLogin() {
		startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
		finish()
	}
}
