package enclosures

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEnclosuresBinding
import navigation.BaseFragment
import navigation.sharedToolbar
import db.Link
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class EnclosuresFragment : BaseFragment() {

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

        sharedToolbar()?.apply {
            setupUpNavigation()
            setTitle(R.string.enclosures)
        }

        binding.list.layoutManager = LinearLayoutManager(requireContext())

        binding.list.adapter = EnclosuresAdapter(object : EnclosuresAdapter.Listener {
            override fun onDeleteClick(item: Link) {
                viewLifecycleOwner.lifecycleScope.launch {
                    model.deleteEnclosure(item)
                }
            }
        })

        binding.list.addItemDecoration(ListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))

        model.getEnclosures()
            .onEach { (binding.list.adapter as EnclosuresAdapter).submitList(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ListAdapterDecoration(private val gapInPixels: Int) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val adapter = parent.adapter

            if (adapter == null || adapter.itemCount == 0) {
                super.getItemOffsets(outRect, view, parent, state)
                return
            }

            val position = parent.getChildLayoutPosition(view)

            val left = 0
            val top = if (position == 0) gapInPixels else 0
            val right = 0
            val bottom = if (position == adapter.itemCount - 1) gapInPixels else 0

            outRect.set(left, top, right, bottom)
        }
    }
}