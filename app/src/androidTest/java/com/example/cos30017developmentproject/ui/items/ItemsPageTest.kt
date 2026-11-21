package com.example.cos30017developmentproject.ui.items

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cos30017developmentproject.MainActivity
import com.example.cos30017developmentproject.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercise real view listeners without Espresso's legacy platform input injector. */
@RunWith(AndroidJUnit4::class)
class ItemsPageTest {
	private fun ActivityScenario<MainActivity>.checkUi(action: (MainActivity) -> Unit) {
		InstrumentationRegistry.getInstrumentation().waitForIdleSync()
		onActivity { action(it) }
	}

	private fun MainActivity.click(id: Int) {
		findViewById<View>(id).performClick()
	}

	private fun MainActivity.label(id: Int) = findViewById<TextView>(id).text.toString()

	@Test
	fun navigationShowsAllRoomItems() {
		ActivityScenario.launch(MainActivity::class.java).use { scenario ->
			scenario.checkUi {
				assertEquals(4, it.findViewById<RecyclerView>(R.id.item_list).adapter!!.itemCount)
				it.click(R.id.nav_items)
				assertTrue(it.findViewById<View>(R.id.items_page).isShown)
				assertEquals("8 items", it.label(R.id.items_result_count))
				assertEquals("Page 1 of 1", it.label(R.id.page_label))
				assertFalse(it.findViewById<View>(R.id.previous_page).isEnabled)
				assertFalse(it.findViewById<View>(R.id.next_page).isEnabled)
			}
			scenario.checkUi {
				val adapter = it.findViewById<RecyclerView>(R.id.items_list).adapter as com.example.cos30017developmentproject.ui.home.ItemCardAdapter
				assertEquals(8, adapter.itemCount)
				assertEquals("Locker keys", adapter.currentList.first().title)
			}
		}
	}

	@Test
	fun searchMatchesDescriptionsAndHandlesNoResults() {
		ActivityScenario.launch(MainActivity::class.java).use { scenario ->
			scenario.checkUi {
				it.click(R.id.nav_items)
				val search = it.findViewById<EditText>(R.id.items_search)
				search.setText("  LIBRARY  ")
				assertEquals("2 items", it.label(R.id.items_result_count))
				assertEquals("Page 1 of 1", it.label(R.id.page_label))
				search.setText("not-an-existing-item")
				assertTrue(it.findViewById<View>(R.id.items_empty).isShown)
				assertFalse(it.findViewById<View>(R.id.items_pagination).isShown)
				search.setText("")
				assertEquals("8 items", it.label(R.id.items_result_count))
			}
		}
	}

	@Test
	fun layoutAndPageSurviveRecreationAndBackReturnsHome() {
		ActivityScenario.launch(MainActivity::class.java).use { scenario ->
			scenario.checkUi {
				it.click(R.id.nav_items)
				it.click(R.id.toggle_item_layout)
				assertEquals(
					it.getString(R.string.switch_to_grid),
					it.findViewById<View>(R.id.toggle_item_layout).contentDescription
				)
				val list = it.findViewById<RecyclerView>(R.id.items_list)
				assertEquals(1, (list.layoutManager as GridLayoutManager).spanCount)
				assertEquals(1, list.adapter!!.getItemViewType(0))
			}
			scenario.recreate()
			scenario.checkUi {
				assertTrue(it.findViewById<View>(R.id.items_page).isShown)
				assertEquals("Page 1 of 1", it.label(R.id.page_label))
				assertEquals(
					it.getString(R.string.switch_to_grid),
					it.findViewById<View>(R.id.toggle_item_layout).contentDescription
				)
				it.click(R.id.toggle_item_layout)
				assertEquals("Page 1 of 1", it.label(R.id.page_label))
				assertEquals(
					it.getString(R.string.switch_to_list),
					it.findViewById<View>(R.id.toggle_item_layout).contentDescription
				)
				it.onBackPressedDispatcher.onBackPressed()
				assertTrue(it.findViewById<View>(R.id.home_scroll).isShown)
				assertFalse(it.findViewById<View>(R.id.items_page).isShown)
			}
		}
	}

	@Test
	fun navigationLabelsStayAboveTheSystemNavigationBar() {
		ActivityScenario.launch(MainActivity::class.java).use { scenario ->
			scenario.checkUi {
				it.click(R.id.nav_items)
				val root = it.findViewById<View>(R.id.main)
				val item = it.findViewById<View>(R.id.nav_items)
				val label = item.findViewById<TextView>(com.google.android.material.R.id.navigation_bar_item_large_label_view)
				val location = IntArray(2)
				label.getLocationOnScreen(location)
				val bottomInset = ViewCompat.getRootWindowInsets(root)!!
					.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
				assertTrue(label.isShown)
				assertTrue(location[1] + label.height <= root.height - bottomInset)
			}
		}
	}

	@Test
	fun drawerStartsHiddenAndClosesWithBackOnItemsPage() {
		ActivityScenario.launch(MainActivity::class.java).use { scenario ->
			val opened = CountDownLatch(1)
			val closed = CountDownLatch(1)
			scenario.checkUi {
				it.click(R.id.nav_items)
				val drawer = it.findViewById<DrawerLayout>(R.id.main)
				assertFalse(drawer.isDrawerOpen(GravityCompat.START))
				drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
					override fun onDrawerOpened(drawerView: View) {
						opened.countDown()
					}

					override fun onDrawerClosed(drawerView: View) {
						closed.countDown()
					}
				})
				it.click(R.id.menu_button)
			}
			assertTrue(opened.await(5, TimeUnit.SECONDS))
			scenario.checkUi { it.onBackPressedDispatcher.onBackPressed() }
			assertTrue(closed.await(5, TimeUnit.SECONDS))
			scenario.checkUi {
				assertTrue(it.findViewById<View>(R.id.items_page).isShown)
				assertFalse(
					it.findViewById<DrawerLayout>(R.id.main).isDrawerOpen(GravityCompat.START)
				)
			}
		}
	}
}
