package settings

import android.app.Activity
import android.content.Intent
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
import common.showDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opml.readOpml
import opml.writeOpml
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

            binding.markScrolledEntriesAsRead.isChecked =
                model.getMarkScrolledEntriesAsRead().first()

            binding.markScrolledEntriesAsRead.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    model.setMarkScrolledEntriesAsRead(isChecked)
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

        binding.importFeeds.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
            }

            startActivityForResult(Intent.createChooser(intent, ""), IMPORT_REQUEST)
        }

        binding.exportFeeds.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/xml"
                putExtra(Intent.EXTRA_TITLE, "feeds.opml")
            }

            startActivityForResult(intent, EXPORT_REQUEST)
        }

        lifecycleScope.launchWhenResumed {
            model.state.collect { state ->
                if (state is SettingsFragmentModel.State.ImportingFeeds) {
                    binding.content.isVisible = false

                    if (!binding.progress.isVisible) {
                        binding.progress.isVisible = true
                        binding.progress.alpha = 0f
                        binding.progress.animate().alpha(1f).duration = 1000
                    }

                    if (!binding.progressMessage.isVisible) {
                        binding.progressMessage.isVisible = true
                        binding.progressMessage.alpha = 0f
                        binding.progressMessage.animate().alpha(1f).duration = 1000
                    }

                    binding.progressMessage.text =
                        getString(R.string.importing_feeds_n_of_n, state.imported, state.total)
                } else {
                    binding.content.isVisible = true

                    binding.progress.isVisible = false
                    binding.progressMessage.isVisible = false
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == IMPORT_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                showDialog(R.string.error, "The app didn't receive any data")
                return
            }

            val uri = data.data

            if (uri == null) {
                showDialog(R.string.error, "The app didn't receive file URI")
                return
            }

            lifecycleScope.launchWhenResumed {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use {
                        val feeds = readOpml(it.bufferedReader().readText())
                        val result = model.importFeeds(feeds)

                        withContext(Dispatchers.Main) {
                            showDialog(
                                title = "Import",
                                message = "Added: ${result.added}\nExists: ${result.exists}\nFailed: ${result.failed}"
                            )
                        }
                    }
                }
            }
        }

        if (requestCode == EXPORT_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                showDialog(R.string.error, "The app didn't receive any data")
                return
            }

            val uri = data.data

            if (uri == null) {
                showDialog(R.string.error, "The app didn't receive file URI")
                return
            }

            lifecycleScope.launchWhenResumed {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.use {
                        it.write(writeOpml(model.getAllFeeds().first()).toByteArray())
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

    companion object {
        private const val IMPORT_REQUEST = 1000
        private const val EXPORT_REQUEST = 1001
    }
}