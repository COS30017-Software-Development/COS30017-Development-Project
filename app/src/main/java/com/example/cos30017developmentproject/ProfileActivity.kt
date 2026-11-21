package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.auth.UserRepository
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
		}
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
