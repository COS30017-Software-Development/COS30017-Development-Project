package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.ui.home.ItemCardAdapter
import com.example.cos30017developmentproject.ui.home.SampleItem
import com.example.cos30017developmentproject.ui.items.ItemsPage
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {
	private lateinit var drawer: DrawerLayout
	private lateinit var bottomNavigation: BottomNavigationView
	private lateinit var sideNavigation: NavigationView
	private lateinit var itemsPage: ItemsPage
	private lateinit var backCallback: OnBackPressedCallback

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_main)
		drawer = findViewById(R.id.main)
		bottomNavigation = findViewById(R.id.bottom_navigation)
		bottomNavigation.isItemActiveIndicatorEnabled = false
		sideNavigation = findViewById(R.id.side_navigation)
		itemsPage = ItemsPage(findViewById(R.id.items_page), window, savedInstanceState)
		ViewCompat.setOnApplyWindowInsetsListener(drawer) { _, insets ->
			val bars =
				insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			// DrawerLayout lays out children without respecting its own padding.
			findViewById<View>(R.id.main_content).setPadding(
				bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom)
			)
			sideNavigation.setPadding(0, bars.top, 0, bars.bottom)
			WindowInsetsCompat.CONSUMED
		}
		ViewCompat.requestApplyInsets(drawer)

		findViewById<RecyclerView>(R.id.item_list).apply {
			val config = resources.configuration
			layoutManager = GridLayoutManager(
				context,
				if (config.screenWidthDp >= 360 && config.fontScale <= 1.3f) 2 else 1
			)
			adapter = ItemCardAdapter(SampleItem.examples.take(4))
		}
		backCallback = object : OnBackPressedCallback(false) {
			override fun handleOnBackPressed() {
				if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START)
				else bottomNavigation.selectedItemId = R.id.nav_home
			}
		}
		onBackPressedDispatcher.addCallback(this, backCallback)
		drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
			override fun onDrawerOpened(drawerView: View) {
				updateBackCallback()
			}

			override fun onDrawerClosed(drawerView: View) {
				updateBackCallback()
			}
		})
		findViewById<ImageButton>(R.id.menu_button).setOnClickListener {
			drawer.openDrawer(
				GravityCompat.START
			)
		}
		findViewById<EditText>(R.id.home_search).apply {
			isFocusable = false
			isCursorVisible = false
			setOnClickListener {
				bottomNavigation.selectedItemId = R.id.nav_items
				itemsPage.focusSearch()
			}
		}
		bottomNavigation.setOnItemSelectedListener { item ->
			when (item.itemId) {
				R.id.nav_home, R.id.nav_items -> {
					showPage(item.itemId == R.id.nav_items)
					true
				}

				R.id.nav_post, R.id.nav_profile -> {
					openLogin()
					false
				}

				else -> {
					showPreviewMessage(item.title.toString())
					false
				}
			}
		}
		bottomNavigation.setOnItemReselectedListener {
			if (it.itemId == R.id.nav_home) scrollHomeToTop()
		}
		sideNavigation.setNavigationItemSelectedListener { item ->
			drawer.closeDrawer(GravityCompat.START)
			if (item.itemId == R.id.drawer_home) {
				bottomNavigation.selectedItemId = R.id.nav_home
				scrollHomeToTop()
			} else if (item.itemId == R.id.drawer_post || item.itemId == R.id.drawer_profile) {
				openLogin()
			} else showPreviewMessage(item.title.toString())
			false
		}
		bottomNavigation.selectedItemId =
			savedInstanceState?.getInt("navigation.page") ?: R.id.nav_home
		drawer.closeDrawer(GravityCompat.START, false)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		itemsPage.saveState(outState)
		outState.putInt("navigation.page", bottomNavigation.selectedItemId)
		super.onSaveInstanceState(outState)
	}

	private fun showPage(showItems: Boolean) {
		findViewById<View>(R.id.home_scroll).isVisible = !showItems
		findViewById<View>(R.id.items_page).isVisible = showItems
		sideNavigation.menu.findItem(R.id.drawer_home).isChecked = !showItems
		if (!showItems) sideNavigation.setCheckedItem(R.id.drawer_home)
		WindowCompat.getInsetsController(window, drawer).hide(WindowInsetsCompat.Type.ime())
		currentFocus?.clearFocus()
		updateBackCallback()
	}

	private fun updateBackCallback() {
		backCallback.isEnabled =
			drawer.isDrawerOpen(GravityCompat.START) || findViewById<View>(R.id.items_page).isVisible
	}

	private fun scrollHomeToTop() {
		findViewById<NestedScrollView>(R.id.home_scroll).smoothScrollTo(0, 0)
	}

	private fun openLogin() {
		// Authentication is not connected yet; protected entries open the login UI.
		bottomNavigation.selectedItemId = R.id.nav_home
		startActivity(Intent(this, AuthActivity::class.java))
	}

	private fun showPreviewMessage(destination: String) {
		Toast.makeText(this, getString(R.string.preview_message, destination), Toast.LENGTH_SHORT)
			.show()
	}
}
