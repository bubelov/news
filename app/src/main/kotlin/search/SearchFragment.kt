package search

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import anim.animateVisibilityChanges
import co.appreactor.feedk.AtomLinkRel
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentSearchBinding
import entries.EntriesAdapter
import kotlinx.coroutines.launch
import navigation.hideKeyboard
import navigation.openUrl
import navigation.showKeyboard
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchFragment : Fragment() {

    private val model: SearchModel by viewModel()

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val adapter by lazy {
        EntriesAdapter(requireActivity()) { onListItemClick(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initToolbar()
        initList()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { binding.setState(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            hideKeyboard(binding.query)
            findNavController().popBackStack()
        }

        binding.query.addTextChangedListener(
            afterTextChanged = {
                binding.clear.isVisible = it!!.isNotEmpty()
                model.setArgs(SearchModel.Args(query = it.toString()))
            }
        )

        binding.query.requestFocus()
        showKeyboard(binding.query)

        binding.clear.setOnClickListener { binding.query.setText("") }
    }

    private fun initList() {
        binding.list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@SearchFragment.adapter
            val cardsGapPx = resources.getDimensionPixelSize(R.dimen.entries_cards_gap)
            addItemDecoration(CardListAdapterDecoration(cardsGapPx))
        }
    }

    private fun FragmentSearchBinding.setState(state: SearchModel.State) {
        animateVisibilityChanges(
            views = listOf(toolbar, list, progress, message),
            visibleViews = when (state) {
                is SearchModel.State.QueryIsEmpty,
                is SearchModel.State.QueryIsTooShort -> listOf(toolbar)
                is SearchModel.State.RunningQuery -> listOf(toolbar, progress)
                is SearchModel.State.ShowingQueryResults -> listOf(toolbar, list)
            },
        )

        if (state is SearchModel.State.ShowingQueryResults) {
            adapter.submitList(state.items)
        }
    }

    private fun onListItemClick(item: EntriesAdapter.Item) {
        model.markAsRead(item.id)

        if (item.openInBrowser) {
            openUrl(
                url = item.links.first { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }.href.toString(),
                useBuiltInBrowser = item.useBuiltInBrowser,
            )
        } else {
            val action = SearchFragmentDirections.actionSearchFragmentToEntryFragment(item.id)
            findNavController().navigate(action)
        }
    }

    private class CardListAdapterDecoration(private val gapInPixels: Int) :
        RecyclerView.ItemDecoration() {

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