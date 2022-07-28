package settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import conf.ConfRepo
import db.databaseFile
import dialog.showErrorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class SettingsFragment : Fragment() {

    private val model: SettingsModel by viewModel()

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val exportDbLauncher = createExportDbLauncher()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        model.state
            .onEach { binding.setState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun createExportDbLauncher(): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    runCatching {
                        requireContext().contentResolver.openOutputStream(uri!!)?.use {
                            requireContext().databaseFile().inputStream().copyTo(it)
                        }
                    }.onFailure { showErrorDialog(it) }
                }
            }
        }
    }

    private fun FragmentSettingsBinding.setState(state: SettingsModel.State) {
        when (state) {
            SettingsModel.State.Loading -> showProgress()
            is SettingsModel.State.ShowingSettings -> showSettings(state)
        }
    }

    private fun FragmentSettingsBinding.showProgress() {
        progress.isVisible = true
        settings.isVisible = false
    }

    private fun FragmentSettingsBinding.showSettings(state: SettingsModel.State.ShowingSettings) {
        progress.isVisible = false
        settings.isVisible = true

        syncInBackground.apply {
            isChecked = state.conf.syncInBackground
            backgroundSyncIntervalButton.isVisible = state.conf.syncInBackground

            setOnCheckedChangeListener { _, isChecked ->
                model.setSyncInBackground(isChecked)
                backgroundSyncIntervalButton.isVisible = isChecked
            }
        }

        backgroundSyncIntervalButton.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.background_sync_interval))
                .setView(R.layout.dialog_background_sync_interval)
                .show()

            val setupInterval = fun RadioButton?.(hours: Int) {
                if (this == null) return

                text = resources.getQuantityString(R.plurals.d_hours, hours, hours)
                val millis = TimeUnit.HOURS.toMillis(hours.toLong())
                isChecked = state.conf.backgroundSyncIntervalMillis == millis

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        model.setBackgroundSyncIntervalMillis(millis)
                        backgroundSyncInterval.text = text
                        dialog.dismiss()
                    }
                }
            }

            setupInterval.apply {
                invoke(dialog.findViewById(R.id.one_hour), 1)
                invoke(dialog.findViewById(R.id.three_hours), 3)
                invoke(dialog.findViewById(R.id.six_hours), 6)
                invoke(dialog.findViewById(R.id.twelve_hours), 12)
                invoke(dialog.findViewById(R.id.twenty_four_hours), 24)
            }
        }

        backgroundSyncInterval.text = resources.getQuantityString(
            R.plurals.d_hours,
            TimeUnit.MILLISECONDS.toHours(state.conf.backgroundSyncIntervalMillis).toInt(),
            TimeUnit.MILLISECONDS.toHours(state.conf.backgroundSyncIntervalMillis).toInt(),
        )

        syncOnStartup.apply {
            isChecked = state.conf.syncOnStartup
            setOnCheckedChangeListener { _, isChecked -> model.setSyncOnStartup(isChecked) }
        }

        showOpenedEntries.apply {
            isChecked = state.conf.showReadEntries
            setOnCheckedChangeListener { _, isChecked -> model.setShowReadEntries(isChecked) }
        }

        showPreviewImages.apply {
            isChecked = state.conf.showPreviewImages
            setOnCheckedChangeListener { _, isChecked -> model.setShowPreviewImages(isChecked) }
        }

        cropPreviewImages.apply {
            isChecked = state.conf.cropPreviewImages
            setOnCheckedChangeListener { _, isChecked -> model.setCropPreviewImages(isChecked) }
        }

        showPreviewText.apply {
            isChecked = state.conf.showPreviewText
            setOnCheckedChangeListener { _, isChecked -> model.setShowPreviewText(isChecked) }
        }

        markScrolledEntriesAsRead.apply {
            isChecked = state.conf.markScrolledEntriesAsRead
            setOnCheckedChangeListener { _, isChecked -> model.setMarkScrolledEntriesAsRead(isChecked) }
        }

        useBuiltInBrowser.apply {
            isChecked = state.conf.useBuiltInBrowser
            setOnCheckedChangeListener { _, isChecked -> model.setUseBuiltInBrowser(isChecked) }
        }

        manageEnclosures.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_enclosuresFragment)
        }

        exportDatabase.setOnClickListener { exportDbLauncher.launch("news.db") }

        logOutTitle.text = state.logOutTitle
        logOutSubtitle.text = state.logOutSubtitle
        logOutSubtitle.isVisible = logOutSubtitle.length() > 0

        logOut.setOnClickListener {
            when (state.conf.backend) {
                ConfRepo.BACKEND_STANDALONE -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.delete_all_data_warning)
                        .setPositiveButton(R.string.delete) { _, _ -> logOut() }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }

                else -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.log_out_warning)
                        .setPositiveButton(R.string.log_out) { _, _ -> logOut() }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    private fun logOut() {
        lifecycleScope.launch {
            model.logOut()

            findNavController().apply {
                while (popBackStack()) {
                    popBackStack()
                }

                navigate(R.id.authFragment)
            }
        }
    }
}