package search

import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.feedk.AtomLinkRel
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentSearchBinding
import com.google.android.material.internal.TextWatcherAdapter
import entries.EntriesAdapter
import entries.EntriesAdapterItem
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import navigation.hideKeyboard
import navigation.openUrl
import navigation.showKeyboard
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchFragment : Fragment() {

    private val args by lazy { SearchFragmentArgs.fromBundle(requireArguments()) }

    private val model: SearchModel by viewModel()

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val adapter by lazy {
        EntriesAdapter(requireActivity()) { onListItemClick(it) }
    }

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

        binding.toolbar.setNavigationOnClickListener {
            requireContext().hideKeyboard(binding.query)
            findNavController().popBackStack()
        }

        binding.query.addTextChangedListener(object : TextWatcherAdapter() {
            override fun afterTextChanged(s: Editable) {
                binding.clear.isVisible = s.isNotEmpty()

                model.setArgs(
                    SearchModel.Args(
                        filter = args.filter!!,
                        query = s.toString(),
                    )
                )
            }
        })

        binding.query.requestFocus()
        requireContext().showKeyboard()

        binding.clear.setOnClickListener { binding.query.setText("") }

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

        model.state
            .onEach { setState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setState(state: SearchModel.State) {
        when (state) {
            SearchModel.State.QueryIsEmpty,
            SearchModel.State.QueryIsTooShort -> {
                binding.list.isVisible = false
                binding.progress.isVisible = false
                binding.message.isVisible = false
            }

            SearchModel.State.RunningQuery -> {
                binding.list.isVisible = false
                binding.progress.isVisible = true
                binding.message.isVisible = false
            }

            is SearchModel.State.ShowingQueryResults -> {
                binding.list.isVisible = true
                adapter.submitList(state.items)
                binding.progress.isVisible = false
                binding.message.isVisible = false
            }
        }
    }

    private fun onListItemClick(item: EntriesAdapterItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            model.markAsRead(item.entry.id)

            if (item.feed.openEntriesInBrowser) {
                openUrl(
                    url = item.entry.links.first { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }.href.toString(),
                    useBuiltInBrowser = item.conf.useBuiltInBrowser,
                )
            } else {
                val action = SearchFragmentDirections.actionSearchFragmentToEntryFragment(item.entry.id)
                findNavController().navigate(action)
            }
        }
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