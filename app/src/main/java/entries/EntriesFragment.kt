package entries

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEntriesBinding
import com.google.android.material.snackbar.Snackbar
import common.AppFragment
import common.CardListAdapterDecoration
import common.ConfRepository
import common.Scrollable
import common.hide
import common.openLink
import common.screenWidth
import common.show
import common.showDialog
import common.showErrorDialog
import db.Entry
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class EntriesFragment : AppFragment(), Scrollable {

    private val args by lazy {
        EntriesFragmentArgs.fromBundle(requireArguments())
    }

    private val model: EntriesViewModel by viewModel()
    private val sharedModel: EntriesSharedViewModel by sharedViewModel()

    private var _binding: FragmentEntriesBinding? = null
    private val binding get() = _binding!!

    private val seenEntries = mutableSetOf<EntriesAdapterItem>()

    private val snackbar by lazy {
        Snackbar.make(
            binding.root,
            "",
            Snackbar.LENGTH_SHORT
        ).apply {
            anchorView = requireActivity().findViewById(R.id.bottomNavigation)
        }
    }

    private val adapter by lazy {
        EntriesAdapter(
            scope = lifecycleScope,
            callback = object : EntriesAdapterCallback {
                override fun onItemClick(item: EntriesAdapterItem) {
                    lifecycleScope.launchWhenResumed {
                        val entry = model.getEntry(item.id) ?: return@launchWhenResumed
                        val feed = model.getFeed(entry.feedId) ?: return@launchWhenResumed

                        model.setRead(listOf(entry.id), true)
                        model.openedEntry.value = item

                        if (feed.openEntriesInBrowser) {
                            openLink(entry.link)
                        } else {
                            val action =
                                EntriesFragmentDirections.actionEntriesFragmentToEntryFragment(item.id)
                            findNavController().navigate(action)
                        }
                    }
                }

                override fun onDownloadPodcastClick(item: EntriesAdapterItem) {
                    lifecycleScope.launchWhenResumed {
                        runCatching {
                            model.downloadPodcast(item.id)
                        }.onFailure {
                            showErrorDialog(it)
                        }
                    }
                }

                override fun onPlayPodcastClick(item: EntriesAdapterItem) {
                    lifecycleScope.launch {
                        runCatching {
                            val entry = model.getEntry(item.id) ?: return@launch
                            model.setRead(listOf(entry.id), true)
                            model.reloadEntry(item)
                            requireContext().openCachedPodcast(entry)
                        }.onFailure {
                            showErrorDialog(it)
                        }
                    }
                }
            }
        ).apply {
            screenWidth = screenWidth()

            registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    if (positionStart == 0) {
                        (binding.listView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                            0,
                            0
                        )
                    }
                }
            })
        }
    }

    private val touchHelper: ItemTouchHelper? by lazy {
        when (args.filter) {
            EntriesFilter.OnlyNotBookmarked -> {
                ItemTouchHelper(object : SwipeHelper(
                    requireContext(),
                    R.drawable.ic_baseline_visibility_24,
                    R.drawable.ic_baseline_bookmark_add_24,
                ) {
                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        val entryIndex = viewHolder.bindingAdapterPosition
                        val entry = adapter.currentList[viewHolder.bindingAdapterPosition]

                        when (direction) {
                            ItemTouchHelper.LEFT -> {
                                dismissEntry(
                                    entry = entry,
                                    entryIndex = entryIndex,
                                    actionText = R.string.marked_as_read,
                                    action = { model.setRead(listOf(entry.id), true) },
                                    undoAction = { model.setRead(listOf(entry.id), false) }
                                )
                            }

                            ItemTouchHelper.RIGHT -> {
                                dismissEntry(
                                    entry = entry,
                                    entryIndex = entryIndex,
                                    actionText = R.string.bookmarked,
                                    action = { model.setBookmarked(entry.id, true) },
                                    undoAction = { model.setBookmarked(entry.id, false) }
                                )
                            }
                        }
                    }
                })
            }

            EntriesFilter.OnlyBookmarked -> {
                ItemTouchHelper(object : SwipeHelper(
                    requireContext(),
                    R.drawable.ic_baseline_bookmark_remove_24,
                    R.drawable.ic_baseline_bookmark_remove_24,
                ) {
                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        val entryIndex = viewHolder.bindingAdapterPosition
                        val entry = adapter.currentList[viewHolder.bindingAdapterPosition]

                        when (direction) {
                            ItemTouchHelper.LEFT -> {
                                dismissEntry(
                                    entry = entry,
                                    entryIndex = entryIndex,
                                    actionText = R.string.removed_from_bookmarks,
                                    action = {
                                        model.setRead(listOf(entry.id), true)
                                        model.setBookmarked(entry.id, false)
                                    },
                                    undoAction = {
                                        model.setRead(listOf(entry.id), false)
                                        model.setBookmarked(entry.id, true)
                                    }
                                )
                            }

                            ItemTouchHelper.RIGHT -> {
                                dismissEntry(
                                    entry = entry,
                                    entryIndex = entryIndex,
                                    actionText = R.string.removed_from_bookmarks,
                                    action = {
                                        model.setRead(listOf(entry.id), true)
                                        model.setBookmarked(entry.id, false)
                                    },
                                    undoAction = {
                                        model.setRead(listOf(entry.id), false)
                                        model.setBookmarked(entry.id, true)
                                    }
                                )
                            }
                        }
                    }
                })
            }

            else -> null
        }
    }

