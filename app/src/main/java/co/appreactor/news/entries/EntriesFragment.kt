package co.appreactor.news.entries

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
import co.appreactor.news.common.showDialog
import co.appreactor.news.common.showErrorDialog
import co.appreactor.news.entriesenclosures.openCachedEnclosure
import kotlinx.android.synthetic.main.fragment_entries.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class EntriesFragment : Fragment() {

    private val model: EntriesFragmentModel by viewModel()

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
                    (listView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
                }
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_entries,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                runCatching {
                    model.performFullSync()
                }.onFailure {
                    showErrorDialog(it)
                }

                swipeRefresh.isRefreshing = false
            }
        }

        lifecycleScope.launchWhenResumed {
            showEntries()
        }
    }

    private suspend fun showEntries() {
        progress.isVisible = true

        lifecycleScope.launchWhenResumed {
            model.syncMessage.collect {
                progressMessage.isVisible = it.isNotBlank()
                progressMessage.text = it
            }
        }

        runCatching {
            model.performInitialSyncIfNecessary()
        }.onFailure {
            Timber.e(it)
            progress.isVisible = false
            showDialog(R.string.error, it.message ?: "")
        }

        listView.setHasFixedSize(true)
        listView.layoutManager = LinearLayoutManager(context)
        listView.adapter = adapter
        listView.addItemDecoration(EntriesAdapterDecoration(resources.getDimensionPixelSize(R.dimen.entries_cards_gap)))

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
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
                        }.onFailure {
                            Timber.e(it)
                        }

                    }
                }

                if (direction == ItemTouchHelper.RIGHT) {
                    lifecycleScope.launchWhenResumed {
                        runCatching {
                            model.markAsBookmarked(entry.id)
                        }.onFailure {
                            Timber.e(it)
                        }

                    }
                }
            }
        })

        touchHelper.attachToRecyclerView(listView)

        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        adapter.screenWidth = displayMetrics.widthPixels

        model
            .getEntries()
            .catch {
                Timber.e(it)
                progress.isVisible = false
                showDialog(R.string.error, it.message ?: "")
            }
            .collect { entries ->
                if (model.isInitialSyncCompleted()) {
                    progress.isVisible = false
                }

                empty.isVisible = entries.isEmpty() && model.isInitialSyncCompleted()

                adapter.submitList(entries)
            }
    }
}