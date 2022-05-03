package search

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentSearchBinding
import com.google.android.material.internal.TextWatcherAdapter
import common.AppFragment
import common.CardListAdapterDecoration
import common.hideKeyboard
import common.openCachedPodcast
import common.openUrl
import common.screenWidth
import common.showErrorDialog
import common.showKeyboard
import entries.EntriesAdapter
import entries.EntriesAdapterCallback
import entries.EntriesAdapterItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchFragment : AppFragment() {

    private val args by lazy {
        SearchFragmentArgs.fromBundle(requireArguments())
    }

    private val model: SearchViewModel by viewModel()

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val adapter = EntriesAdapter(
        callback = object : EntriesAdapterCallback {
            override fun onItemClick(item: EntriesAdapterItem) {
                lifecycleScope.launchWhenResumed {
                    val entry = model.getEntry(item.id).first() ?: return@launchWhenResumed
                    val feed = model.getFeed(entry.feedId).first() ?: return@launchWhenResumed

                    model.setRead(listOf(entry.id), true)

                    if (feed.openEntriesInBrowser) {
                        openUrl(entry.link, model.getConf().first().useBuiltInBrowser)
                    } else {
                        val action =
                            SearchFragmentDirections.actionSearchFragmentToEntryFragment(item.id)
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
                        val entry = model.getEntry(item.id).first() ?: return@launch
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
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchPanel.isVisible = true

        toolbar?.setupUpNavigation(hideKeyboard = true)

        lifecycleScope.launch {
            model.searchString.collect {
                searchPanelClearButton.isVisible = it.isNotEmpty()
            }
        }

        searchPanelText.addTextChangedListener(object : TextWatcherAdapter() {
            override fun afterTextChanged(s: Editable) {
                model.searchString.value = s.toString()
            }
        })

        searchPanelClearButton.setOnClickListener {
            searchPanelText.setText("")
        }

        searchPanelText.requestFocus()
        requireContext().showKeyboard()

        lifecycleScope.launch {
            model.showProgress.collect {
                binding.progress.isVisible = it
            }
        }

        lifecycleScope.launch {
            model.showEmpty.collect {
                binding.message.isVisible = it
            }
        }

        binding.list.setHasFixedSize(true)
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapter
        binding.list.addItemDecoration(
            CardListAdapterDecoration(
                resources.getDimensionPixelSize(
                    R.dimen.entries_cards_gap
                )
            )
        )

        adapter.screenWidth = screenWidth()

        lifecycleScope.launch {
            model.searchResults.collect { results ->
                adapter.submitList(results)
            }
        }

        lifecycleScope.launch {
            model.onViewCreated(args.filter!!)
        }
    }

    override fun onDestroyView() {
        searchPanel.isVisible = false
        requireContext().hideKeyboard(searchPanelText)
        super.onDestroyView()
    }
}