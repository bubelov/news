package search

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentSearchBinding
import com.google.android.material.internal.TextWatcherAdapter
import common.BaseFragment
import common.CardListAdapterDecoration
import common.hideKeyboard
import common.screenWidth
import common.showKeyboard
import db.EntryWithoutContent
import db.Link
import entries.EntriesAdapter
import entries.EntriesAdapterCallback
import entries.EntriesAdapterItem
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchFragment : BaseFragment() {

    private val args by lazy { SearchFragmentArgs.fromBundle(requireArguments()) }

    private val model: SearchModel by viewModel()

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val adapter = EntriesAdapter(
        callback = object : EntriesAdapterCallback {
            override fun onItemClick(item: EntriesAdapterItem) {
//                lifecycleScope.launchWhenResumed {
//                    val entry = model.getEntry(item.id).first() ?: return@launchWhenResumed
//                    val feed = model.getFeed(entry.feedId).first() ?: return@launchWhenResumed
//
//                    model.setRead(listOf(entry.id), true)
//
//                    if (feed.openEntriesInBrowser) {
//                        openUrl(
//                            url = entry.links.first { it.rel == "alternate" }.href,
//                            useBuiltInBrowser = model.getConf().first().useBuiltInBrowser
//                        )
//                    } else {
//                        val action =
//                            SearchFragmentDirections.actionSearchFragmentToEntryFragment(item.id)
//                        findNavController().navigate(action)
//                    }
//                }
            }

            override fun onDownloadAudioEnclosureClick(entry: EntryWithoutContent, link: Link) {
//                lifecycleScope.launchWhenResumed {
//                    runCatching {
//                        model.downloadPodcast(item.id)
//                    }.onFailure {
//                        showErrorDialog(it)
//                    }
//                }
            }

            override fun onPlayAudioEnclosureClick(entry: EntryWithoutContent, link: Link) {
//                lifecycleScope.launch {
//                    runCatching {
//                        val entry = model.getEntry(item.id).first() ?: return@launch
//                        model.setRead(listOf(entry.id), true)
//
//                        openCachedPodcast(
//                            cacheUri = model.getCachedPodcastUri(entry.id),
//                            enclosureLinkType = entry.links.first { it.rel == "enclosure" }.type,
//                        )
//                    }.onFailure {
//                        showErrorDialog(it)
//                    }
//                }
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

        binding.toolbar.navigationIcon = DrawerArrowDrawable(context).also { it.progress = 1f }

        binding.toolbar.setNavigationOnClickListener {
            requireContext().hideKeyboard(binding.query)
            findNavController().popBackStack()
        }

        model.query
            .onEach { binding.clear.isVisible = it.isNotEmpty() }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        binding.query.addTextChangedListener(object : TextWatcherAdapter() {
            override fun afterTextChanged(s: Editable) {
                model.setQuery(s.toString())
            }
        })

        binding.clear.setOnClickListener {
            binding.query.setText("")
        }

        binding.query.requestFocus()
        requireContext().showKeyboard()

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

        model.setFilter(args.filter!!)

        model.state
            .onEach { setState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setState(state: SearchModel.State) {
        binding.progress.isVisible = false

        when (state) {
            SearchModel.State.Loading -> {
                binding.progress.isVisible = true
            }
            is SearchModel.State.Loaded -> {
                binding.progress.isVisible = false
                val items = state.items
                binding.message.isVisible = items.isEmpty()
                adapter.submitList(items)
            }
        }
    }
}