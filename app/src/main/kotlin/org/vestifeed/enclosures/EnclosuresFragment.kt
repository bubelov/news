package org.vestifeed.enclosures

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import org.vestifeed.navigation.AppFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.vestifeed.parser.AtomLinkRel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vestifeed.R
import org.vestifeed.app.App
import org.vestifeed.app.db
import org.vestifeed.databinding.FragmentEnclosuresBinding
import org.vestifeed.db.table.Link
import org.vestifeed.dialog.showErrorDialog
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EnclosuresFragment : AppFragment() {

    private var _binding: FragmentEnclosuresBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { (requireContext().applicationContext as App).db }
    private val enclosuresRepo by lazy { EnclosuresRepo(requireContext(), db) }

    private val _state = MutableStateFlow<State>(State.LoadingEnclosures)
    private val state = _state.asStateFlow()

    val adapter = EnclosuresAdapter(object : EnclosuresAdapter.Callback {
        override fun onDownloadClick(item: EnclosuresAdapter.Item) {
            downloadAudioEnclosure(item.enclosure)
        }

        override fun onPlayClick(item: EnclosuresAdapter.Item) {
            playAudioEnclosure(item.enclosure)
        }

        override fun onDeleteClick(item: EnclosuresAdapter.Item) {
            deleteEnclosure(item.enclosure)
        }
    })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEnclosuresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        binding.list.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@EnclosuresFragment.adapter
            addItemDecoration(CardListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            enclosuresRepo.deletePartialDownloads()

            flowOf(db().entry.selectCount()).collect {
                val entries = db().entry.selectAllLinksPublishedAndTitle()
                val enclosures = mutableListOf<EnclosuresAdapter.Item>()

                entries.forEach { entry ->
                    val entryEnclosures = entry.links.filter { it.rel is AtomLinkRel.Enclosure }

                    enclosures += entryEnclosures.map {
                        EnclosuresAdapter.Item(
                            entryId = it.entryId!!,
                            enclosure = it,
                            primaryText = entry.title,
                            secondaryText = DATE_TIME_FORMAT.format(entry.published),
                        )
                    }
                }

                _state.update { State.ShowingEnclosures(enclosures) }
            }
        }

        state.onEach {
            when (it) {
                is State.LoadingEnclosures -> {
                    binding.progress.isVisible = true
                    binding.list.isVisible = false
                }

                is State.ShowingEnclosures -> {
                    binding.progress.isVisible = false
                    binding.list.isVisible = true
                    adapter.submitList(it.items)
                }
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun downloadAudioEnclosure(enclosure: Link) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { enclosuresRepo.downloadAudioEnclosure(enclosure) }
                .onFailure { showErrorDialog(it) }
        }
    }

    fun playAudioEnclosure(enclosure: Link) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(enclosure.extCacheUri!!.toUri(), enclosure.type)

        runCatching {
            startActivity(intent)
        }.onFailure {
            if (it is ActivityNotFoundException) {
                showErrorDialog(getString(R.string.you_have_no_apps_which_can_play_this_podcast))
            } else {
                showErrorDialog(it)
            }
        }
    }

    private fun deleteEnclosure(enclosure: Link) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { enclosuresRepo.deleteFromCache(enclosure) }
                .onFailure { showErrorDialog(it) }
        }
    }

    private class CardListAdapterDecoration(
        private val gapPx: Int,
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val position = parent.getChildAdapterPosition(view)

            val bottomGap = if (position == (parent.adapter?.itemCount ?: 0) - 1) {
                gapPx
            } else {
                0
            }

            outRect.set(gapPx, gapPx, gapPx, bottomGap)
        }
    }

    sealed class State {
        object LoadingEnclosures : State()
        data class ShowingEnclosures(val items: List<EnclosuresAdapter.Item>) : State()
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}