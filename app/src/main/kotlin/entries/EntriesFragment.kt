package entries

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import co.appreactor.feedk.AtomLinkRel
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEntriesBinding
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import com.google.android.material.snackbar.Snackbar
import conf.ConfRepo
import dialog.showErrorDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import navigation.openUrl
import org.koin.androidx.viewmodel.ext.android.viewModel

class EntriesFragment : Fragment(), OnItemReselectedListener {

    private val args by lazy { EntriesFragmentArgs.fromBundle(requireArguments()) }

    private val model: EntriesModel by viewModel()

    private var _binding: FragmentEntriesBinding? = null
    private val binding get() = _binding!!

    private val seenEntries = mutableSetOf<EntriesAdapterItem>()

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
    ): View {
        _binding = FragmentEntriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initToolbar()
        initSwipeRefresh()
        initList()

        model.args.update { args.filter!! }

        model.state
            .onEach { binding.setState(it) }
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

    override fun onNavigationItemReselected(item: MenuItem) {
        scrollToTop()
    }

    private fun scrollToTop() {
        binding.list.layoutManager?.scrollToPosition(0)
    }

    private fun initToolbar() {
        binding.toolbar.apply {
            inflateMenu(R.menu.menu_entries)

            when (val filter = args.filter!!) {
                EntriesFilter.Bookmarked -> {
                    setTitle(R.string.bookmarks)
                }

                EntriesFilter.NotBookmarked -> {
                    setTitle(R.string.news)
                }

                is EntriesFilter.BelongToFeed -> {
                    binding.toolbar.apply {
                        navigationIcon = DrawerArrowDrawable(context).also { it.progress = 1f }
                        setNavigationOnClickListener { findNavController().popBackStack() }
                    }

                    model.getFeed(filter.feedId)
                        .onEach { title = it?.title }
                        .launchIn(viewLifecycleOwner.lifecycleScope)
                }
            }

            initSearchButton()
            initShowReadEntriesButton()
            initSortOrderButton()
            initMarkAllAsReadButton()
            initSettingsButton()
        }
    }

    private fun initSearchButton() {
        binding.toolbar.menu!!.findItem(R.id.search).setOnMenuItemClickListener {
            findNavController().navigate(
                EntriesFragmentDirections.actionEntriesFragmentToSearchFragment(
                    args.filter!!
                )
            )
            true
        }
    }

    private fun initShowReadEntriesButton() {
        val button = binding.toolbar.menu!!.findItem(R.id.showOpenedEntries)
        button.isVisible = getShowReadEntriesButtonVisibility()

        lifecycleScope.launchWhenResumed {
            val conf = model.loadConf().first()

            if (conf.showReadEntries) {
                button.setIcon(R.drawable.ic_baseline_visibility_24)
                button.title = getString(R.string.hide_read_news)
                touchHelper?.attachToRecyclerView(null)
            } else {
                button.setIcon(R.drawable.ic_baseline_visibility_off_24)
                button.title = getString(R.string.show_read_news)
                touchHelper?.attachToRecyclerView(binding.list)
            }
        }

        button.setOnMenuItemClickListener {
            model.saveConf { it.copy(showReadEntries = !it.showReadEntries) }
            initShowReadEntriesButton()
            true
        }
    }

    private fun initSortOrderButton() {
        val button = binding.toolbar.menu.findItem(R.id.sort)

        model.loadConf().onEach { conf ->
            when (conf.sortOrder) {
                ConfRepo.SORT_ORDER_ASCENDING -> {
                    button.setIcon(R.drawable.ic_clock_forward)
                    button.title = getString(R.string.show_newest_first)
                }

                ConfRepo.SORT_ORDER_DESCENDING -> {
                    button.setIcon(R.drawable.ic_clock_back)
                    button.title = getString(R.string.show_oldest_first)
                }
            }

            button.setOnMenuItemClickListener {
                model.changeSortOrder()
                true
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun initMarkAllAsReadButton() {
        binding.toolbar.menu!!.findItem(R.id.markAllAsRead).setOnMenuItemClickListener {
            lifecycleScope.launchWhenResumed { model.markAllAsRead() }
            true
        }
    }

    private fun initSettingsButton() {
        binding.toolbar.menu!!.findItem(R.id.settings).setOnMenuItemClickListener {
            findNavController().navigate(EntriesFragmentDirections.actionEntriesFragmentToSettingsFragment())
            true
        }
    }

    private fun initSwipeRefresh() {
        val swipeRefresh = binding.swipeRefresh

        swipeRefresh.apply {
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

                            swipeRefresh.isRefreshing = false
                        }
                    }
                }

                else -> {
                    swipeRefresh.isEnabled = false
                }
            }
        }
    }

    private fun initList() {
        binding.list.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@EntriesFragment.adapter

            val listItemDecoration = CardListAdapterDecoration(
                resources.getDimensionPixelSize(R.dimen.entries_cards_gap)
            )

            addItemDecoration(listItemDecoration)
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

    private fun FragmentEntriesBinding.views(): List<View> {
        return listOf(progress, message, retry, swipeRefresh)
    }

    private fun FragmentEntriesBinding.visibleViews(state: EntriesModel.State): List<View> {
        return when (state) {
            is EntriesModel.State.InitialSync -> listOf(progress, message)
            is EntriesModel.State.FailedToSync -> listOf(retry)
            is EntriesModel.State.LoadingCachedEntries -> listOf(progress)
            is EntriesModel.State.ShowingCachedEntries -> listOf(swipeRefresh, message)
        }
    }

    private fun FragmentEntriesBinding.setState(state: EntriesModel.State) {
        views().forEach { it.isVisible = false }
        visibleViews(state).forEach { it.isVisible = true }

        when (state) {
            is EntriesModel.State.InitialSync -> {
                message.text = state.message
                message.isVisible = state.message.isNotEmpty()
            }

            is EntriesModel.State.FailedToSync -> {
                retry.setOnClickListener { model.onRetry() }
                showErrorDialog(state.cause)
            }

            EntriesModel.State.LoadingCachedEntries -> {

            }

            is EntriesModel.State.ShowingCachedEntries -> {
                swipeRefresh.isRefreshing = state.showBackgroundProgress

                if (state.entries.isEmpty()) {
                    message.text = getEmptyMessage()
                } else {
                    message.text = ""
                }

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
                setAction(R.string.undo) { undoAction.invoke() }
            }.show()

            model.apply { action.invoke() }
        }.onFailure {
            showErrorDialog(it)
        }
    }

    private fun onListItemClick(item: EntriesAdapterItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            model.setRead(listOf(item.entry.id), true)

            val entry = model.getEntry(item.entry.id).first() ?: return@launch
            val feed = model.getFeed(entry.feedId).first() ?: return@launch

            if (feed.openEntriesInBrowser) {
                openUrl(
                    url = entry.links.first { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }.href.toString(),
                    useBuiltInBrowser = model.loadConf().first().useBuiltInBrowser,
                )
            } else {
                val action = EntriesFragmentDirections.actionEntriesFragmentToEntryFragment(item.entry.id)
                findNavController().navigate(action)
            }
        }
    }

    private fun createTouchHelper(): ItemTouchHelper? {
        return when (args.filter) {
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
                                    undoAction = { model.setRead(listOf(entry.entry.id), false) },
                                )
                            }

                            ItemTouchHelper.RIGHT -> {
                                showSnackbar(
                                    actionText = R.string.bookmarked,
                                    action = { model.setBookmarked(entry.entry.id, true) },
                                    undoAction = { model.setBookmarked(entry.entry.id, false) },
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
                if (positionStart == 0) {
                    (binding.list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                        0,
                        0,
                    )
                }
            }
        })
    }

    private class CardListAdapterDecoration(private val gapInPixels: Int) : RecyclerView.ItemDecoration() {

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
}