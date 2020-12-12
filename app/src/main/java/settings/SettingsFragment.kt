package settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.NavGraphDirections
import co.appreactor.news.R
import common.Preferences
import co.appreactor.news.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.android.sso.AccountImporter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class SettingsFragment : Fragment() {

    private val model: SettingsFragmentModel by viewModel()

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            binding.showOpenedEntries.isChecked = model.getShowOpenedEntries().first()

            binding.showOpenedEntries.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    model.setShowOpenedEntries(isChecked)
                }
            }

            binding.showPreviewImages.isChecked = model.getShowPreviewImages().first()

            binding.showPreviewImages.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    model.setShowPreviewImages(isChecked)
                }
            }

            binding.cropPreviewImages.isChecked = model.getCropPreviewImages().first()

            binding.cropPreviewImages.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    model.setCropPreviewImages(isChecked)
                }
            }

            when (model.getAuthType()) {
                Preferences.AUTH_TYPE_STANDALONE -> {
                    binding.logOutTitle.setText(R.string.delete_all_data)
                    binding.logOutSubtitle.isVisible = false
                }

                else -> binding.logOutSubtitle.text = model.getAccountName(requireContext())
            }
        }

        binding.viewExceptions.setOnClickListener {
            findNavController().navigate(SettingsFragmentDirections.actionSettingsFragmentToExceptionsFragment())
        }

        lifecycleScope.launchWhenResumed {
            model.getExceptionsCount().collect {
                binding.viewExceptionsSubtitle.text = getString(R.string.exceptions_logged_d, it)
            }
        }

        lifecycleScope.launchWhenResumed {
            model.getShowPreviewImages().collect {
                binding.cropPreviewImages.isEnabled = it
            }
        }

        binding.logOut.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                when (model.getAuthType()) {
                    Preferences.AUTH_TYPE_STANDALONE -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setMessage(R.string.delete_all_data_warning)
                            .setPositiveButton(
                                R.string.delete
                            ) { _, _ ->
                                logOut()
                            }.setNegativeButton(
                                R.string.cancel,
                                null
                            ).show()
                    }

                    else -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setMessage(R.string.log_out_warning)
                            .setPositiveButton(
                                R.string.log_out
                            ) { _, _ ->
                                logOut()
                            }.setNegativeButton(
                                R.string.cancel,
                                null
                            ).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun logOut() {
        lifecycleScope.launch {
            AccountImporter.clearAllAuthTokens(context)
            model.logOut()
            findNavController().popBackStack(R.id.entriesFragment, true)
            findNavController().navigate(NavGraphDirections.actionGlobalToAuthFragment())
        }
    }
}