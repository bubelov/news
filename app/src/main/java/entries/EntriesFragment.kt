package entries

import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import common.showDialog
import common.showErrorDialog
import co.appreactor.news.databinding.FragmentEntriesBinding
import com.google.android.material.snackbar.Snackbar
import common.Preferences
import entriesenclosures.openCachedEnclosure
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class EntriesFragment : Fragment() {

    private val args by lazy {
        EntriesFragmentArgs.fromBundle(requireArguments())
    }

    private val model: EntriesFragmentModel by viewModel()

    private var _binding: FragmentEntriesBinding? = null
    private val binding get() = _binding!!

    private val snackbar by lazy {
        Snackbar.make(
            binding.root,
            "",
            Snackbar.LENGTH_SHORT
        ).apply {
            anchorView = requireActivity().findViewById(R.id.bottomNavigation)
        }
    }

    private var shouldScrollToTop = false

    private val adapter = EntriesAdapter(
        scope = lifecycleScope,
        callback = object : EntriesAdapterCallback {
            override fun onItemClick(item: EntriesAdapterItem) {
                val action = EntriesFragmentDirections.actionEntriesFragmentToEntryFragment(item.id)
                findNavController().navigate(action)
            }

            override fun onDownloadPodcastClick(item: EntriesAdapterItem) {
                lifecycleScope.launchWhenResumed {
                    runCatching {
                        model.downloadEnclosure(item.id)
                    }.onFailure {
                        Timber.e(it)
                        showDialog(R.string.error, it.message ?: "")
                    }
                }
            }

            override fun onPlayPodcastClick(item: EntriesAdapterItem) {
                lifecycleScope.launch {
                    runCatching {
                        requireContext().openCachedEnclosure(model.getEntry(item.id)!!)
                    }.onFailure {
                        Timber.e(it)
                        showDialog(R.string.error, it.message ?: "")
                    }
                }
            }
        }
    ).apply {
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (args.feedId.isNotEmpty()) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)

            binding.toolbar.setNavigationOnClickListener {
                findNavController().popBackStack()
            }

            lifecycleScope.launchWhenResumed {
                val feed = model.getFeed(args.feedId)
                binding.toolbar.title = feed?.title
            }

            binding.swipeRefresh.isEnabled = false
        } else {
            binding.swipeRefresh.setOnRefreshListener {
                lifecycleScope.launch {
                    runCatching {
                        model.performFullSync()
                    }.onFailure {
                        showErrorDialog(it)
                    }

                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }

        val showOpenedEntriesMenuItem = binding.toolbar.menu.findItem(R.id.showOpenedEntries)

        lifecycleScope.launchWhenResumed {
            model.getShowOpenedEntries().collect { showOpenedEntries ->
                if (showOpenedEntries) {
                    showOpenedEntriesMenuItem.setIcon(R.drawable.ic_baseline_visibility_24)
                    showOpenedEntriesMenuItem.title = getString(R.string.hide_opened_news)
                } else {
                    showOpenedEntriesMenuItem.setIcon(R.drawable.ic_baseline_visibility_off_24)
                    showOpenedEntriesMenuItem.title = getString(R.string.show_opened_news)
                }
            }
        }

        showOpenedEntriesMenuItem.setOnMenuItemClickListener {
            lifecycleScope.launchWhenResumed {
                model.setShowOpenedEntries(!model.getShowOpenedEntries().first())
            }

            true
        }

        val sortOrderMenuItem = binding.toolbar.menu.findItem(R.id.sort)

        lifecycleScope.launchWhenResumed {
            model.getSortOrder().collect { sortOrder ->
                when (sortOrder) {
                    Preferences.SORT_ORDER_ASCENDING -> {
                        sortOrderMenuItem.setIcon(R.drawable.ic_clock_forward)
                        sortOrderMenuItem.title = getString(R.string.show_newest_first)
                    }

                    Preferences.SORT_ORDER_DESCENDING -> {
                        sortOrderMenuItem.setIcon(R.drawable.ic_clock_back)
                        sortOrderMenuItem.title = getString(R.string.show_oldest_first)
                    }
                }
            }
        }

        sortOrderMenuItem.setOnMenuItemClickListener {
            lifecycleScope.launchWhenResumed {
                shouldScrollToTop = true

                when (model.getSortOrder().first()) {
                    Preferences.SORT_ORDER_ASCENDING -> model.setSortOrder(Preferences.SORT_ORDER_DESCENDING)
                    Preferences.SORT_ORDER_DESCENDING -> model.setSortOrder(Preferences.SORT_ORDER_ASCENDING)
                }
            }

            true
        }

        lifecycleScope.launchWhenResumed {
            showEntries()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private suspend fun showEntries() {
        binding.progress.isVisible = true

        lifecycleScope.launchWhenResumed {
            model.syncMessage.collect {
                binding.progressMessage.isVisible = it.isNotBlank()
                binding.progressMessage.text = it
            }
        }

        runCatching {
            model.performInitialSyncIfNecessary()
        }.onFailure {
            Timber.e(it)
            binding.progress.isVisible = false
            showDialog(R.string.error, it.message ?: "")
        }

        binding.listView.setHasFixedSize(true)
        binding.listView.layoutManager = LinearLayoutManager(context)
        binding.listView.adapter = adapter
        binding.listView.addItemDecoration(
            EntriesAdapterDecoration(
                resources.getDimensionPixelSize(
                    R.dimen.entries_cards_gap
                )
            )
        )

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
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
                val entry = adapter.currentList[viewHolder.bindingAdapterPosition]

                if (direction == ItemTouchHelper.LEFT) {
                    lifecycleScope.launchWhenResumed {
                        runCatching {
                            model.markAsOpened(entry.id)
                            snackbar.setText(R.string.marked_as_read)
                            snackbar.show()
                        }.onFailure {
                            Timber.e(it)
                        }
                    }
                }

                if (direction == ItemTouchHelper.RIGHT) {
                    lifecycleScope.launchWhenResumed {
                        runCatching {
                            model.markAsBookmarked(entry.id)
                            snackbar.setText(R.string.bookmarked)
                            snackbar.show()
                        }.onFailure {
                            Timber.e(it)
                        }

                    }
                }
            }
        })

        touchHelper.attachToRecyclerView(binding.listView)

        val displayMetrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireContext().display?.getRealMetrics(displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        }

        adapter.screenWidth = displayMetrics.widthPixels

        model
            .getEntries(args.feedId)
            .catch {
                Timber.e(it)
                binding.progress.isVisible = false
                showDialog(R.string.error, it.message ?: "")
            }
            .collect { entries ->
                if (model.isInitialSyncCompleted()) {
                    binding.progress.isVisible = false
                }

                binding.empty.isVisible = entries.isEmpty() && model.isInitialSyncCompleted()

                adapter.submitList(entries) {
                    if (shouldScrollToTop) {
                        shouldScrollToTop = false

                        (binding.listView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                            0,
                            0
                        )
                    }
                }
            }
    }
}