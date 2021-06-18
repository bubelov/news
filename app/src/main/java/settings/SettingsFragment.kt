package settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.NavGraphDirections
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.android.sso.AccountImporter
import common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class SettingsFragment : AppFragment() {

    private val model: SettingsViewModel by viewModel()

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    @Suppress("BlockingMethodInNonBlockingContext")
    private val exportDatabaseLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        val database = requireActivity().getDatabasePath(App.DB_FILE_NAME)

        if (!database.exists()) {
            Toast.makeText(requireContext(), "Database does not exist", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        lifecycleScope.launchWhenResumed {
            withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openOutputStream(uri)?.use {
                        database.inputStream().copyTo(it)
                    }
                }.onFailure {
                    showErrorDialog(it)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.apply {
            setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
            setNavigationOnClickListener { findNavController().popBackStack() }
            setTitle(R.string.settings)
        }

        val prefs = model.getPreferences()

        binding.apply {
            syncInBackground.apply {
                isChecked = prefs.syncInBackground
                backgroundSyncIntervalPanel.isVisible = prefs.syncInBackground

                setOnCheckedChangeListener { _, isChecked ->
                    model.savePreferences { syncInBackground = isChecked }
                    backgroundSyncIntervalPanel.isVisible = isChecked
                    app().setupBackgroundSync(override = true)
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
                    isChecked = model.getPreferences().backgroundSyncIntervalMillis == millis

                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            model.savePreferences { backgroundSyncIntervalMillis = millis }
                            app().setupBackgroundSync(override = true)
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
                TimeUnit.MILLISECONDS.toHours(prefs.backgroundSyncIntervalMillis).toInt(),
                TimeUnit.MILLISECONDS.toHours(prefs.backgroundSyncIntervalMillis).toInt(),
            )

            syncOnStartup.apply {
                isChecked = prefs.syncOnStartup

                setOnCheckedChangeListener { _, isChecked ->
                    model.savePreferences { syncOnStartup = isChecked }
                }
            }

            showOpenedEntries.apply {
                isChecked = prefs.showOpenedEntries

                setOnCheckedChangeListener { _, isChecked ->
                    model.savePreferences { showOpenedEntries = isChecked }
                }
            }

            showPreviewImages.apply {
                isChecked = prefs.showPreviewImages

                setOnCheckedChangeListener { _, isChecked ->
                    model.savePreferences { showPreviewImages = isChecked }
                }
            }

            cropPreviewImages.apply {
                isChecked = prefs.cropPreviewImages

                setOnCheckedChangeListener { _, isChecked ->
                    model.savePreferences { cropPreviewImages = isChecked }
                }
            }

            markScrolledEntriesAsRead.apply {
                isChecked = prefs.markScrolledEntriesAsRead

                setOnCheckedChangeListener { _, isChecked ->
                    model.savePreferences { markScrolledEntriesAsRead = isChecked }
                }
            }

            viewLogEntries.setOnClickListener {
                findNavController().navigate(SettingsFragmentDirections.actionSettingsFragmentToLogEntriesFragment())
            }

            viewExceptions.setOnClickListener {
                findNavController().navigate(SettingsFragmentDirections.actionSettingsFragmentToExceptionsFragment())
            }

            exportDatabase.setOnClickListener {
                exportDatabaseLauncher.launch("news.db")
            }

            logOut.setOnClickListener {
                lifecycleScope.launchWhenResumed {
                    when (model.getPreferences().authType) {
                        PreferencesRepository.AUTH_TYPE_STANDALONE -> {
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

            when (prefs.authType) {
                PreferencesRepository.AUTH_TYPE_STANDALONE -> {
                    binding.logOutTitle.setText(R.string.delete_all_data)
                    binding.logOutSubtitle.isVisible = false
                }

                else -> binding.logOutSubtitle.text = model.getAccountName(requireContext())
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