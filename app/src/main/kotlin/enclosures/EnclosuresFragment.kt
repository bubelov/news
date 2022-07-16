package enclosures

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEnclosuresBinding
import db.Link
import dialog.showErrorDialog
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class EnclosuresFragment : Fragment() {

    private val model: EnclosuresModel by viewModel()

    private var _binding: FragmentEnclosuresBinding? = null
    private val binding get() = _binding!!

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
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEnclosuresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.list.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@EnclosuresFragment.adapter
            addItemDecoration(CardListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))
        }

        model.getEnclosures()
            .onEach { (binding.list.adapter as EnclosuresAdapter).submitList(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun downloadAudioEnclosure(enclosure: Link) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { model.downloadAudioEnclosure(enclosure) }
                .onFailure { showErrorDialog(it) }
        }
    }

    fun playAudioEnclosure(enclosure: Link) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(enclosure.extCacheUri!!, enclosure.type)

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
            runCatching { model.deleteEnclosure(enclosure) }
                .onFailure { showErrorDialog(it) }
        }
    }

    private class CardListAdapterDecoration(
        private val gapPx: Int
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
}