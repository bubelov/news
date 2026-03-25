package org.vestifeed.entries

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import co.appreactor.feedk.AtomLinkRel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vestifeed.R
import org.vestifeed.anim.animateVisibilityChanges
import org.vestifeed.anim.showSmooth
import org.vestifeed.api.Api
import org.vestifeed.app.db
import org.vestifeed.auth.AuthFragment
import org.vestifeed.db.Conf
import org.vestifeed.db.ConfQueries
import org.vestifeed.db.EntriesAdapterRow
import org.vestifeed.db.Feed
import org.vestifeed.databinding.FragmentEntriesBinding
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.di.Di
import org.vestifeed.entry.EntryFragment
import org.vestifeed.feeds.FeedsFragment
import org.vestifeed.feeds.FeedsRepo
import org.vestifeed.navigation.AppFragment
import org.vestifeed.navigation.openUrl
import org.vestifeed.search.SearchFragment
import org.vestifeed.settings.SettingsFragment
import org.vestifeed.sync.Sync
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EntriesFragment : AppFragment() {

    private val filter: EntriesFilter? by lazy {
        arguments?.getParcelable(
            "filter",
            EntriesFilter::class.java,
        )
    }

    private val api by lazy { Di.get(Api::class.java) }
    private val entriesRepo by lazy { EntriesRepo(api, db()) }
    private val feedsRepo by lazy { FeedsRepo(api, db()) }
    private val sync by lazy { Sync(db(), feedsRepo, entriesRepo) }

    private val _state = MutableStateFlow<State>(State.LoadingCachedEntries)
    private val state = _state.asStateFlow()

    private var scrollToTopNextTime = false

    private var _binding: FragmentEntriesBinding? = null
    private val binding get() = _binding!!

    private val seenEntries = mutableSetOf<EntriesAdapter.Item>()

    private val snackbar by lazy {
        Snackbar.make(binding.root, "", Snackbar.LENGTH_SHORT).apply {
            anchorView = requireActivity().findViewById(R.id.bottomNav)
        }
    }

    private val adapter by lazy {
        EntriesAdapter(requireActivity()) { onListItemClick(it) }
            .apply { scrollToTopOnInsert() }
    }

    private val touchHelper: ItemTouchHelper? by lazy { createTouchHelper() }

    private val trackingListener = createTrackingListener()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return if (hasBackend()) {
            val intent = requireActivity().intent
            val sharedFeedUrl =
                (intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT))?.trim() ?: ""
            intent.removeExtra(Intent.EXTRA_TEXT)

            if (sharedFeedUrl.isNotBlank()) {
                parentFragmentManager.commit {
                    replace(
                        R.id.fragmentContainerView,
                        FeedsFragment::class.java,
                        bundleOf("url" to sharedFeedUrl),
                    )
                }
            }

            _binding = FragmentEntriesBinding.inflate(inflater, container, false)
            binding.root
        } else {
            parentFragmentManager.commit {
                replace(
                    R.id.fragmentContainerView,
                    AuthFragment::class.java,
                    null,
                )
            }
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (filter == null) {
            showErrorDialog(getString(R.string.required_argument_is_missing, "filter")) {
                requireActivity().finish()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.statusBars()).let {
                v.updatePadding(top = it.top)
            }
            insets
        }

        initSwipeRefresh()
        initList()

        viewLifecycleOwner.lifecycleScope.launch {
            refresh()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                state.collect { binding.setState(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            refresh()
        }
    }

    override fun onStop() {
        super.onStop()

        val currentState = state.value

        if (currentState is State.ShowingCachedEntries && currentState.conf.markScrolledEntriesAsRead) {
            setRead(
                entryIds = seenEntries.map { it.id },
                read = true,
            )

            seenEntries.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun hasBackend() = db().confQueries.select().backend.isNotBlank()

    private suspend fun refresh() {
        val conf = db().confQueries.select()
        val syncState = sync.state.value
        maybeStartupSync()
        updateState(conf, syncState)
    }

    private fun maybeStartupSync() {
        val conf = db().confQueries.select()
        if (!conf.initialSyncCompleted || (conf.syncOnStartup && !conf.syncedOnStartup)) {
            db().confQueries.update { it.copy(syncedOnStartup = true) }
            viewLifecycleOwner.lifecycleScope.launch {
                sync.run()
                refresh()
            }
        }
    }

    private suspend fun updateState(conf: Conf, syncState: Sync.State) {
        when (syncState) {
            is Sync.State.InitialSync -> _state.update { State.InitialSync(syncState.message) }

            else -> {
                val showBgProgress = when (syncState) {
                    is Sync.State.FollowUpSync -> syncState.args.syncEntries
                    else -> false
                }

                val scrollToTop = scrollToTopNextTime
                scrollToTopNextTime = false

                val rows: List<EntriesAdapterRow> = if (filter is EntriesFilter.BelongToFeed) {
                    entriesRepo.selectByFeedIdAndReadAndBookmarked(
                        feedId = (filter as EntriesFilter.BelongToFeed).feedId,
                        read = if (conf.showReadEntries) listOf(true, false) else listOf(false),
                        bookmarked = false,
                    ).first()
                } else {
                    val includeRead = (conf.showReadEntries || filter is EntriesFilter.Bookmarked)
                    val includeBookmarked = filter is EntriesFilter.Bookmarked

                    entriesRepo.selectByReadAndBookmarked(
                        read = if (includeRead) listOf(true, false) else listOf(false),
                        bookmarked = includeBookmarked,
                    ).first()
                }

                val sortedRows = when (conf.sortOrder) {
                    ConfQueries.SORT_ORDER_ASCENDING -> rows.sortedBy { it.published }
                    ConfQueries.SORT_ORDER_DESCENDING -> rows.sortedByDescending { it.published }
                    else -> throw Exception()
                }

                _state.update {
                    State.ShowingCachedEntries(
                        feed = if (filter is EntriesFilter.BelongToFeed) {
                            feedsRepo.selectById((filter as EntriesFilter.BelongToFeed).feedId)
                                .first()
                        } else {
                            null
                        },
                        entries = sortedRows.map { it.toItem(conf) },
                        showBackgroundProgress = showBgProgress,
                        scrollToTop = scrollToTop,
                        conf = conf,
                    )
                }
            }
        }
    }

    private fun onRetry() {
        viewLifecycleOwner.lifecycleScope.launch {
            sync.run()
            refresh()
        }
    }

    private fun onPullRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            sync.run()
            refresh()
        }
    }

    private fun saveConf(newConf: (Conf) -> Conf) {
        db().confQueries.update(newConf)
    }

    private fun changeSortOrder() {
        scrollToTopNextTime = true

        db().confQueries.update {
            val newSortOrder = when (it.sortOrder) {
                ConfQueries.SORT_ORDER_ASCENDING -> ConfQueries.SORT_ORDER_DESCENDING
                ConfQueries.SORT_ORDER_DESCENDING -> ConfQueries.SORT_ORDER_ASCENDING
                else -> throw Exception()
            }

            it.copy(sortOrder = newSortOrder)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            refresh()
        }
    }

    private fun setRead(entryIds: Collection<String>, read: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            entryIds.forEach {
                entriesRepo.updateReadAndReadSynced(
                    id = it,
                    read = read,
                    readSynced = false,
                )
            }

            sync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )

            refresh()
        }
    }

    private fun setBookmarked(entryId: String, bookmarked: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            entriesRepo.updateBookmarkedAndBookmaredSynced(
                id = entryId,
                bookmarked = bookmarked,
                bookmarkedSynced = false
            )

            sync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )

            refresh()
        }
    }

    private fun markAllAsRead() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val currentFilter = filter) {
                is EntriesFilter.Unread -> {
                    entriesRepo.updateReadByBookmarked(
                        read = true,
                        bookmarked = false,
                    )
                }

                is EntriesFilter.Bookmarked -> {
                    entriesRepo.updateReadByBookmarked(
                        read = true,
                        bookmarked = true,
                    )
                }

                is EntriesFilter.BelongToFeed -> {
                    entriesRepo.updateReadByFeedId(
                        read = true,
                        feedId = currentFilter.feedId,
                    )
                }

                null -> {

                }
            }

            sync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )

            refresh()
        }
    }

    private fun EntriesAdapterRow.toItem(conf: Conf): EntriesAdapter.Item {
        return EntriesAdapter.Item(
            id = id,
            showImage = extShowPreviewImages || conf.showPreviewImages,
            cropImage = conf.cropPreviewImages,
            imageUrl = extOpenGraphImageUrl,
            imageWidth = extOpenGraphImageWidth,
            imageHeight = extOpenGraphImageHeight,
            title = title,
            subtitle = "$feedTitle · ${DATE_TIME_FORMAT.format(published)}",
            summary = summary ?: "",
            read = extRead,
            openInBrowser = extOpenEntriesInBrowser,
            useBuiltInBrowser = conf.useBuiltInBrowser,
            links = links,
        )
    }

    private fun initSwipeRefresh() {
        binding.swipeRefresh.apply {
            when (filter) {
                is EntriesFilter.Unread, is EntriesFilter.BelongToFeed -> {
                    isEnabled = true
                    setOnRefreshListener { onPullRefresh() }
                }

                else -> isEnabled = false
            }
        }
    }

    private fun initList() {
        if (binding.list.adapter == null) {
            binding.list.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = this@EntriesFragment.adapter

                val listItemDecoration = CardListAdapterDecoration(
                    resources.getDimensionPixelSize(R.dimen.entries_cards_gap)
                )

                addItemDecoration(listItemDecoration)
            }

            touchHelper?.attachToRecyclerView(binding.list)
        }
    }

    private fun FragmentEntriesBinding.setState(state: State) {
        animateVisibilityChanges(
            views = listOf(toolbar, progress, message, retry, swipeRefresh),
            visibleViews = when (state) {
                is State.InitialSync -> listOf(toolbar, progress)
                is State.FailedToSync -> listOf(toolbar, retry)
                is State.LoadingCachedEntries -> listOf(toolbar, progress)
                is State.ShowingCachedEntries -> listOf(toolbar, swipeRefresh)
            },
        )

        updateToolbar(state)

        when (state) {
            is State.InitialSync -> {
                if (state.message.isNotEmpty()) {
                    message.showSmooth()
                    message.text = state.message
                }
            }

            is State.FailedToSync -> {
                retry.setOnClickListener { onRetry() }
                showErrorDialog(state.cause)
            }

            State.LoadingCachedEntries -> {}

            is State.ShowingCachedEntries -> {
                swipeRefresh.isRefreshing = state.showBackgroundProgress

                if (state.entries.isEmpty()) {
                    message.showSmooth()
                    message.text = getEmptyMessage()
                }

                seenEntries.clear()
                adapter.submitList(state.entries) { if (state.scrollToTop) scrollToTop() }

                if (
                    state.conf.markScrolledEntriesAsRead
                    && (filter is EntriesFilter.Unread || filter is EntriesFilter.BelongToFeed)
                ) {
                    binding.list.addOnScrollListener(trackingListener)
                } else {
                    binding.list.removeOnScrollListener(trackingListener)
                }
            }
        }
    }

    private fun updateToolbar(state: State) {
        binding.toolbar.apply {
            when (filter) {
                EntriesFilter.Bookmarked -> setTitle(R.string.bookmarks)
                EntriesFilter.Unread -> setTitle(R.string.unread)

                is EntriesFilter.BelongToFeed -> {
                    binding.toolbar.apply {
                        navigationIcon = DrawerArrowDrawable(context).also { it.progress = 1f }
                        setNavigationOnClickListener { parentFragmentManager.popBackStack() }
                    }

                    if (state is State.ShowingCachedEntries) {
                        title = state.feed?.title
                    }
                }

                null -> {}
            }

            updateSearchButton()
            updateShowReadEntriesButton(state)
            updateSortOrderButton(state)
            updateMarkAllAsReadButton()
            updateSettingsButton()
        }
    }

    private fun updateSearchButton() {
        binding.toolbar.menu!!.findItem(R.id.search).setOnMenuItemClickListener {
            parentFragmentManager.commit {
                replace(
                    R.id.fragmentContainerView,
                    SearchFragment::class.java,
                    null,
                )
                addToBackStack(null)
            }
            true
        }
    }

    private fun updateShowReadEntriesButton(state: State) {
        val button = binding.toolbar.menu!!.findItem(R.id.showOpenedEntries)
        button.isVisible = getShowReadEntriesButtonVisibility()

        if (state !is State.ShowingCachedEntries) {
            button.isVisible = false
            return
        }

        if (state.conf.showReadEntries) {
            button.setIcon(R.drawable.ic_baseline_visibility_24)
            button.title = getString(R.string.hide_read_news)
            touchHelper?.attachToRecyclerView(null)
        } else {
            button.setIcon(R.drawable.ic_baseline_visibility_off_24)
            button.title = getString(R.string.show_read_news)
            touchHelper?.attachToRecyclerView(binding.list)
        }

        button.setOnMenuItemClickListener {
            saveConf { it.copy(showReadEntries = !it.showReadEntries) }
            viewLifecycleOwner.lifecycleScope.launch {
                refresh()
            }
            true
        }
    }

    private fun updateSortOrderButton(state: State) {
        val button = binding.toolbar.menu.findItem(R.id.sort)

        if (state !is State.ShowingCachedEntries) {
            button.isVisible = false
            return
        } else {
            button.isVisible = true
        }

        when (state.conf.sortOrder) {
            ConfQueries.SORT_ORDER_ASCENDING -> {
                button.setIcon(R.drawable.ic_clock_forward)
                button.title = getString(R.string.show_newest_first)
            }

            ConfQueries.SORT_ORDER_DESCENDING -> {
                button.setIcon(R.drawable.ic_clock_back)
                button.title = getString(R.string.show_oldest_first)
            }
        }

        button.setOnMenuItemClickListener {
            changeSortOrder()
            true
        }
    }

    private fun updateMarkAllAsReadButton() {
        binding.toolbar.menu!!.findItem(R.id.markAllAsRead).setOnMenuItemClickListener {
            markAllAsRead()
            true
        }
    }

    private fun updateSettingsButton() {
        binding.toolbar.menu!!.findItem(R.id.settings).setOnMenuItemClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainerView, SettingsFragment::class.java, null)
                addToBackStack(null)
            }

            true
        }
    }

    private fun scrollToTop() {
        binding.list.layoutManager?.scrollToPosition(0)
    }

    private fun getShowReadEntriesButtonVisibility(): Boolean {
        return when (filter) {
            EntriesFilter.Unread -> true
            EntriesFilter.Bookmarked -> false
            is EntriesFilter.BelongToFeed -> true
            null -> false
        }
    }

    private fun getEmptyMessage(): String {
        return when (filter) {
            is EntriesFilter.Bookmarked -> getString(R.string.you_have_no_bookmarks)
            else -> getString(R.string.news_list_is_empty)
        }
    }

    private fun showSnackbar(
        @StringRes actionText: Int,
        action: (() -> Unit),
        undoAction: (() -> Unit),
    ) {
        runCatching {
            snackbar.apply {
                setText(actionText)
                setAction(R.string.undo) { undoAction.invoke() }
            }.show()

            action.invoke()
        }.onFailure {
            showErrorDialog(it)
        }
    }

    private fun onListItemClick(item: EntriesAdapter.Item) {
        setRead(
            entryIds = listOf(item.id),
            read = true,
        )

        if (item.openInBrowser) {
            val alternateLinks = item.links.filter { it.rel is AtomLinkRel.Alternate }

            if (alternateLinks.isEmpty()) {
                showErrorDialog(R.string.this_entry_doesnt_have_any_external_links)
                return
            }

            val alternateHtmlLink = alternateLinks.firstOrNull { it.type == "text/html" }
            val linkToOpen = alternateHtmlLink ?: alternateLinks.first()

            openUrl(
                url = linkToOpen.href.toString(),
                useBuiltInBrowser = item.useBuiltInBrowser,
            )
        } else {
            parentFragmentManager.commit {
                replace(
                    R.id.fragmentContainerView,
                    EntryFragment::class.java,
                    bundleOf("entryId" to item.id),
                )
                addToBackStack(null)
            }
        }
    }

    private fun createTouchHelper(): ItemTouchHelper? {
        return when (filter) {
            EntriesFilter.Unread, is EntriesFilter.BelongToFeed -> {
                ItemTouchHelper(object : SwipeHelper(
                    requireContext(),
                    R.drawable.ic_baseline_visibility_24,
                    R.drawable.ic_baseline_bookmark_add_24,
                ) {
                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        val entry = adapter.currentList[viewHolder.bindingAdapterPosition]

                        when (direction) {
                            ItemTouchHelper.LEFT -> {
                                showSnackbar(
                                    actionText = R.string.marked_as_read,
                                    action = { setRead(listOf(entry.id), true) },
                                    undoAction = { setRead(listOf(entry.id), false) },
                                )
                            }

                            ItemTouchHelper.RIGHT -> {
                                showSnackbar(
                                    actionText = R.string.bookmarked,
                                    action = { setBookmarked(entry.id, true) },
                                    undoAction = { setBookmarked(entry.id, false) },
                                )
                            }
                        }
                    }
                })
            }

            EntriesFilter.Bookmarked -> {
                ItemTouchHelper(object : SwipeHelper(
                    requireContext(),
                    R.drawable.ic_baseline_bookmark_remove_24,
                    R.drawable.ic_baseline_bookmark_remove_24,
                ) {
                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        val entry = adapter.currentList[viewHolder.bindingAdapterPosition]

                        when (direction) {
                            ItemTouchHelper.LEFT -> {
                                showSnackbar(
                                    actionText = R.string.removed_from_bookmarks,
                                    action = { setBookmarked(entry.id, false) },
                                    undoAction = { setBookmarked(entry.id, true) },
                                )
                            }

                            ItemTouchHelper.RIGHT -> {
                                showSnackbar(
                                    actionText = R.string.removed_from_bookmarks,
                                    action = { setBookmarked(entry.id, false) },
                                    undoAction = { setBookmarked(entry.id, true) },
                                )
                            }
                        }
                    }
                })
            }

            else -> null
        }
    }

    private fun createTrackingListener(): OnScrollListener {
        return object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {

            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                    return
                }

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager

                if (layoutManager.findFirstVisibleItemPosition() == RecyclerView.NO_POSITION) {
                    return
                }

                if (layoutManager.findLastVisibleItemPosition() == RecyclerView.NO_POSITION) {
                    return
                }

                val visibleEntries =
                    (layoutManager.findFirstVisibleItemPosition()..layoutManager.findLastVisibleItemPosition()).map {
                        adapter.currentList[it]
                    }

                seenEntries.addAll(visibleEntries)
            }
        }
    }

    private fun EntriesAdapter.scrollToTopOnInsert() {
        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (_binding == null) {
                    return
                }

                if (positionStart == 0) {
                    (binding.list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                        0,
                        0,
                    )
                }
            }
        })
    }

    private class CardListAdapterDecoration(private val gapInPixels: Int) :
        RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val position = parent.getChildAdapterPosition(view)

            val bottomGap = if (position == (parent.adapter?.itemCount ?: 0) - 1) {
                gapInPixels
            } else {
                0
            }

            outRect.set(gapInPixels, gapInPixels, gapInPixels, bottomGap)
        }
    }

    sealed class State {

        data class InitialSync(val message: String) : State()

        object LoadingCachedEntries : State()

        data class ShowingCachedEntries(
            val feed: Feed?,
            val entries: List<EntriesAdapter.Item>,
            val showBackgroundProgress: Boolean,
            val scrollToTop: Boolean = false,
            val conf: Conf,
        ) : State()

        data class FailedToSync(val cause: Throwable) : State()
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}