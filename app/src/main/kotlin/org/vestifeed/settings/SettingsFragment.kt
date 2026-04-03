package org.vestifeed.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import org.vestifeed.navigation.AppFragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vestifeed.R
import org.vestifeed.app.App
import org.vestifeed.auth.AuthFragment
import org.vestifeed.databinding.FragmentSettingsBinding
import org.vestifeed.db.table.Conf
import org.vestifeed.db.table.ConfSchema
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.enclosures.EnclosuresFragment
import org.vestifeed.sync.BackgroundSyncScheduler
import java.util.concurrent.TimeUnit

class SettingsFragment : AppFragment() {

    private val db by lazy { (requireContext().applicationContext as App).db }
    private val syncScheduler by lazy { BackgroundSyncScheduler(requireContext()) }

    private val _state = MutableStateFlow<State>(State.Loading)
    private val state = _state.asStateFlow()

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.statusBars()).let {
                v.updatePadding(top = it.top)
            }
            insets
        }

        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        refresh()

        state
            .onEach { binding.setState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        val conf = db.conf.select()
        _state.update {
            val logOutTitle: String
            val logOutSubtitle: String

            when (conf.backend) {
                ConfSchema.BACKEND_STANDALONE -> {
                    logOutTitle = getString(R.string.delete_all_data)
                    logOutSubtitle = ""
                }

                else -> {
                    logOutTitle = getString(R.string.log_out)
                    logOutSubtitle = conf.accountName()
                }
            }

            State.ShowingSettings(
                conf = conf,
                logOutTitle = logOutTitle,
                logOutSubtitle = logOutSubtitle,
            )
        }
    }

    private fun setSyncInBackground(value: Boolean) {
        db.conf.update { it.copy(syncInBackground = value) }
        syncScheduler.schedule()
        refresh()
    }

    private fun setBackgroundSyncIntervalMillis(value: Long) {
        db.conf.update { it.copy(backgroundSyncIntervalMillis = value) }
        syncScheduler.schedule()
        refresh()
    }

    private fun setSyncOnStartup(value: Boolean) {
        db.conf.update { it.copy(syncOnStartup = value) }
        refresh()
    }

    private fun setShowReadEntries(value: Boolean) {
        db.conf.update { it.copy(showReadEntries = value) }
        refresh()
    }

    private fun setShowPreviewImages(value: Boolean) {
        db.conf.update { it.copy(showPreviewImages = value) }
        refresh()
    }

    private fun setCropPreviewImages(value: Boolean) {
        db.conf.update { it.copy(cropPreviewImages = value) }
        refresh()
    }

    private fun setShowPreviewText(value: Boolean) {
        db.conf.update { it.copy(showPreviewText = value) }
        refresh()
    }

    private fun setMarkScrolledEntriesAsRead(value: Boolean) {
        db.conf.update { it.copy(markScrolledEntriesAsRead = value) }
        refresh()
    }

    private fun setUseBuiltInBrowser(value: Boolean) {
        db.conf.update { it.copy(useBuiltInBrowser = value) }
        refresh()
    }

    private fun logOut() {
        db.conf.delete()

        db.transaction {
            db.feed.deleteAll()
            db.entry.deleteAll()
        }
    }

    private fun Conf.accountName(): String {
        return when (backend) {
            ConfSchema.BACKEND_STANDALONE -> ""
            ConfSchema.BACKEND_MINIFLUX -> {
                minifluxServerUrl.extractDomain()
            }

            ConfSchema.BACKEND_NEXTCLOUD -> {
                val username = nextcloudServerUsername
                "$username@${nextcloudServerUrl.extractDomain()}"
            }

            else -> ""
        }
    }

    private fun String.extractDomain(): String {
        return replace("https://", "").replace("http://", "")
    }

    private fun createExportDbLauncher(): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }

            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            requireContext().contentResolver.openOutputStream(uri)?.use {
                                (requireContext().applicationContext as App).databaseFile()
                                    .inputStream().copyTo(it)
                            }
                        }
                    }.onFailure {
                        showErrorDialog(it)
                    }
                }
            }
        }
    }

    private fun FragmentSettingsBinding.setState(state: State) {
        when (state) {
            State.Loading -> showProgress()
            is State.ShowingSettings -> showSettings(state)
        }
    }

    private fun FragmentSettingsBinding.showProgress() {
        progress.isVisible = true
        settings.isVisible = false
    }

    private fun FragmentSettingsBinding.showSettings(state: State.ShowingSettings) {
        progress.isVisible = false
        settings.isVisible = true

        syncInBackground.apply {
            isChecked = state.conf.syncInBackground
            backgroundSyncIntervalButton.isVisible = state.conf.syncInBackground

            setOnCheckedChangeListener { _, isChecked ->
                setSyncInBackground(isChecked)
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
                        setBackgroundSyncIntervalMillis(millis)
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
            setOnCheckedChangeListener { _, isChecked -> setSyncOnStartup(isChecked) }
        }

        showOpenedEntries.apply {
            isChecked = state.conf.showReadEntries
            setOnCheckedChangeListener { _, isChecked -> setShowReadEntries(isChecked) }
        }

        showPreviewImages.apply {
            isChecked = state.conf.showPreviewImages
            setOnCheckedChangeListener { _, isChecked -> setShowPreviewImages(isChecked) }
        }

        cropPreviewImages.apply {
            isChecked = state.conf.cropPreviewImages
            setOnCheckedChangeListener { _, isChecked -> setCropPreviewImages(isChecked) }
        }

        showPreviewText.apply {
            isChecked = state.conf.showPreviewText
            setOnCheckedChangeListener { _, isChecked -> setShowPreviewText(isChecked) }
        }

        markScrolledEntriesAsRead.apply {
            isChecked = state.conf.markScrolledEntriesAsRead
            setOnCheckedChangeListener { _, isChecked ->
                setMarkScrolledEntriesAsRead(isChecked)
            }
        }

        useBuiltInBrowser.apply {
            isChecked = state.conf.useBuiltInBrowser
            setOnCheckedChangeListener { _, isChecked -> setUseBuiltInBrowser(isChecked) }
        }

        manageEnclosures.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainerView, EnclosuresFragment::class.java, null)
                addToBackStack(null)
            }
        }

        exportDatabase.setOnClickListener { exportDbLauncher.launch("news.org.vestifeed.db") }

        logOutTitle.text = state.logOutTitle
        logOutSubtitle.text = state.logOutSubtitle
        logOutSubtitle.isVisible = logOutSubtitle.length() > 0

        logOut.setOnClickListener {
            when (state.conf.backend) {
                ConfSchema.BACKEND_STANDALONE -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.delete_all_data_warning)
                        .setPositiveButton(R.string.delete) { _, _ -> doLogOut() }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }

                else -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.log_out_warning)
                        .setPositiveButton(R.string.log_out) { _, _ -> doLogOut() }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    private fun doLogOut() {
        lifecycleScope.launch {
            logOut()

            parentFragmentManager.commit {
                replace(
                    R.id.fragmentContainerView, AuthFragment::class.java, null
                )
            }
        }
    }

    sealed class State {
        object Loading : State()
        data class ShowingSettings(
            val conf: Conf,
            val logOutTitle: String,
            val logOutSubtitle: String,
        ) : State()
    }
}