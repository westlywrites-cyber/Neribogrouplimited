package com.westly.opay.feature.welcome

const val WELCOME_PAGE_COUNT = 5

/** Pure page logic for the welcome slides. The pager reports its page; this decides what the buttons do. */
data class WelcomePagerState(
    val page: Int = 0,
    val count: Int = WELCOME_PAGE_COUNT
) {
    init {
        require(count > 0) { "count must be positive" }
        require(page in 0 until count) { "page out of range" }
    }

    val lastPage: Int get() = count - 1
    val isLast: Boolean get() = page == lastPage
    val showSkip: Boolean get() = !isLast
    val canGoBack: Boolean get() = page > 0

    fun next(): WelcomePagerState = copy(page = (page + 1).coerceAtMost(lastPage))

    fun skip(): WelcomePagerState = copy(page = lastPage)

    fun back(): WelcomePagerState = copy(page = (page - 1).coerceAtLeast(0))
}
