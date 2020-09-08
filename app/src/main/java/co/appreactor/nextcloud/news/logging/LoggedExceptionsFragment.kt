package co.appreactor.nextcloud.news.logging

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import co.appreactor.nextcloud.news.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.fragment_logged_exceptions.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class LoggedExceptionsFragment : Fragment() {

    private val model: ExceptionsFragmentModel by viewModel()

    private val adapter = ExceptionsAdapter {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(it.exceptionClass)
            .setMessage(it.stackTrace)
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_logged_exceptions,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.apply {
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

        listView.setHasFixedSize(true)
        listView.layoutManager = LinearLayoutManager(requireContext())
        listView.adapter = adapter

        lifecycleScope.launchWhenResumed {
            progress.isVisible = true

            model.getExceptions().collect { exceptions ->
                progress.isVisible = false
                empty.isVisible = exceptions.isEmpty()
                adapter.swapItems(exceptions)
            }
        }
    }
}