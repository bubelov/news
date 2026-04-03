package org.vestifeed.search

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import org.vestifeed.navigation.AppFragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.vestifeed.parser.AtomLinkRel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vestifeed.R
import org.vestifeed.anim.animateVisibilityChanges
import org.vestifeed.app.App
import org.vestifeed.app.db
import org.vestifeed.app.sync
import org.vestifeed.db.SelectByQuery
import org.vestifeed.databinding.FragmentSearchBinding
import org.vestifeed.db.table.Conf
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entries.EntriesAdapter
import org.vestifeed.entry.EntryFragment
import org.vestifeed.navigation.hideKeyboard
import org.vestifeed.navigation.openUrl
import org.vestifeed.navigation.showKeyboard
import org.vestifeed.sync.Sync
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SearchFragment : AppFragment() {

    private val db by lazy { (requireContext().applicationContext as App).db }

    private val args = MutableStateFlow<Args?>(null)

    private val _state = MutableStateFlow<State>(State.QueryIsEmpty)
    private val state = _state.asStateFlow()

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
            args.filterNotNull().collect { args ->
                val conf = db.conf.select()
                if (args.query.length < 3) {
                    _state.update { State.QueryIsTooShort }
                    return@collect
                }

                _state.update { State.RunningQuery }

                val rows = db().entry.selectByFtsQuery(args.query)
                val items = rows.map { it.toItem(conf) }

                _state.update { State.ShowingQueryResults(items) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                state.collect { binding.setState(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setArgs(args: Args) {
        this.args.update { args }
    }

    private fun markAsRead(entryId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                db().entry.updateReadAndReadSynced(
                    id = entryId,
                    extRead = true,
                    extReadSynced = false,
                )

                sync().runInBackground(
                    Sync.Args(
                        syncFeeds = false,
                        syncFlags = true,
                        syncEntries = false,
                    )
                )
            }.onFailure { showErrorDialog(it) }
        }
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            hideKeyboard(binding.query)
            parentFragmentManager.popBackStack()
        }

        binding.query.addTextChangedListener(
            afterTextChanged = {
                binding.clear.isVisible = it!!.isNotEmpty()
                setArgs(Args(query = it.toString()))
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

    private fun FragmentSearchBinding.setState(state: State) {
        animateVisibilityChanges(
            views = listOf(toolbar, list, progress, message),
            visibleViews = when (state) {
                is State.QueryIsEmpty,
                is State.QueryIsTooShort -> listOf(toolbar)

                is State.RunningQuery -> listOf(toolbar, progress)
                is State.ShowingQueryResults -> listOf(toolbar, list)
            },
        )

        if (state is State.ShowingQueryResults) {
            adapter.submitList(state.items)
        }
    }

    private fun onListItemClick(item: EntriesAdapter.Item) {
        markAsRead(item.id)

        if (item.openInBrowser) {
            val htmlLink =
                item.links.firstOrNull { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }

            if (htmlLink != null) {
                openUrl(
                    url = htmlLink.href.toString(),
                    useBuiltInBrowser = item.useBuiltInBrowser,
                )
            } else {
                parentFragmentManager.commit {
                    replace(
                        R.id.fragmentContainerView,
                        EntryFragment::class.java,
                        bundleOf("entryId" to item.id),
                    )
                    addToBackStack(null)
                }
            }
        } else {
            parentFragmentManager.commit {
                replace(
                    R.id.fragmentContainerView,
                    EntryFragment::class.java,
                    bundleOf("entryId" to item.id),
                )
                addToBackStack(null)
            }
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

    data class Args(
        val query: String,
    )

    sealed class State {
        object QueryIsEmpty : State()
        object QueryIsTooShort : State()
        object RunningQuery : State()
        data class ShowingQueryResults(val items: List<EntriesAdapter.Item>) : State()
    }

    private fun SelectByQuery.toItem(conf: Conf): EntriesAdapter.Item {
        return EntriesAdapter.Item(
            id = id,
            showImage = extShowPreviewImages || conf.showPreviewImages,
            cropImage = conf.cropPreviewImages,
            imageUrl = extOpenGraphImageUrl,
            imageWidth = extOpenGraphImageWidth,
            imageHeight = extOpenGraphImageHeight,
            title = title,
            subtitle = "$feedTitle · ${DATE_TIME_FORMAT.format(published)}",
            summary = summary ?: "",
            read = extRead,
            openInBrowser = extOpenEntriesInBrowser,
            useBuiltInBrowser = conf.useBuiltInBrowser,
            links = links,
        )
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}