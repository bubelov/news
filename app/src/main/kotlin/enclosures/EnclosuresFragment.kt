package enclosures

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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class EnclosuresFragment : Fragment() {

    private val model: EnclosuresModel by viewModel()

    private var _binding: FragmentEnclosuresBinding? = null
    private val binding get() = _binding!!

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

        binding.list.layoutManager = LinearLayoutManager(requireContext())

        binding.list.adapter = EnclosuresAdapter(object : EnclosuresAdapter.Callback {
            override fun onDownloadClick(item: EnclosuresAdapter.Item) {
                TODO()
            }

            override fun onPlayClick(item: EnclosuresAdapter.Item) {
                TODO()
            }

            override fun onDeleteClick(item: EnclosuresAdapter.Item) {
                viewLifecycleOwner.lifecycleScope.launch {
                    model.deleteEnclosure(item.entryId, item.enclosure)
                }
            }
        })

        binding.list.addItemDecoration(CardListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))

        model.getEnclosures()
            .onEach { (binding.list.adapter as EnclosuresAdapter).submitList(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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