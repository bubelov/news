package entries

import android.net.Uri
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
import common.openCachedPodcast
import common.openLink
import common.screenWidth
import common.show
import common.showErrorDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class EntriesFragment : AppFragment(), Scrollable {

    private val args by lazy {
        val args = requireArguments()

        // Default filter
        if (args["filter"] == null) {
            args.putParcelable("filter", EntriesFilter.NotBookmarked)
        }

        EntriesFragmentArgs.fromBundle(requireArguments())
    }

    private val model: EntriesModel by viewModel()
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
            callback = object : EntriesAdapterCallback {
                override fun onItemClick(item: EntriesAdapterItem) {
                    lifecycleScope.launchWhenResumed {
                        model.setRead(listOf(item.id), true)

                        val entry = model.getEntry(item.id) ?: return@launchWhenResumed
                        val feed = model.getFeed(entry.feedId) ?: return@launchWhenResumed

                        if (feed.openEntriesInBrowser) {
                            val link = runCatching {
                                Uri.parse(entry.link)
                            }.getOrElse {
                                showErrorDialog(it)
                                return@launchWhenResumed
                            }

                            openLink(link, model.getConf().first().useBuiltInBrowser)
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
                            openCachedPodcast(
                                cacheUri = model.getCachedPodcastUri(entry.id),
                                enclosureLinkType = entry.enclosureLinkType,
                            )
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
                        (binding.list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
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
            EntriesFilter.NotBookmarked -> {
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
                                    action = {
                                        model.setRead(listOf(entry.id), true)
                                    },
                                    undoAction = {
                                        model.setRead(listOf(entry.id), false)
                                    }
                                )
                            }

                            ItemTouchHelper.RIGHT -> {
                                dismissEntry(
                                    entry = entry,
                                    entryIndex = entryIndex,
                                    actionText = R.string.bookmarked,
                                    action = {
                                        model.setBookmarked(entry.id, true)
                                    },
                                    undoAction = {
                                        model.setBookmarked(entry.id, false)
                                    }
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
                        val entryIndex = viewHolder.bindingAdapterPosition
                        val entry = adapter.currentList[viewHolder.bindingAdapterPosition]

                        when (direction) {
                            ItemTouchHelper.LEFT -> {
                                dismissEntry(
                                    entry = entry,
                                    entryIndex = entryIndex,
                                    actionText = R.string.removed_from_bookmarks,
                                    action = {
                                        model.setBookmarked(entry.id, false)
                                    },
                                    undoAction = {
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
                                        model.setBookmarked(entry.id, false)
                                    },
                                    undoAction = {
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

    private val trackingListener = object : RecyclerView.OnScrollListener() {
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

        initDrawer()
        initToolbar()
        initSwipeRefresh()
        initList()

        model.state
            .onStart { model.onViewCreated(args.filter!!, sharedModel) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        model.state
            .onEach { displayState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        if (runBlocking { model.getConf().first().markScrolledEntriesAsRead }) {
            model.setRead(
                entryIds = seenEntries.map { it.id },
                read = true,
            )

            seenEntries.clear()
        }
    }

    override fun scrollToTop() {
        binding.list.layoutManager?.scrollToPosition(0)
    }

    private fun initDrawer() {
        isDrawerLocked = args.filter is EntriesFilter.BelongToFeed
    }

    private fun initToolbar() = toolbar?.apply {
        inflateMenu(R.menu.menu_entries)

        when (val filter = args.filter!!) {
            EntriesFilter.Bookmarked -> {
                setTitle(R.string.bookmarks)
            }

            EntriesFilter.NotBookmarked -> {
                setTitle(R.string.news)
            }

            is EntriesFilter.BelongToFeed -> {
                setupUpNavigation()

                lifecycleScope.launchWhenResumed {
                    title = model.getFeed(filter.feedId)?.title
                }
            }
        }

        initSearchButton()
        initShowReadEntriesButton()
        initSortOrderButton()
        initMarkAllAsReadButton()
    }

    private fun initSearchButton() {
        val searchMenuItem = toolbar?.menu?.findItem(R.id.search)

        searchMenuItem?.setOnMenuItemClickListener {
            findNavController().navigate(
                EntriesFragmentDirections.actionEntriesFragmentToSearchFragment(
                    args.filter!!
                )
            )
            true
        }
    }

    private fun initShowReadEntriesButton() {
        val showOpenedEntriesMenuItem = toolbar?.menu?.findItem(R.id.showOpenedEntries)
        showOpenedEntriesMenuItem?.isVisible = getShowReadEntriesButtonVisibility()

        lifecycleScope.launchWhenResumed {
            val conf = model.getConf().first()

            if (conf.showReadEntries) {
                showOpenedEntriesMenuItem?.setIcon(R.drawable.ic_baseline_visibility_24)
                showOpenedEntriesMenuItem?.title = getString(R.string.hide_read_news)
                touchHelper?.attachToRecyclerView(null)
            } else {
                showOpenedEntriesMenuItem?.setIcon(R.drawable.ic_baseline_visibility_off_24)
                showOpenedEntriesMenuItem?.title = getString(R.string.show_read_news)
                touchHelper?.attachToRecyclerView(binding.list)
            }
        }

        showOpenedEntriesMenuItem?.setOnMenuItemClickListener {
            lifecycleScope.launchWhenResumed {
                val conf = model.getConf().first()
                model.saveConf(conf.copy(showReadEntries = !conf.showReadEntries))
                initShowReadEntriesButton()
            }

            true
        }
    }

    private fun initSortOrderButton() {
        val sortOrderMenuItem = toolbar?.menu?.findItem(R.id.sort) ?: return

        model.getConf().onEach { conf ->
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

            sortOrderMenuItem.setOnMenuItemClickListener {
                val newSortOrder = when (conf.sortOrder) {
                    ConfRepository.SORT_ORDER_ASCENDING -> ConfRepository.SORT_ORDER_DESCENDING
                    ConfRepository.SORT_ORDER_DESCENDING -> ConfRepository.SORT_ORDER_ASCENDING
                    else -> throw Exception()
                }

                lifecycleScope.launch {
                    model.saveConf(conf.copy(sortOrder = newSortOrder))
                }

                true
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun initMarkAllAsReadButton() {
        toolbar?.menu?.findItem(R.id.markAllAsRead)?.setOnMenuItemClickListener {
            lifecycleScope.launchWhenResumed { model.markAllAsRead() }
            true
        }
    }

    private fun initSwipeRefresh() = binding.swipeRefresh.apply {
        when (args.filter) {
            is EntriesFilter.NotBookmarked -> {
                isEnabled = true

                setOnRefreshListener {
                    lifecycleScope.launch {
                        runCatching {
                            model.onPullRefresh()
                        }.onFailure {
                            lifecycleScope.launchWhenResumed { showErrorDialog(it) }
                        }

                        binding.swipeRefresh.isRefreshing = false
                    }
                }
            }

            else -> {
                binding.swipeRefresh.isEnabled = false
            }
        }
    }

    private fun initList() {
        binding.apply {
            list.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = this@EntriesFragment.adapter

                val listItemDecoration = CardListAdapterDecoration(
                    resources.getDimensionPixelSize(R.dimen.entries_cards_gap)
                )

                addItemDecoration(listItemDecoration)
            }
        }

        touchHelper?.attachToRecyclerView(binding.list)

        model.getConf().onEach {
            if (
                it.markScrolledEntriesAsRead
                && (args.filter is EntriesFilter.NotBookmarked || args.filter is EntriesFilter.BelongToFeed)
            ) {
                binding.list.addOnScrollListener(trackingListener)
            } else {
                binding.list.removeOnScrollListener(trackingListener)
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private suspend fun displayState(state: EntriesModel.State?) = binding.apply {
        Timber.d("Displaying state ${state?.javaClass?.simpleName}")

        when (state) {
            null -> {
                swipeRefresh.isRefreshing = false
                list.hide()
                progress.hide()
                message.hide()
                retry.hide()
            }

            is EntriesModel.State.PerformingInitialSync -> {
                swipeRefresh.isRefreshing = false
                list.hide()
                progress.show(animate = true)
                message.show(animate = true)
                message.text = state.message
                retry.hide()
            }

            is EntriesModel.State.FailedToSync -> {
                swipeRefresh.isRefreshing = false
                list.hide()
                progress.hide()
                message.hide()
                retry.show(animate = true)
                retry.setOnClickListener {
                    lifecycleScope.launchWhenResumed {
                        model.onRetry(sharedModel)
                    }
                }
                showErrorDialog(state.cause)
            }

            EntriesModel.State.LoadingEntries -> {
                swipeRefresh.isRefreshing = false
                list.hide()
                progress.show(animate = true)
                message.hide()
                retry.hide()
            }

            is EntriesModel.State.ShowingEntries -> {
                Timber.d(
                    "Showing entries (count = %s, show_background_progress = %s)",
                    state.entries.count(),
                    state.showBackgroundProgress,
                )

                swipeRefresh.isRefreshing = state.showBackgroundProgress
                list.show()
                progress.hide()

                if (state.entries.isEmpty()) {
                    message.text = getEmptyMessage()
                    message.show(animate = true)
                } else {
                    message.hide()
                }

                retry.hide()
                seenEntries.clear()
                adapter.submitList(state.entries) { if (state.scrollToTop) scrollToTop() }
            }
        }
    }

    private fun getShowReadEntriesButtonVisibility(): Boolean {
        return when (args.filter!!) {
            EntriesFilter.NotBookmarked -> true
            EntriesFilter.Bookmarked -> false
            is EntriesFilter.BelongToFeed -> true
        }
    }

    private fun getEmptyMessage(): String {
        return when (args.filter) {
            is EntriesFilter.Bookmarked -> getString(R.string.you_have_no_bookmarks)
            else -> getString(R.string.news_list_is_empty)
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