//    private val listLayoutManager: LinearLayoutManager by lazy {
//        LinearLayoutManager(requireContext())
//    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.apply {
            setTitle(R.string.news)
            inflateMenu(R.menu.menu_entries)
        }

        initListView()

        lifecycleScope.launchWhenResumed {
            runCatching {
                model.onViewReady(args.filter!!, sharedModel)
            }.onFailure {
                showErrorDialog(it)
            }
        }

        binding.retry.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                model.onRetry(sharedModel)
            }
        }

        when (val filter = args.filter) {
            is EntriesFilter.OnlyNotBookmarked -> {
                isDrawerLocked = false
                binding.swipeRefresh.setOnRefreshListener {
                    lifecycleScope.launch {
                        runCatching {
                            model.fetchEntriesFromApi()
                        }.onFailure {
                            binding.swipeRefresh.isRefreshing = false
                            showErrorDialog(it)
                        }
                    }
                }
            }

            is EntriesFilter.OnlyBookmarked -> {
                isDrawerLocked = false
                toolbar.setTitle(R.string.bookmarks)
                binding.swipeRefresh.isEnabled = false
            }

            is EntriesFilter.OnlyFromFeed -> {
                binding.swipeRefresh.isEnabled = false

                toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)

                toolbar.setNavigationOnClickListener {
                    findNavController().popBackStack()
                }

                lifecycleScope.launchWhenResumed {
                    val feed = model.getFeed(filter.feedId)
                    toolbar.title = feed?.title
                }
            }
        }

        initSearchButton()
        initShowReadEntriesButton()
        initSortOrderButton()
        initMarkAllAsReadButton()

        lifecycleScope.launchWhenResumed {
            model.state.collectLatest { displayState(it) }
        }
    }

    override fun onResume() {
        super.onResume()

        model.apply {
            openedEntry.value?.let {
                reloadEntry(it)
                openedEntry.value = null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        if (runBlocking { model.getConf().markScrolledEntriesAsRead }) {
            model.setRead(
                entryIds = seenEntries.map { it.id },
                read = true,
            )

            seenEntries.clear()
        }
    }

    override fun scrollToTop() {
        binding.listView.layoutManager?.scrollToPosition(0)
    }

    private fun initSearchButton() {
        val searchMenuItem = toolbar.menu.findItem(R.id.search)

        searchMenuItem.setOnMenuItemClickListener {
            findNavController().navigate(
                EntriesFragmentDirections.actionEntriesFragmentToSearchFragment(
                    args.filter!!
                )
            )
            true
        }
    }

    private fun initShowReadEntriesButton() {
        val showOpenedEntriesMenuItem = toolbar.menu.findItem(R.id.showOpenedEntries)
        showOpenedEntriesMenuItem.isVisible = getShowReadEntriesButtonVisibility()

        lifecycleScope.launchWhenResumed {
            val conf = model.getConf()

            if (conf.showReadEntries) {
                showOpenedEntriesMenuItem.setIcon(R.drawable.ic_baseline_visibility_24)
                showOpenedEntriesMenuItem.title = getString(R.string.hide_read_news)
                touchHelper?.attachToRecyclerView(null)
            } else {
                showOpenedEntriesMenuItem.setIcon(R.drawable.ic_baseline_visibility_off_24)
                showOpenedEntriesMenuItem.title = getString(R.string.show_read_news)
                touchHelper?.attachToRecyclerView(binding.listView)
            }
        }

        showOpenedEntriesMenuItem.setOnMenuItemClickListener {
            lifecycleScope.launchWhenResumed {
                adapter.submitList(null)
                val conf = model.getConf()
                model.saveConf(model.getConf().copy(showReadEntries = !conf.showReadEntries))
                initShowReadEntriesButton()
            }

            true
        }
    }

    private fun initSortOrderButton() {
        val sortOrderMenuItem = toolbar.menu.findItem(R.id.sort)

        lifecycleScope.launchWhenResumed {
            val conf = model.getConf()

            when (conf.sortOrder) {
                ConfRepository.SORT_ORDER_ASCENDING -> {
                    sortOrderMenuItem.setIcon(R.drawable.ic_clock_forward)
                    sortOrderMenuItem.title = getString(R.string.show_newest_first)
                }

                ConfRepository.SORT_ORDER_DESCENDING -> {
                    sortOrderMenuItem.setIcon(R.drawable.ic_clock_back)
                    sortOrderMenuItem.title = getString(R.string.show_oldest_first)
                }
            }
        }

        sortOrderMenuItem.setOnMenuItemClickListener {
            lifecycleScope.launchWhenResumed {
                adapter.submitList(null)

                val conf = model.getConf()

                val newSortOrder = when (conf.sortOrder) {
                    ConfRepository.SORT_ORDER_ASCENDING -> ConfRepository.SORT_ORDER_DESCENDING
                    ConfRepository.SORT_ORDER_DESCENDING -> ConfRepository.SORT_ORDER_ASCENDING
                    else -> throw Exception()
                }

                model.saveConf(conf.copy(sortOrder = newSortOrder))
                initSortOrderButton()
            }

            true
        }
    }

    private fun initMarkAllAsReadButton() {
        toolbar.menu.findItem(R.id.markAllAsRead)?.setOnMenuItemClickListener {
            lifecycleScope.launchWhenResumed { model.markAllAsRead() }
            true
        }
    }

    private fun initListView() {
        binding.apply {
            listView.apply {
                layoutManager = LinearLayoutManager(requireContext())
                setHasFixedSize(true)
                adapter = this@EntriesFragment.adapter

                val listItemDecoration = CardListAdapterDecoration(
                    resources.getDimensionPixelSize(R.dimen.entries_cards_gap)
                )

                addItemDecoration(listItemDecoration)
            }
        }

        touchHelper?.attachToRecyclerView(binding.listView)

        lifecycleScope.launchWhenResumed {
            if (model.getConf().markScrolledEntriesAsRead
                && (args.filter is EntriesFilter.OnlyNotBookmarked || args.filter is EntriesFilter.OnlyFromFeed)
            ) {
                markScrolledEntriesAsRead()
            }
        }
    }

    private fun markScrolledEntriesAsRead() = lifecycleScope.launchWhenResumed {
        binding.listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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

                val seenItemsOutOfRange =
                    seenEntries.filterNot { visibleEntries.contains(it) }
                seenEntries.removeAll(seenItemsOutOfRange)

                seenItemsOutOfRange.forEach {
                    if (!it.read.value) {
                        it.read.value = true
                        model.setRead(listOf(it.id), true)
                    }
                }
            }
        })
    }

    private suspend fun displayState(state: EntriesViewModel.State?) = binding.apply {
        Timber.d("Displaying state $state")

        when (state) {
            null -> {
                swipeRefresh.isRefreshing = false
                listView.hide()
                progress.hide()
                message.hide()
                retry.hide()
            }

            is EntriesViewModel.State.PerformingInitialSync -> {
                swipeRefresh.isRefreshing = false
                listView.hide()
                progress.show(animate = true)
                message.show(animate = true)
                state.message.collect { message.text = it }
                retry.hide()
            }

            is EntriesViewModel.State.FailedToSync -> {
                swipeRefresh.isRefreshing = false
                listView.hide()
                progress.hide()
                message.hide()
                retry.show(animate = true)
                showErrorDialog(state.error)
            }

            EntriesViewModel.State.LoadingEntries -> {
                swipeRefresh.isRefreshing = false
                listView.hide()
                progress.show(animate = true)
                message.hide()
                retry.hide()
            }

            is EntriesViewModel.State.ShowingEntries -> {
                swipeRefresh.isRefreshing = state.showBackgroundProgress
                listView.show()
                progress.hide()

                if (state.entries.isEmpty()) {
                    message.text = getEmptyMessage(state.includesUnread)
                    message.show(animate = true)
                } else {
                    message.hide()
                }

                retry.hide()
                seenEntries.clear()
                adapter.submitList(state.entries)
            }
        }
    }

    private fun getShowReadEntriesButtonVisibility(): Boolean {
        return when (args.filter!!) {
            EntriesFilter.OnlyNotBookmarked -> true
            EntriesFilter.OnlyBookmarked -> false
            is EntriesFilter.OnlyFromFeed -> true
        }
    }

    private fun getEmptyMessage(includesUnread: Boolean): String {
        return when (args.filter) {
            is EntriesFilter.OnlyBookmarked -> getString(R.string.you_have_no_bookmarks)
            else -> if (includesUnread) {
                getString(R.string.news_list_is_empty)
            } else {
                getString(R.string.you_have_no_unread_news)
            }
        }
    }

    private fun Context.openCachedPodcast(entry: Entry) {
        val cacheUri = model.getCachedPodcastUri(entryId = entry.id)

        if (cacheUri == null) {
            showErrorDialog(Exception("Can't find cache URI for podcast entry ${entry.id}"))
            return
        }

        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = cacheUri
            setDataAndType(cacheUri, entry.enclosureLinkType)
        }

        runCatching {
            startActivity(intent)
        }.onFailure {
            if (it is ActivityNotFoundException) {
                showDialog(
                    R.string.error,
                    R.string.you_have_no_apps_which_can_play_this_podcast
                )
            } else {
                showErrorDialog(it)
            }
        }
    }

    private fun dismissEntry(
        entry: EntriesAdapterItem,
        entryIndex: Int,
        @StringRes actionText: Int,
        action: (() -> Unit),
        undoAction: (() -> Unit),
    ) {
        runCatching {
            snackbar.apply {
                setText(actionText)
                setAction(R.string.undo) {
                    model.show(entry, entryIndex)
                    undoAction.invoke()
                }
            }.show()

            model.apply {
                hide(entry)
                action.invoke()
            }
        }.onFailure {
            showErrorDialog(it)
        }
    }
}