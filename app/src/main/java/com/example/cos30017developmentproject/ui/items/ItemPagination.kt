package com.example.cos30017developmentproject.ui.items

/** In-memory search and pagination, independent of Android views. */
class ItemPagination<T>(
    private val items: List<T>,
    private val pageSize: Int = 8,
    private val searchableText: (T) -> String
) {
    init { require(pageSize > 0) }

    private var filtered = items
    var pageIndex = 0
        private set
    val resultCount get() = filtered.size
    val pageCount get() = maxOf(1, (resultCount + pageSize - 1) / pageSize)
    val visibleItems get() = filtered.drop(pageIndex * pageSize).take(pageSize)

    fun search(query: String) {
        filtered = items.filter { searchableText(it).contains(query.trim(), ignoreCase = true) }
        pageIndex = 0
    }

    fun goToPage(index: Int) {
        pageIndex = index.coerceIn(0, pageCount - 1)
    }
}
