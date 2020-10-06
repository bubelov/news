package co.appreactor.news.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.NavGraphDirections
import co.appreactor.news.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class SettingsFragment : Fragment() {

    private val model: SettingsFragmentModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_settings,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            showReadEntries.isChecked = model.getShowReadEntries().first()

            showReadEntries.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    model.setShowReadEntries(isChecked)
                }
            }

            showPreviewImages.isChecked = model.getShowPreviewImages().first()

            showPreviewImages.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    model.setShowPreviewImages(isChecked)
                }
            }

            cropPreviewImages.isChecked = model.getCropPreviewImages().first()

            cropPreviewImages.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    model.setCropPreviewImages(isChecked)
                }
            }

            username.text = model.getAccountName(requireContext())
        }

        viewExceptions.setOnClickListener {
            findNavController().navigate(SettingsFragmentDirections.actionSettingsFragmentToExceptionsFragment())
        }

        lifecycleScope.launchWhenResumed {
            model.getExceptionsCount().collect {
                viewExceptionsSubtitle.text = getString(R.string.exceptions_logged_d, it)
            }
        }

        lifecycleScope.launchWhenResumed {
            model.getShowPreviewImages().collect {
                cropPreviewImages.isEnabled = it
            }
        }

        logOut.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.log_out_warning)
                .setPositiveButton(
                    R.string.log_out
                ) { _, _ ->
                    lifecycleScope.launch {
                        model.clearData(requireContext())
                        findNavController().popBackStack(R.id.entriesFragment, true)
                        findNavController().navigate(NavGraphDirections.actionGlobalToAuthFragment())
                    }
                }.setNegativeButton(
                    R.string.cancel,
                    null
                ).show()
        }
    }
}