package com.example.cos30017developmentproject.ui.items

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.R
import com.example.cos30017developmentproject.ItemDetailsActivity
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import com.example.cos30017developmentproject.ui.home.ItemCardAdapter
import com.example.cos30017developmentproject.ui.home.ItemCard
import com.example.cos30017developmentproject.ui.home.toItemCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class ItemFilter { ALL, LOST, FOUND }

class ItemsPage(
	private val view: View,
	window: Window,
	savedState: Bundle?,
	private val repository: FirestoreItemRepository,
	private val lifecycleOwner: LifecycleOwner
) {
	private val insetsController = WindowCompat.getInsetsController(window, view)
	private val search = view.findViewById<EditText>(R.id.items_search)
	private val list = view.findViewById<RecyclerView>(R.id.items_list)
	private val toggle = view.findViewById<ImageButton>(R.id.toggle_item_layout)
	private val previous = view.findViewById<ImageButton>(R.id.previous_page)
	private val next = view.findViewById<ImageButton>(R.id.next_page)
	private val adapter = ItemCardAdapter { item ->
		view.context.startActivity(Intent(view.context, ItemDetailsActivity::class.java).apply {
			putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, item.id)
			putExtra(
				ItemDetailsActivity.EXTRA_ITEM_TYPE,
				if (item.isFound) ItemDetailsActivity.TYPE_FOUND else ItemDetailsActivity.TYPE_LOST
			)
		})
	}
	private val layout = GridLayoutManager(view.context, 2)
	private var pagination = ItemPagination<ItemCard>(emptyList()) { "${it.title}\n${it.description}" }
	private var listMode = savedState?.getBoolean("items.listMode") ?: false
	private var filter = savedState?.getString("items.filter")?.let(ItemFilter::valueOf) ?: ItemFilter.ALL
	private var pendingPageIndex: Int? = savedState?.getInt("items.page")
	private var itemsJob: Job? = null

	init {
		list.layoutManager = layout
		list.adapter = adapter
		search.setText(savedState?.getString("items.query").orEmpty())
		search.doAfterTextChanged {
			pagination.search(it.toString())
			render()
		}
		search.setOnEditorActionListener { _, action, _ ->
			if (action == EditorInfo.IME_ACTION_SEARCH) {
				insetsController.hide(WindowInsetsCompat.Type.ime())
				search.clearFocus()
				true
			} else false
		}
		toggle.setOnClickListener {
			listMode = !listMode
			render()
		}
		previous.setOnClickListener { changePage(-1) }
		next.setOnClickListener { changePage(1) }
		observeItems()
	}

	fun setFilter(newFilter: ItemFilter) {
		if (filter == newFilter) return
		filter = newFilter
		observeItems()
	}

	fun focusSearch() {
		search.requestFocus()
		search.post { insetsController.show(WindowInsetsCompat.Type.ime()) }
	}

	fun saveState(state: Bundle) {
		state.putString("items.query", search.text.toString())
		state.putBoolean("items.listMode", listMode)
		state.putInt("items.page", pagination.pageIndex)
		state.putString("items.filter", filter.name)
	}

	private fun observeItems() {
		itemsJob?.cancel()
		val context = view.context
		itemsJob = lifecycleOwner.lifecycleScope.launch {
			repository.observeItems()
				.catch { error ->
					Log.e(TAG, "Unable to load items page", error)
					Toast.makeText(context, R.string.items_load_failed, Toast.LENGTH_SHORT).show()
					emit(emptyList())
				}
				.map { items ->
					items.filter { item ->
						when (filter) {
							ItemFilter.ALL -> true
							ItemFilter.LOST -> item.type == FirestoreItemRepository.TYPE_LOST
							ItemFilter.FOUND -> item.type == FirestoreItemRepository.TYPE_FOUND
						}
					}.map { it.toItemCard(context) }
				}
				.collect { items ->
					pagination = ItemPagination(items) { "${it.title}\n${it.description}" }
					pagination.search(search.text.toString())
					pendingPageIndex?.let {
						pagination.goToPage(it)
						pendingPageIndex = null
					}
					render()
				}
		}
	}

	private fun changePage(offset: Int) {
		pagination.goToPage(pagination.pageIndex + offset)
		render()
	}

	private fun render() {
		val context = view.context
		val config = context.resources.configuration
		layout.spanCount = if (listMode || config.screenWidthDp < 360 || config.fontScale > 1.3f) 1 else 2
		adapter.setListMode(listMode)
		adapter.submitList(pagination.visibleItems)
		list.scrollToPosition(0)
		toggle.setImageResource(if (listMode) R.drawable.ic_view_grid else R.drawable.ic_view_list)
		toggle.contentDescription = context.getString(if (listMode) R.string.switch_to_grid else R.string.switch_to_list)
		view.findViewById<TextView>(R.id.items_result_count).text = context.resources.getQuantityString(
			R.plurals.sample_item_count, pagination.resultCount, pagination.resultCount
		)
		view.findViewById<TextView>(R.id.page_label).text = context.getString(
			R.string.page_label, pagination.pageIndex + 1, pagination.pageCount
		)
		val hasResults = pagination.resultCount > 0
		list.isVisible = hasResults
		view.findViewById<View>(R.id.items_empty).isVisible = !hasResults
		view.findViewById<View>(R.id.items_pagination).isVisible = hasResults
		previous.isEnabled = pagination.pageIndex > 0
		next.isEnabled = pagination.pageIndex < pagination.pageCount - 1
		listOf(previous, next).forEach { it.alpha = if (it.isEnabled) 1f else 0.35f }
	}

	private companion object {
		const val TAG = "ItemsPage"
	}
}
