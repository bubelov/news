package entries

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import anim.animateVisibilityChanges
import anim.showSmooth
import co.appreactor.feedk.AtomLinkRel
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEntriesBinding
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import com.google.android.material.snackbar.Snackbar
import conf.ConfRepo
import dialog.showErrorDialog
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import navigation.openUrl
import org.koin.androidx.viewmodel.ext.android.viewModel

class EntriesFragment : Fragment(), OnItemReselectedListener {

    private val args by lazy { EntriesFragmentArgs.fromBundle(requireArguments()) }

    private val model: EntriesModel by viewModel()

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
        return if (model.hasBackend()) {
            val intent = requireActivity().intent
            val sharedFeedUrl = (intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT))?.trim() ?: ""
            intent.removeExtra(Intent.EXTRA_TEXT)

            if (sharedFeedUrl.isNotBlank()) {
                findNavController().navigate(
                    resId = R.id.feedsFragment,
                    args = bundleOf(Pair("url", sharedFeedUrl)),
                    navOptions = NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build(),
                )
            }

            if (!requireArguments().containsKey("filter")) {
                requireArguments().putParcelable("filter", EntriesFilter.NotBookmarked)
            }

            _binding = FragmentEntriesBinding.inflate(inflater, container, false)
            binding.root
        } else {
            val builder = NavOptions.Builder().setPopUpTo(R.id.nav_graph, true)
            findNavController().navigate(R.id.authFragment, null, builder.build())
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSwipeRefresh()
        initList()

        model.args.update { args.filter!! }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { binding.setState(it) }
            }
        }
    }

    override fun onStop() {
        super.onStop()

        val state = model.state.value

        if (state is EntriesModel.State.ShowingCachedEntries && state.conf.mark_scrolled_entries_as_read) {
            model.setRead(
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

    override fun onNavigationItemReselected(item: MenuItem) {
        scrollToTop()
    }

    private fun initSwipeRefresh() {
        binding.swipeRefresh.apply {
            when (args.filter) {
                is EntriesFilter.NotBookmarked, is EntriesFilter.BelongToFeed -> {
                    isEnabled = true
                    setOnRefreshListener { model.onPullRefresh() }
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

    private fun FragmentEntriesBinding.setState(state: EntriesModel.State) {
        animateVisibilityChanges(
            views = listOf(toolbar, progress, message, retry, swipeRefresh),
            visibleViews = when (state) {
                is EntriesModel.State.InitialSync -> listOf(toolbar, progress)
                is EntriesModel.State.FailedToSync -> listOf(toolbar, retry)
                is EntriesModel.State.LoadingCachedEntries -> listOf(toolbar, progress)
                is EntriesModel.State.ShowingCachedEntries -> listOf(toolbar, swipeRefresh)
            },
        )

        updateToolbar(state)

        when (state) {
            is EntriesModel.State.InitialSync -> {
                if (state.message.isNotEmpty()) {
                    message.showSmooth()
                    message.text = state.message
                }
            }

            is EntriesModel.State.FailedToSync -> {
                retry.setOnClickListener { model.onRetry() }
                showErrorDialog(state.cause)
            }

            EntriesModel.State.LoadingCachedEntries -> {}

            is EntriesModel.State.ShowingCachedEntries -> {
                swipeRefresh.isRefreshing = state.showBackgroundProgress

                if (state.entries.isEmpty()) {
                    message.showSmooth()
                    message.text = getEmptyMessage()
                }

                seenEntries.clear()
                adapter.submitList(state.entries) { if (state.scrollToTop) scrollToTop() }

                if (
                    state.conf.mark_scrolled_entries_as_read
                    && (args.filter is EntriesFilter.NotBookmarked || args.filter is EntriesFilter.BelongToFeed)
                ) {
                    binding.list.addOnScrollListener(trackingListener)
                } else {
                    binding.list.removeOnScrollListener(trackingListener)
                }
            }
        }
    }

    private fun updateToolbar(state: EntriesModel.State) {
        binding.toolbar.apply {
            when (args.filter!!) {
                EntriesFilter.Bookmarked -> setTitle(R.string.bookmarks)
                EntriesFilter.NotBookmarked -> setTitle(R.string.news)

                is EntriesFilter.BelongToFeed -> {
                    binding.toolbar.apply {
                        navigationIcon = DrawerArrowDrawable(context).also { it.progress = 1f }
                        setNavigationOnClickListener { findNavController().popBackStack() }
                    }

                    if (state is EntriesModel.State.ShowingCachedEntries) {
                        title = state.feed?.title
                    }
                }
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
            findNavController().navigate(R.id.action_entriesFragment_to_searchFragment)
            true
        }
    }

    private fun updateShowReadEntriesButton(state: EntriesModel.State) {
        val button = binding.toolbar.menu!!.findItem(R.id.showOpenedEntries)
        button.isVisible = getShowReadEntriesButtonVisibility()

        if (state !is EntriesModel.State.ShowingCachedEntries) {
            button.isVisible = false
            return
        }

        if (state.conf.show_read_entries) {
            button.setIcon(R.drawable.ic_baseline_visibility_24)
            button.title = getString(R.string.hide_read_news)
            touchHelper?.attachToRecyclerView(null)
        } else {
            button.setIcon(R.drawable.ic_baseline_visibility_off_24)
            button.title = getString(R.string.show_read_news)
            touchHelper?.attachToRecyclerView(binding.list)
        }

        button.setOnMenuItemClickListener {
            model.saveConf { it.copy(show_read_entries = !it.show_read_entries) }
            true
        }
    }

    private fun updateSortOrderButton(state: EntriesModel.State) {
        val button = binding.toolbar.menu.findItem(R.id.sort)

        if (state !is EntriesModel.State.ShowingCachedEntries) {
            button.isVisible = false
            return
        } else {
            button.isVisible = true
        }

        when (state.conf.sort_order) {
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
            scrollToTop()
            true
        }
    }

    private fun updateMarkAllAsReadButton() {
        binding.toolbar.menu!!.findItem(R.id.markAllAsRead).setOnMenuItemClickListener {
            model.markAllAsRead()
            true
        }
    }

    private fun updateSettingsButton() {
        binding.toolbar.menu!!.findItem(R.id.settings).setOnMenuItemClickListener {
            findNavController().navigate(EntriesFragmentDirections.actionEntriesFragmentToSettingsFragment())
            true
        }
    }

    private fun scrollToTop() {
        binding.list.layoutManager?.scrollToPosition(0)
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

    private fun onListItemClick(item: EntriesAdapter.Item) {
        model.setRead(
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
            val action = EntriesFragmentDirections.actionEntriesFragmentToEntryFragment(item.id)
            findNavController().navigate(action)
        }
    }

    private fun createTouchHelper(): ItemTouchHelper? {
        return when (args.filter) {
            EntriesFilter.NotBookmarked, is EntriesFilter.BelongToFeed -> {
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
                                    action = { model.setRead(listOf(entry.id), true) },
                                    undoAction = { model.setRead(listOf(entry.id), false) },
                                )
                            }

                            ItemTouchHelper.RIGHT -> {
                                showSnackbar(
                                    actionText = R.string.bookmarked,
                                    action = { model.setBookmarked(entry.id, true) },
                                    undoAction = { model.setBookmarked(entry.id, false) },
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
                                    action = { model.setBookmarked(entry.id, false) },
                                    undoAction = { model.setBookmarked(entry.id, true) },
                                )
                            }

                            ItemTouchHelper.RIGHT -> {
                                showSnackbar(
                                    actionText = R.string.removed_from_bookmarks,
                                    action = { model.setBookmarked(entry.id, false) },
                                    undoAction = { model.setBookmarked(entry.id, true) },
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