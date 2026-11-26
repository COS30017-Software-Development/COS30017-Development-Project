package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import com.example.cos30017developmentproject.data.remote.FirestoreNotification
import com.example.cos30017developmentproject.data.remote.FirestoreNotificationRepository
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.ui.home.ItemCardAdapter
import com.example.cos30017developmentproject.ui.home.toItemCard
import com.example.cos30017developmentproject.ui.items.ItemFilter
import com.example.cos30017developmentproject.ui.items.ItemsPage
import com.example.cos30017developmentproject.ui.notifications.NotificationsPage
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
	private lateinit var drawer: DrawerLayout
	private lateinit var bottomNavigation: BottomNavigationView
	private lateinit var sideNavigation: NavigationView
	private lateinit var itemsPage: ItemsPage
	private lateinit var notificationsPage: NotificationsPage
	private lateinit var backCallback: OnBackPressedCallback
	private lateinit var firestoreRepository: FirestoreItemRepository
	private lateinit var notificationRepository: FirestoreNotificationRepository
	private var unreadCountJob: Job? = null
	private val firebaseAuth by lazy { FirebaseAuth.getInstance() }
	private val authStateListener = FirebaseAuth.AuthStateListener { refreshUnreadCount() }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_main)
		drawer = findViewById(R.id.main)
		bottomNavigation = findViewById(R.id.bottom_navigation)
		bottomNavigation.isItemActiveIndicatorEnabled = false
		sideNavigation = findViewById(R.id.side_navigation)
		firestoreRepository = FirestoreItemRepository()
		notificationRepository = FirestoreNotificationRepository()
		itemsPage = ItemsPage(findViewById(R.id.items_page), window, savedInstanceState, firestoreRepository, this)
		notificationsPage = NotificationsPage(
			findViewById(R.id.notifications_page),
			notificationRepository,
			this,
			::openNotificationDetails
		)
		ViewCompat.setOnApplyWindowInsetsListener(drawer) { _, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())            // DrawerLayout lays out children without respecting its own padding.
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
				context, if (config.screenWidthDp >= 360 && config.fontScale <= 1.3f) 2 else 1
			)
			val itemAdapter = ItemCardAdapter { item ->
				startActivity(Intent(this@MainActivity, ItemDetailsActivity::class.java).apply {
					putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, item.id)
					putExtra(
						ItemDetailsActivity.EXTRA_ITEM_TYPE,
						if (item.isFound) ItemDetailsActivity.TYPE_FOUND else ItemDetailsActivity.TYPE_LOST
					)
				})
			}
			adapter = itemAdapter
			lifecycleScope.launch {
				firestoreRepository.observeItems()
					.catch { error ->
						Log.e(TAG, "Unable to load home items", error)
						Toast.makeText(this@MainActivity, R.string.items_load_failed, Toast.LENGTH_SHORT).show()
						emit(emptyList())
					}
					.collect { items ->
						val cards = items.map { it.toItemCard(this@MainActivity) }.take(4)
						itemAdapter.submitList(cards)
						this@MainActivity.findViewById<View>(R.id.home_items_empty).isVisible = cards.isEmpty()
					}
			}
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
				R.id.nav_home -> {
					showPage(false)
					true
				}

				R.id.nav_items -> {
					showItems(ItemFilter.ALL)
					true
				}

				R.id.nav_post -> {
					openPost()
					false
				}

				R.id.nav_profile -> {
					openProfile()
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
			} else if (item.itemId == R.id.drawer_items) {
				showItems(ItemFilter.ALL)
			} else if (item.itemId == R.id.drawer_post) {
				openPost()
			} else if (item.itemId == R.id.drawer_my_posts) {
				openMyPosts()
			} else if (item.itemId == R.id.drawer_my_claims) {
				openMyClaims()
			} else if (item.itemId == R.id.drawer_notifications) {
				openNotifications()
			} else if (item.itemId == R.id.drawer_profile) {
				openProfile()
			} else showPreviewMessage(item.title.toString())
			false
		}
		bottomNavigation.selectedItemId = intent.getIntExtra(
			EXTRA_SELECTED_NAVIGATION, savedInstanceState?.getInt("navigation.page") ?: R.id.nav_home
		)
		if (savedInstanceState?.getBoolean("navigation.notifications") == true) openNotifications()
		drawer.closeDrawer(GravityCompat.START, false)
	}

	override fun onStart() {
		super.onStart()
		firebaseAuth.addAuthStateListener(authStateListener)
		refreshUnreadCount()
	}

	override fun onStop() {
		firebaseAuth.removeAuthStateListener(authStateListener)
		unreadCountJob?.cancel()
		unreadCountJob = null
		super.onStop()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		itemsPage.saveState(outState)
		outState.putInt("navigation.page", bottomNavigation.selectedItemId)
		outState.putBoolean("navigation.notifications", findViewById<View>(R.id.notifications_page).isVisible)
		super.onSaveInstanceState(outState)
	}

	private fun showPage(showItems: Boolean) {
		findViewById<View>(R.id.home_scroll).isVisible = !showItems
		findViewById<View>(R.id.items_page).isVisible = showItems
		findViewById<View>(R.id.notifications_page).isVisible = false
		notificationsPage.stopObserving()
		sideNavigation.menu.findItem(R.id.drawer_home).isChecked = !showItems
		if (!showItems) sideNavigation.setCheckedItem(R.id.drawer_home)
		WindowCompat.getInsetsController(window, drawer).hide(WindowInsetsCompat.Type.ime())
		currentFocus?.clearFocus()
		updateBackCallback()
	}

	private fun showItems(filter: ItemFilter) {
		itemsPage.setFilter(filter)
		showPage(true)
		sideNavigation.setCheckedItem(R.id.drawer_items)
	}

	private fun updateBackCallback() {
		backCallback.isEnabled = drawer.isDrawerOpen(GravityCompat.START) ||
			findViewById<View>(R.id.items_page).isVisible ||
			findViewById<View>(R.id.notifications_page).isVisible
	}

	private fun scrollHomeToTop() {
		findViewById<NestedScrollView>(R.id.home_scroll).smoothScrollTo(0, 0)
	}

	private fun openPost() {
		bottomNavigation.selectedItemId = R.id.nav_home
		if (SessionManager(this).userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
		} else {
			startActivity(Intent(this, AddItemActivity::class.java))
		}
	}

	private fun openMyPosts() {
		bottomNavigation.selectedItemId = R.id.nav_home
		if (SessionManager(this).userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
		} else {
			startActivity(Intent(this, MyPostsActivity::class.java))
		}
	}

	private fun openMyClaims() {
		bottomNavigation.selectedItemId = R.id.nav_home
		if (SessionManager(this).userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
		} else {
			startActivity(Intent(this, MyClaimsActivity::class.java))
		}
	}

	private fun openNotifications() {
		if (SessionManager(this).userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
		} else {
			findViewById<View>(R.id.home_scroll).isVisible = false
			findViewById<View>(R.id.items_page).isVisible = false
			findViewById<View>(R.id.notifications_page).isVisible = true
			notificationsPage.startObserving()
			sideNavigation.setCheckedItem(R.id.drawer_notifications)
			updateBackCallback()
		}
	}

	private fun openNotificationDetails(notification: FirestoreNotification) {
		startActivity(Intent(this, ItemDetailsActivity::class.java).apply {
			putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, notification.itemId)
			putExtra(
				ItemDetailsActivity.EXTRA_ITEM_TYPE,
				if (notification.itemType == FirestoreItemRepository.TYPE_FOUND) {
					ItemDetailsActivity.TYPE_FOUND
				} else {
					ItemDetailsActivity.TYPE_LOST
				}
			)
		})
	}

	private fun refreshUnreadCount() {
		unreadCountJob?.cancel()
		if (firebaseAuth.currentUser == null) {
			updateUnreadBadge(0)
			return
		}
		unreadCountJob = lifecycleScope.launch {
			notificationRepository.observeUnreadCount()
				.catch { error ->
					Log.e(TAG, "Unable to load unread notification count", error)
					emit(0)
				}
				.collect(::updateUnreadBadge)
		}
	}

	private fun updateUnreadBadge(count: Int) {
		val badge = sideNavigation.menu.findItem(R.id.drawer_notifications)
			?.actionView?.findViewById<View>(R.id.notification_badge_dot)
		badge?.isVisible = count > 0
	}

	private fun openProfile() {
		bottomNavigation.selectedItemId = R.id.nav_home
		if (SessionManager(this).userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
		} else {
			startActivity(Intent(this, ProfileActivity::class.java))
		}
	}

	private fun showPreviewMessage(destination: String) {
		Toast.makeText(this, getString(R.string.preview_message, destination), Toast.LENGTH_SHORT)
			.show()
	}

	companion object {
		const val EXTRA_SELECTED_NAVIGATION = "selected_navigation"
		private const val TAG = "MainActivity"
	}
}
