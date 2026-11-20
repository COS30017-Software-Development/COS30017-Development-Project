package com.example.cos30017developmentproject.ui.items

import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.R
import com.example.cos30017developmentproject.ui.home.ItemCardAdapter
import com.example.cos30017developmentproject.ui.home.SampleItem

class ItemsPage(private val view: View, window: Window, savedState: Bundle?) {
	private val insetsController = WindowCompat.getInsetsController(window, view)
	private val search = view.findViewById<EditText>(R.id.items_search)
	private val list = view.findViewById<RecyclerView>(R.id.items_list)
	private val toggle = view.findViewById<ImageButton>(R.id.toggle_item_layout)
	private val previous = view.findViewById<ImageButton>(R.id.previous_page)
	private val next = view.findViewById<ImageButton>(R.id.next_page)
	private val adapter = ItemCardAdapter()
	private val layout = GridLayoutManager(view.context, 2)
	private val pagination = ItemPagination(SampleItem.examples) {
		"${view.context.getString(it.title)}\n${view.context.getString(it.description)}"
	}
	private var listMode = savedState?.getBoolean("items.listMode") ?: false

	init {
		list.layoutManager = layout
		list.adapter = adapter
		search.setText(savedState?.getString("items.query").orEmpty())
		pagination.search(search.text.toString())
		pagination.goToPage(savedState?.getInt("items.page") ?: 0)
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
		render()
	}

	fun focusSearch() {
		search.requestFocus()
		search.post { insetsController.show(WindowInsetsCompat.Type.ime()) }
	}

	fun saveState(state: Bundle) {
		state.putString("items.query", search.text.toString())
		state.putBoolean("items.listMode", listMode)
		state.putInt("items.page", pagination.pageIndex)
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
}
