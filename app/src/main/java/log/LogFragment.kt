package log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentLogBinding
import common.AppFragment
import common.ListAdapterDecoration
import common.hide
import common.show
import common.showErrorDialog
import kotlinx.coroutines.flow.collect
import org.koin.android.viewmodel.ext.android.viewModel

class LogFragment : AppFragment() {

    private val model: LogViewModel by viewModel()

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    private val adapter = LogAdapter {
        findNavController().navigate(
            LogFragmentDirections.actionLogFragmentToExceptionFragment(it.id)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.apply {
            setupUpNavigation()
            setTitle(R.string.event_log)
            inflateMenu(R.menu.menu_log_entries)

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.delete -> lifecycleScope.launchWhenResumed { model.deleteAll() }
                }

                true
            }
        }

        binding.apply {
            list.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(requireContext())
                adapter = this@LogFragment.adapter
                addItemDecoration(ListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))
            }

            lifecycleScope.launchWhenResumed { model.onViewReady() }

            lifecycleScope.launchWhenResumed {
                model.state.collect { state ->
                    when (state) {
                        null, is LogViewModel.State.Loading, LogViewModel.State.Deleting -> {
                            list.hide()
                            progress.show(animate = true)
                            empty.hide()
                        }

                        is LogViewModel.State.Loaded -> {
                            progress.hide()

                            if (state.items.isEmpty()) {
                                list.hide()
                                empty.show(animate = true)
                            } else {
                                list.show()
                                adapter.submitList(state.items)
                                empty.hide()
                            }
                        }

                        is LogViewModel.State.FailedToLoad -> {
                            showErrorDialog(state.reason) {
                                model.reload()
                            }
                        }

                        is LogViewModel.State.FailedToDelete -> {
                            showErrorDialog(state.reason) {
                                model.reload()
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