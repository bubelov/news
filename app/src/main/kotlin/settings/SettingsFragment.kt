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
import common.App
import common.AppFragment
import common.ConfRepository
import common.app
import common.showErrorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
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

        toolbar?.apply {
            setupUpNavigation()
            setTitle(R.string.settings)
        }

        val conf = runBlocking { model.getConf().first() }

        binding.apply {
            syncInBackground.apply {
                isChecked = conf.syncInBackground
                backgroundSyncIntervalButton.isVisible = conf.syncInBackground

                setOnCheckedChangeListener { _, isChecked ->
                    runBlocking {
                        model.saveConf(
                            model.getConf().first().copy(syncInBackground = isChecked)
                        )
                    }
                    backgroundSyncIntervalButton.isVisible = isChecked
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
                    isChecked =
                        runBlocking { model.getConf().first().backgroundSyncIntervalMillis == millis }

                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            runBlocking {
                                model.saveConf(
                                    model.getConf().first().copy(backgroundSyncIntervalMillis = millis)
                                )
                            }

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
                TimeUnit.MILLISECONDS.toHours(conf.backgroundSyncIntervalMillis).toInt(),
                TimeUnit.MILLISECONDS.toHours(conf.backgroundSyncIntervalMillis).toInt(),
            )

            syncOnStartup.apply {
                isChecked = conf.syncOnStartup

                setOnCheckedChangeListener { _, isChecked ->
                    runBlocking {
                        model.saveConf(model.getConf().first().copy(syncOnStartup = isChecked))
                    }
                }
            }

            showOpenedEntries.apply {
                isChecked = conf.showReadEntries

                setOnCheckedChangeListener { _, isChecked ->
                    runBlocking {
                        model.saveConf(model.getConf().first().copy(showReadEntries = isChecked))
                    }
                }
            }

            showPreviewImages.apply {
                isChecked = conf.showPreviewImages

                setOnCheckedChangeListener { _, isChecked ->
                    runBlocking {
                        model.saveConf(model.getConf().first().copy(showPreviewImages = isChecked))
                    }
                }
            }

            cropPreviewImages.apply {
                isChecked = conf.cropPreviewImages

                setOnCheckedChangeListener { _, isChecked ->
                    runBlocking {
                        model.saveConf(model.getConf().first().copy(cropPreviewImages = isChecked))
                    }
                }
            }

            showPreviewText.apply {
                isChecked = conf.showPreviewText

                setOnCheckedChangeListener { _, isChecked ->
                    runBlocking {
                        model.saveConf(model.getConf().first().copy(showPreviewText = isChecked))
                    }
                }
            }

            markScrolledEntriesAsRead.apply {
                isChecked = conf.markScrolledEntriesAsRead

                setOnCheckedChangeListener { _, isChecked ->
                    runBlocking {
                        model.saveConf(model.getConf().first().copy(markScrolledEntriesAsRead = isChecked))
                    }
                }
            }

            useBuiltInBrowser.apply {
                isChecked = conf.useBuiltInBrowser

                setOnCheckedChangeListener { _, isChecked ->
                    runBlocking {
                        model.saveConf(model.getConf().first().copy(useBuiltInBrowser = isChecked))
                    }
                }
            }

            manageEnclosures.setOnClickListener {
                findNavController().navigate(R.id.action_settingsFragment_to_enclosuresFragment)
            }

            exportDatabase.setOnClickListener {
                exportDatabaseLauncher.launch("news.db")
            }

            logOut.setOnClickListener {
                lifecycleScope.launchWhenResumed {
                    when (model.getConf().first().backend) {
                        ConfRepository.BACKEND_STANDALONE -> {
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

            when (conf.backend) {
                ConfRepository.BACKEND_STANDALONE -> {
                    binding.logOutTitle.setText(R.string.delete_all_data)
                    binding.logOutSubtitle.isVisible = false
                }

                else -> binding.logOutSubtitle.text = model.getAccountName()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun logOut() {
        lifecycleScope.launch {
            model.logOut()

            findNavController().apply {
                while (popBackStack()) {
                    popBackStack()
                }
                navigate(NavGraphDirections.actionGlobalToAuthFragment())
            }
        }
    }
}