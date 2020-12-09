package co.appreactor.news.logging

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
import co.appreactor.news.common.showDialog
import co.appreactor.news.databinding.FragmentLoggedExceptionsBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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

            setOnMenuItemClickListener {
                if (it.itemId == R.id.delete) {
                    lifecycleScope.launch {
                        model.deleteAll()
                    }

                    true
                } else {
                    false
                }
            }
        }

        binding.listView.setHasFixedSize(true)
        binding.listView.layoutManager = LinearLayoutManager(requireContext())
        binding.listView.adapter = adapter

        lifecycleScope.launchWhenResumed {
            binding.progress.isVisible = true

            model.getExceptions().collect { exceptions ->
                binding.progress.isVisible = false
                binding.empty.isVisible = exceptions.isEmpty()
                adapter.swapItems(exceptions)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}