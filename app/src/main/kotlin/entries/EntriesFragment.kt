package entries

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.net.toUri
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
import common.openUrl
import common.screenWidth
import common.show
import common.showErrorDialog
import db.EntryWithoutContent
import db.Link
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel

class EntriesFragment : AppFragment(), Scrollable {

    private val args by lazy { EntriesFragmentArgs.fromBundle(requireArguments()) }

    private val model: EntriesModel by viewModel()

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
                        model.setRead(listOf(item.entry.id), true)

                        val entry = model.getEntry(item.entry.id).first() ?: return@launchWhenResumed
                        val feed = model.getFeed(entry.feedId).first() ?: return@launchWhenResumed

                        if (feed.openEntriesInBrowser) {
                            openUrl(
                                url = entry.links.first { it.rel == "alternate" && it.type == "text/html" }.href.toString(),
                                useBuiltInBrowser = model.loadConf().first().useBuiltInBrowser,
                            )
                        } else {
                            val action = EntriesFragmentDirections.actionEntriesFragmentToEntryFragment(item.entry.id)
                            findNavController().navigate(action)
                        }
                    }
                }

                override fun onDownloadAudioEnclosureClick(entry: EntryWithoutContent, link: Link) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            model.downloadAudioEnclosure(entry, link)
                        }.onFailure {
                            showErrorDialog(it)
                        }
                    }
                }

                override fun onPlayAudioEnclosureClick(entry: EntryWithoutContent, link: Link) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            openCachedPodcast(
                                cacheUri = link.extCacheUri?.toUri(),
                                enclosureLinkType = link.type!!,
                            )

                            model.setRead(listOf(link.entryId!!), true)
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
                        val entry = adapter.currentList[viewHolder.bindingAdapterPosition]

                        when (direction) {
                            ItemTouchHelper.LEFT -> {
                                showSnackbar(
                                    actionText = R.string.marked_as_read,
                                    action = { model.setRead(listOf(entry.entry.id), true) },
                                    undoAction = { model.setRead(listOf(entry.entry.id), false) }
                                )
                            }

                            ItemTouchHelper.RIGHT -> {
                                showSnackbar(
                                    actionText = R.string.bookmarked,
                                    action = { model.setBookmarked(entry.entry.id, true) },
                                    undoAction = { model.setBookmarked(entry.entry.id, false) }
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
                                    action = { model.setBookmarked(entry.entry.id, false) },
                                    undoAction = { model.setBookmarked(entry.entry.id, true) },
                                )
                            }

                            ItemTouchHelper.RIGHT -> {
                                showSnackbar(
                                    actionText = R.string.removed_from_bookmarks,
                                    action = { model.setBookmarked(entry.entry.id, false) },
                                    undoAction = { model.setBookmarked(entry.entry.id, true) },
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
            .onStart { model.filter.update { args.filter!! } }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        model.state
            .onEach { displayState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        if (runBlocking { model.loadConf().first().markScrolledEntriesAsRead }) {
            model.setRead(
                entryIds = seenEntries.map { it.entry.id },
                value = true,
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
                    title = model.getFeed(filter.feedId).first()?.title
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
            val conf = model.loadConf().first()

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
                model.saveConf { it.copy(showReadEntries = !it.showReadEntries) }
                initShowReadEntriesButton()
            }

            true
        }
    }

    private fun initSortOrderButton() {
        val sortOrderMenuItem = toolbar?.menu?.findItem(R.id.sort) ?: return

        model.loadConf().onEach { conf ->
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
                model.changeSortOrder()
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

        model.loadConf().onEach {
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
        when (state) {
            null -> {
                swipeRefresh.isRefreshing = false
                list.hide()
                progress.hide()
                message.hide()
                retry.hide()
            }

            is EntriesModel.State.InitialSync -> {
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
                        model.onRetry()
                    }
                }
                showErrorDialog(state.cause)
            }

            EntriesModel.State.LoadingCachedEntries -> {
                swipeRefresh.isRefreshing = false
                list.hide()
                progress.show(animate = true)
                message.hide()
                retry.hide()
            }

            is EntriesModel.State.ShowingCachedEntries -> {
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

    private fun showSnackbar(
        @StringRes actionText: Int,
        action: (() -> Unit),
        undoAction: (() -> Unit),
    ) {
        runCatching {
            snackbar.apply {
                setText(actionText)
                setAction(R.string.undo) {
                    undoAction.invoke()
                }
            }.show()

            model.apply {
                action.invoke()
            }
        }.onFailure {
            showErrorDialog(it)
        }
    }
}