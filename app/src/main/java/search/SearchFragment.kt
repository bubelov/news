package search

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentSearchBinding
import com.google.android.material.internal.TextWatcherAdapter
import common.hideKeyboard
import common.showKeyboard
import entries.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class SearchFragment : Fragment() {

    private val args by lazy {
        SearchFragmentArgs.fromBundle(requireArguments())
    }

    private val model: SearchViewModel by viewModel()

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val adapter = EntriesAdapter(
        scope = lifecycleScope,
        callback = object : EntriesAdapterCallback {
            override fun onItemClick(item: EntriesAdapterItem) {
                lifecycleScope.launchWhenResumed {
                    val entry = model.getEntry(item.id) ?: return@launchWhenResumed
                    val feed = model.getFeed(entry.feedId) ?: return@launchWhenResumed

                    model.setRead(entry.id)

                    if (feed.openEntriesInBrowser) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.link)))
                    } else {
                        val action =
                            SearchFragmentDirections.actionSearchFragmentToEntryFragment(item.id)
                        findNavController().navigate(action)
                    }
                }
            }

            override fun onDownloadPodcastClick(item: EntriesAdapterItem) {

            }

            override fun onPlayPodcastClick(item: EntriesAdapterItem) {

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
        binding.toolbar.setNavigationOnClickListener {
            requireContext().hideKeyboard(binding.searchInput)
            findNavController().popBackStack()
        }

        lifecycleScope.launch {
            model.searchString.collect {
                binding.clear.isVisible = it.isNotEmpty()
            }
        }

        binding.searchInput.addTextChangedListener(object : TextWatcherAdapter() {
            override fun afterTextChanged(s: Editable) {
                model.searchString.value = s.toString()
            }
        })

        binding.clear.setOnClickListener {
            binding.searchInput.setText("")
        }

        binding.searchInput.requestFocus()
        requireContext().showKeyboard()

        lifecycleScope.launch {
            model.showProgress.collect {
                binding.progress.isVisible = it
            }
        }

        lifecycleScope.launch {
            model.showEmpty.collect {
                binding.empty.isVisible = it
            }
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

        val displayMetrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireContext().display?.getRealMetrics(displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        }

        adapter.screenWidth = displayMetrics.widthPixels

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
        requireContext().hideKeyboard(binding.searchInput)
        super.onDestroyView()
    }
}