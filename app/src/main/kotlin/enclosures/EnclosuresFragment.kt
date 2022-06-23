package enclosures

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEnclosuresBinding
import common.AppFragment
import common.ListAdapterDecoration
import common.sharedToolbar
import db.Link
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class EnclosuresFragment : AppFragment() {

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
}