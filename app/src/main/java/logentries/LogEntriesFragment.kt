package logentries

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentLogEntriesBinding
import common.*
import kotlinx.coroutines.flow.collect
import org.koin.android.viewmodel.ext.android.viewModel

class LogEntriesFragment : Fragment() {

    private val model: LogEntriesViewModel by viewModel()

    private var _binding: FragmentLogEntriesBinding? = null
    private val binding get() = _binding!!

    private val adapter = LogEntriesAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogEntriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        resetToolbar()

        toolbar().apply {
            setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
            setNavigationOnClickListener { findNavController().popBackStack() }
            setTitle(R.string.event_log)
            inflateMenu(R.menu.menu_log_entries)

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.delete -> lifecycleScope.launchWhenResumed { model.deleteAllItems() }
                }

                true
            }
        }

        binding.apply {
            list.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(requireContext())
                adapter = this@LogEntriesFragment.adapter
                addItemDecoration(ListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))
            }

            lifecycleScope.launchWhenResumed { model.onViewReady() }

            lifecycleScope.launchWhenResumed {
                model.state.collect { state ->
                    when (state) {
                        LogEntriesViewModel.State.Idle -> {

                        }

                        LogEntriesViewModel.State.Loading -> {
                            list.isVisible = false
                            progress.show(animate = true)
                            empty.hide()
                        }

                        is LogEntriesViewModel.State.Loaded -> {
                            if (state.items.isEmpty()) {
                                list.hide()
                                progress.hide()
                                empty.show(animate = true)
                            } else {
                                list.show()
                                adapter.submitList(state.items)
                                progress.hide()
                                empty.hide()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}