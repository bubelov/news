package entries

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEntriesBinding
import com.google.android.material.snackbar.Snackbar
import common.*
import db.Entry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class EntriesFragment : Fragment() {

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
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.link)))
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
                            Timber.e(it)
                            showDialog(R.string.error, it.message ?: "")
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
            val displayMetrics = DisplayMetrics()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requireContext().display?.getRealMetrics(displayMetrics)
            } else {
                @Suppress("DEPRECATION")
                requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            }

            screenWidth = displayMetrics.widthPixels

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

    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            return makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val entryIndex = viewHolder.bindingAdapterPosition
            val entry = adapter.currentList[viewHolder.bindingAdapterPosition]

            when (direction) {
                ItemTouchHelper.LEFT -> {
                    when (args.filter) {
                        EntriesFilter.OnlyNotBookmarked -> {
                            dismissEntry(
                                entry = entry,
                                entryIndex = entryIndex,
                                actionText = R.string.marked_as_read,
                                action = { model.setRead(listOf(entry.id), true) },
                                undoAction = { model.setRead(listOf(entry.id), false) }
                            )
                        }

                        EntriesFilter.OnlyBookmarked -> {
                            dismissEntry(
                                entry = entry,
                                entryIndex = entryIndex,
                                actionText = R.string.removed_from_bookmarks,
                                action = { model.setBookmarked(entry.id, false) },
                                undoAction = { model.setBookmarked(entry.id, true) }
                            )
                        }

                        else -> Timber.e(Exception("Unexpected filter: ${args.filter}"))
                    }
                }

                ItemTouchHelper.RIGHT -> {
                    when (args.filter) {
                        EntriesFilter.OnlyNotBookmarked -> {
                            dismissEntry(
                                entry = entry,
                                entryIndex = entryIndex,
                                actionText = R.string.bookmarked,
                                action = { model.setBookmarked(entry.id, true) },
                                undoAction = { model.setBookmarked(entry.id, false) }
                            )
                        }

                        EntriesFilter.OnlyBookmarked -> {
                            dismissEntry(
                                entry = entry,
                                entryIndex = entryIndex,
                                actionText = R.string.removed_from_bookmarks,
                                action = { model.setBookmarked(entry.id, false) },
                                undoAction = { model.setBookmarked(entry.id, true) }
                            )
                        }

                        else -> Timber.e(Exception("Unexpected filter: ${args.filter}"))
                    }
                }
            }
        }
    })

    private lateinit var listLayoutManager: LinearLayoutManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initListView()

        lifecycleScope.launchWhenResumed {
            model.onViewReady(args.filter!!, sharedModel)
        }

        binding.retry.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                model.onRetry(sharedModel)
            }
        }

        when (val filter = args.filter) {
            is EntriesFilter.OnlyNotBookmarked -> {
                binding.swipeRefresh.setOnRefreshListener {
                    lifecycleScope.launch {
                        runCatching {
                            model.fetchEntriesFromApi()
                        }.onFailure {
                            showErrorDialog(it)
                        }
                    }
                }
            }

            is EntriesFilter.OnlyBookmarked -> {
                binding.toolbar.title = getString(R.string.bookmarks)
                binding.swipeRefresh.isEnabled = false
            }

            is EntriesFilter.OnlyFromFeed -> {
                binding.swipeRefresh.isEnabled = false

                binding.toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)

                binding.toolbar.setNavigationOnClickListener {
                    findNavController().popBackStack()
                }

                lifecycleScope.launchWhenResumed {
                    val feed = model.getFeed(filter.feedId)
                    binding.toolbar.title = feed?.title
                }
            }
        }

        initShowOpenedEntriesButton()
        initSortOrderButton()
        initSearchButton()
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

        if (runBlocking { model.getPreferences().markScrolledEntriesAsRead }) {
            model.setRead(
                entryIds = seenEntries.map { it.id },
                read = true,
                scope = requireActivity().lifecycleScope,
            )

            seenEntries.clear()
        }
    }

    private fun initSortOrderButton() {
        val sortOrderMenuItem = binding.toolbar.menu.findItem(R.id.sort)

        lifecycleScope.launchWhenResumed {
            val prefs = model.getPreferences()

            when (prefs.sortOrder) {
                PreferencesRepository.SORT_ORDER_ASCENDING -> {
                    sortOrderMenuItem.setIcon(R.drawable.ic_clock_forward)
                    sortOrderMenuItem.title = getString(R.string.show_newest_first)
                }

                PreferencesRepository.SORT_ORDER_DESCENDING -> {
                    sortOrderMenuItem.setIcon(R.drawable.ic_clock_back)
                    sortOrderMenuItem.title = getString(R.string.show_oldest_first)
                }
            }
        }

        sortOrderMenuItem.setOnMenuItemClickListener {
            lifecycleScope.launchWhenResumed {
                adapter.submitList(null)

                val preferences = model.getPreferences()

                val newSortOrder = when (preferences.sortOrder) {
                    PreferencesRepository.SORT_ORDER_ASCENDING -> PreferencesRepository.SORT_ORDER_DESCENDING
                    PreferencesRepository.SORT_ORDER_DESCENDING -> PreferencesRepository.SORT_ORDER_ASCENDING
                    else -> throw Exception()
                }

                model.savePreferences { sortOrder = newSortOrder }
                initSortOrderButton()
            }

            true
        }
    }

    private fun initShowOpenedEntriesButton() {
        val showOpenedEntriesMenuItem = binding.toolbar.menu.findItem(R.id.showOpenedEntries)
        showOpenedEntriesMenuItem.isVisible = getShowReadEntriesButtonVisibility()

        lifecycleScope.launchWhenResumed {
            val prefs = model.getPreferences()

            if (prefs.showOpenedEntries) {
                showOpenedEntriesMenuItem.setIcon(R.drawable.ic_baseline_visibility_24)
                showOpenedEntriesMenuItem.title = getString(R.string.hide_opened_news)
                touchHelper.attachToRecyclerView(null)
            } else {
                showOpenedEntriesMenuItem.setIcon(R.drawable.ic_baseline_visibility_off_24)
                showOpenedEntriesMenuItem.title = getString(R.string.show_opened_news)

                if (swipesAllowed()) {
                    touchHelper.attachToRecyclerView(binding.listView)
                }
            }
        }

        showOpenedEntriesMenuItem.setOnMenuItemClickListener {
            lifecycleScope.launchWhenResumed {
                adapter.submitList(null)
                val prefs = model.getPreferences()
                model.savePreferences { showOpenedEntries = !prefs.showOpenedEntries }
                initShowOpenedEntriesButton()
            }

            true
        }
    }

    private fun initSearchButton() {
        val searchMenuItem = binding.toolbar.menu.findItem(R.id.search)

        searchMenuItem.setOnMenuItemClickListener {
            findNavController().navigate(
                EntriesFragmentDirections.actionEntriesFragmentToSearchFragment(
                    args.filter!!
                )
            )
            true
        }
    }

    private fun initListView() {
        listLayoutManager = LinearLayoutManager(requireContext())

        val listItemDecoration = EntriesAdapterDecoration(
            resources.getDimensionPixelSize(R.dimen.entries_cards_gap)
        )

        binding.apply {
            listView.apply {
                layoutManager = listLayoutManager
                setHasFixedSize(true)
                adapter = this@EntriesFragment.adapter
                addItemDecoration(listItemDecoration)
            }
        }

        if (swipesAllowed()) {
            touchHelper.attachToRecyclerView(binding.listView)
        }

        lifecycleScope.launchWhenResumed {
            if (model.getPreferences().markScrolledEntriesAsRead
                && args.filter is EntriesFilter.OnlyNotBookmarked
            ) {
                markScrolledEntriesAsRead()
            }
        }

        showListItems()
    }

    private fun markScrolledEntriesAsRead() = lifecycleScope.launchWhenResumed {
        binding.listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {

            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                    return
                }

                if (listLayoutManager.findFirstVisibleItemPosition() == RecyclerView.NO_POSITION) {
                    return
                }

                if (listLayoutManager.findLastVisibleItemPosition() == RecyclerView.NO_POSITION) {
                    return
                }

                val visibleEntries =
                    (listLayoutManager.findFirstVisibleItemPosition()..listLayoutManager.findLastVisibleItemPosition()).map {
                        adapter.currentList[it]
                    }

                seenEntries.addAll(visibleEntries)

                val seenItemsOutOfRange =
                    seenEntries.filterNot { visibleEntries.contains(it) }
                seenEntries.removeAll(seenItemsOutOfRange)

                seenItemsOutOfRange.forEach {
                    if (!it.opened.value) {
                        it.opened.value = true
                        model.setRead(listOf(it.id), true)
                    }
                }
            }
        })
    }

    private fun showListItems() = lifecycleScope.launchWhenResumed {
        binding.apply {
            model.state.collectLatest { state ->
                Timber.d("State: ${state.javaClass.simpleName}")

                when (state) {
                    is EntriesViewModel.State.Inactive -> {
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
                        showDialog(R.string.error, state.error.message ?: "")
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
        }
    }

    private fun getShowReadEntriesButtonVisibility(): Boolean {
        return when (args.filter!!) {
            is EntriesFilter.OnlyNotBookmarked -> true
            is EntriesFilter.OnlyBookmarked -> false
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

    private fun swipesAllowed(): Boolean {
        return when (args.filter) {
            is EntriesFilter.OnlyNotBookmarked -> true
            is EntriesFilter.OnlyBookmarked -> true
            else -> false
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