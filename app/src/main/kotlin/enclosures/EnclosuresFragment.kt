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

        toolbar?.apply {
            setupUpNavigation()
            setTitle(R.string.enclosures)
        }

        initList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initList() = binding.list.apply {
        setHasFixedSize(true)
        layoutManager = LinearLayoutManager(requireContext())

        adapter = EnclosuresAdapter(object : EnclosuresAdapter.Listener {
            override fun onDeleteClick(item: EnclosuresAdapter.Item) {
                lifecycleScope.launchWhenResumed {
                    model.deleteEnclosure(item.entryId)
                    (adapter as EnclosuresAdapter).submitList(model.getEnclosures())
                }
            }
        })

        addItemDecoration(ListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))

        lifecycleScope.launchWhenResumed {
            (adapter as EnclosuresAdapter).submitList(model.getEnclosures())
        }
    }
}