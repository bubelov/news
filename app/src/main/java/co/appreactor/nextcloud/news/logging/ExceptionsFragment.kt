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
import kotlinx.android.synthetic.main.fragment_exceptions.*
import kotlinx.coroutines.flow.collect
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class ExceptionsFragment : Fragment() {

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
            R.layout.fragment_exceptions,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        listView.setHasFixedSize(true)
        listView.layoutManager = LinearLayoutManager(requireContext())
        listView.adapter = adapter

        lifecycleScope.launchWhenResumed {
            progress.isVisible = true

            model.getExceptions().collect { exceptions ->
                Timber.d("Collected ${exceptions.size} exceptions")
                progress.isVisible = false
                empty.isVisible = exceptions.isEmpty()
                adapter.swapItems(exceptions)
            }
        }
    }
}