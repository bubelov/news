package logging

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
import common.showDialog
import co.appreactor.news.databinding.FragmentLoggedExceptionsBinding
import common.Result
import kotlinx.coroutines.flow.collect
import org.koin.android.viewmodel.ext.android.viewModel

class LoggedExceptionsFragment : Fragment() {

    private val model: LoggedExceptionsFragmentModel by viewModel()

    private var _binding: FragmentLoggedExceptionsBinding? = null
    private val binding get() = _binding!!

    private val adapter = LoggedExceptionsAdapter {
        showDialog(it.exceptionClass, it.stackTrace)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoggedExceptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.apply {
            setNavigationOnClickListener {
                findNavController().popBackStack()
            }

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.delete -> lifecycleScope.launchWhenResumed { model.deleteAllItems() }
                }

                true
            }
        }

        binding.listView.setHasFixedSize(true)
        binding.listView.layoutManager = LinearLayoutManager(requireContext())
        binding.listView.adapter = adapter

        lifecycleScope.launchWhenResumed {
            model.onViewReady()
        }

        lifecycleScope.launchWhenResumed {
            model.items.collect { result ->
                binding.progress.isVisible = false

                when (result) {
                    is Result.Progress -> {
                        binding.progress.isVisible = true
                        binding.progress.alpha = 0f
                        binding.progress.animate().alpha(1f).setDuration(1000).start()
                    }

                    is Result.Success -> {
                        binding.empty.isVisible = result.data.isEmpty()
                        adapter.swapItems(result.data)
                    }

                    else -> {

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