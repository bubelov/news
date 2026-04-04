package org.vestifeed.entries

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.vestifeed.parser.AtomLinkRel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vestifeed.R
import org.vestifeed.app.db
import org.vestifeed.app.sync
import org.vestifeed.databinding.FragmentEntriesBinding
import org.vestifeed.db.table.Conf
import org.vestifeed.db.table.ConfSchema
import org.vestifeed.db.table.EntryQueries
import org.vestifeed.db.table.Feed
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entry.EntryFragment
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

    sealed class State {
        data class InitialSync(val message: String) : State()

        object LoadingCachedEntries : State()

        data class ShowingCachedEntries(
            val feed: Feed?,
            val entries: List<EntriesAdapter.Item>,
        ) : State()
    }

    private val state = MutableStateFlow<State>(State.LoadingCachedEntries)

    private var _binding: FragmentEntriesBinding? = null
    private val binding get() = _binding!!

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEntriesBinding.inflate(inflater, container, false)
        return binding.root
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

//        viewLifecycleOwner.lifecycleScope.launch {
//            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
//                state.collect { binding.setState(it) }
//            }
//        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                sync().state.collect { onNewSyncState(it) }
            }
        }
    }

    private suspend fun onNewSyncState(syncState: Sync.State) {
        Log.d("entries_fragment", "new sync state: $syncState")

        when (filter) {
            EntriesFilter.Unread -> {
                binding.toolbar.setTitle(R.string.unread)
            }

            EntriesFilter.Bookmarked -> {
                binding.toolbar.setTitle(R.string.bookmarks)
            }

            is EntriesFilter.BelongToFeed -> {
                val feedId = (filter as EntriesFilter.BelongToFeed).feedId
                val feed = withContext(Dispatchers.IO) {
                    db().feed.selectById(feedId)
                }
                binding.toolbar.setTitle(feed?.title ?: feedId)
            }

            null -> {}
        }

        binding.swipeRefresh.isRefreshing = syncState is Sync.State.FollowUpSync

        when (syncState) {
            is Sync.State.InitialSync -> {
                when (syncState.stage) {
                    Sync.InitialSyncStage.SyncingFeeds -> {
                        binding.swipeRefresh.isVisible = false
                        binding.progress.isVisible = true
                        binding.message.isVisible = true
                        binding.message.setText("Fetching feeds")
                    }

                    is Sync.InitialSyncStage.SyncingEntries -> {
                        binding.swipeRefresh.isVisible = false
                        binding.progress.isVisible = true
                        binding.message.isVisible = true
                        binding.message.setText("Fetching entries (${syncState.stage.entriesSynced})")
                    }
                }
            }

            is Sync.State.Idle -> {
                if (syncState.error != null) {
                    showErrorDialog(syncState.error)
                }

                binding.progress.isVisible = true

                val entries = when (filter) {
                    EntriesFilter.Unread -> {
                        withContext(Dispatchers.IO) { db().entry.selectUnread() }
                    }

                    EntriesFilter.Bookmarked -> {
                        withContext(Dispatchers.IO) { db().entry.selectBookmarked() }
                    }

                    is EntriesFilter.BelongToFeed -> {
                        withContext(Dispatchers.IO) {
                            db().entry.selectByFeedId((filter as EntriesFilter.BelongToFeed).feedId)
                                .filterNot { it.extRead }
                        }
                    }

                    null -> emptyList()
                }

                val conf = withContext(Dispatchers.IO) {
                    db().conf.select()
                }

                val listItems = withContext(Dispatchers.IO) {
                    entries.map { it.toItem(conf) }
                }

                adapter.submitList(listItems)

                binding.progress.isVisible = false

                if (listItems.isEmpty()) {
                    binding.message.isVisible = true
                    binding.message.text = getEmptyMessage()
                } else {
                    binding.message.isVisible = false
                    binding.swipeRefresh.isVisible = true
                }
            }

            else -> {

            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

//    private fun refresh() {
//        val conf = db().conf.select()
//        val syncState = sync().state.value
//        updateState(conf, syncState)
//    }

    private fun onPullRefresh() {
        sync().runInBackground()
    }

    private fun saveConf(newConf: (Conf) -> Conf) {
        db().conf.update(newConf)
    }

    private fun changeSortOrder() {
        db().conf.update {
            val newSortOrder = when (it.sortOrder) {
                ConfSchema.SORT_ORDER_ASCENDING -> ConfSchema.SORT_ORDER_DESCENDING
                ConfSchema.SORT_ORDER_DESCENDING -> ConfSchema.SORT_ORDER_ASCENDING
                else -> throw Exception()
            }

            it.copy(sortOrder = newSortOrder)
        }

//        refresh()
    }

    private fun setRead(entryIds: Collection<String>, read: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                entryIds.forEach {
                    db().entry.updateReadAndReadSynced(
                        id = it,
                        extRead = read,
                        extReadSynced = false,
                    )
                }
            }

            sync().runInBackground(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    private fun setBookmarked(entryId: String, bookmarked: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db().entry.updateBookmarkedAndBookmarkedSynced(
                    id = entryId,
                    extBookmarked = bookmarked,
                    extBookmarkedSynced = false
                )
            }

            sync().runInBackground(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    private fun markAllAsRead() {
        // todo
    }

    private fun EntryQueries.EntriesAdapterRow.toItem(conf: Conf): EntriesAdapter.Item {
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
        Log.d("entries_fragment", state.toString())

        updateToolbar(state)

        progress.isVisible = false
        message.isVisible = false
        swipeRefresh.isVisible = false

        when (state) {
            is State.InitialSync -> {
                progress.isVisible = true
                message.isVisible = state.message.isNotBlank()
                message.text = state.message
            }

            State.LoadingCachedEntries -> {
                progress.isVisible = true
            }

            is State.ShowingCachedEntries -> {
                Log.d("entries_fragment", "cached entries: ${state.entries.size}")

                if (state.entries.isEmpty()) {
                    message.isVisible = true
                    message.text = getEmptyMessage()
                } else {
                    swipeRefresh.isVisible = true
                    adapter.submitList(state.entries)
                }
            }
        }
    }

    private fun updateToolbar(state: State) {
        binding.toolbar.apply {
            when (filter) {
                EntriesFilter.Bookmarked -> {
                    when (state) {
                        is State.ShowingCachedEntries -> {
                            setTitle(getString(R.string.bookmarks) + " (${state.entries.size})")
                        }

                        else -> {
                            setTitle(R.string.bookmarks)
                        }
                    }
                }

                EntriesFilter.Unread -> {
                    when (state) {
                        is State.ShowingCachedEntries -> {
                            setTitle(getString(R.string.unread) + " (${state.entries.size})")
                        }

                        else -> {
                            setTitle(R.string.unread)
                        }
                    }
                }

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

        val conf = db().conf.select()

        if (conf.showReadEntries) {
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

        val conf = db().conf.select()

        when (conf.sortOrder) {
            ConfSchema.SORT_ORDER_ASCENDING -> {
                button.setIcon(R.drawable.ic_clock_forward)
                button.title = getString(R.string.show_newest_first)
            }

            ConfSchema.SORT_ORDER_DESCENDING -> {
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

    override fun onOpenGraphImageDownloaded() {
        super.onOpenGraphImageDownloaded()
        // todo optimize
        viewLifecycleOwner.lifecycleScope.launch {
            val syncState = sync().state.value

            if (syncState is Sync.State.Idle) {
                onNewSyncState(sync().state.value)
            }
        }
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
        db().entry.updateReadAndReadSynced(
            id = item.id,
            extRead = true,
            extReadSynced = false,
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

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}