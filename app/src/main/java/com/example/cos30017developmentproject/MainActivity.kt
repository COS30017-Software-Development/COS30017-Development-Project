package com.example.cos30017developmentproject

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.ui.home.ItemCardAdapter
import com.example.cos30017developmentproject.ui.home.SampleItem
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_main)
		val drawer = findViewById<DrawerLayout>(R.id.main)
		ViewCompat.setOnApplyWindowInsetsListener(drawer) { view, insets ->
			val bars =
				insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
			// Child views must not apply the system-bar padding a second time.
			WindowInsetsCompat.CONSUMED
		}
		ViewCompat.requestApplyInsets(drawer)

		val list = findViewById<RecyclerView>(R.id.item_list)
		list.layoutManager = GridLayoutManager(
			this,
			if (resources.configuration.screenWidthDp >= 360 && resources.configuration.fontScale <= 1.3f) 2 else 1
		)
		list.adapter = ItemCardAdapter(SampleItem.examples)

		val backCallback = object : OnBackPressedCallback(false) {
			override fun handleOnBackPressed() = drawer.closeDrawer(GravityCompat.START)
		}
		onBackPressedDispatcher.addCallback(this, backCallback)
		drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
			override fun onDrawerOpened(drawerView: android.view.View) {
				backCallback.isEnabled = true
			}

			override fun onDrawerClosed(drawerView: android.view.View) {
				backCallback.isEnabled = false
			}
		})
		findViewById<ImageButton>(R.id.menu_button).setOnClickListener {
			drawer.openDrawer(
				GravityCompat.START
			)
		}
		findViewById<android.widget.Button>(R.id.home_search).setOnClickListener {
			showPreviewMessage(
				getString(R.string.search)
			)
		}

		val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
		val sideNavigation = findViewById<NavigationView>(R.id.side_navigation)
		sideNavigation.setCheckedItem(R.id.drawer_home)
		bottomNavigation.setOnItemSelectedListener { item ->
			if (item.itemId == R.id.nav_home) {
				scrollHomeToTop()
				true
			} else {
				showPreviewMessage(item.title.toString())
				false
			}
		}
		bottomNavigation.setOnItemReselectedListener { scrollHomeToTop() }
		sideNavigation.setNavigationItemSelectedListener { item ->
			drawer.closeDrawer(GravityCompat.START)
			if (item.itemId == R.id.drawer_home) scrollHomeToTop()
			else showPreviewMessage(item.title.toString())
			// Keep Home selected until the other destinations exist.
			false
		}
		// The drawer starts hidden on every activity creation.
		drawer.closeDrawer(GravityCompat.START, false)
	}

	private fun scrollHomeToTop() {
		findViewById<NestedScrollView>(R.id.home_scroll).smoothScrollTo(0, 0)
	}

	private fun showPreviewMessage(destination: String) {
		Toast.makeText(this, getString(R.string.preview_message, destination), Toast.LENGTH_SHORT)
			.show()
	}
}
