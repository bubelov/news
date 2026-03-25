package org.vestifeed.feedsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import org.vestifeed.navigation.AppFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vestifeed.R
import org.vestifeed.app.App
import org.vestifeed.app.api
import org.vestifeed.databinding.FragmentFeedSettingsBinding
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.feeds.FeedsRepo
import org.vestifeed.navigation.showKeyboard

class FeedSettingsFragment : AppFragment() {

    private val feedId by lazy { requireArguments().getString("feedId", "") }

    private val db by lazy { (requireContext().applicationContext as App).db }
    private val api by lazy { api() }
    private val feedsRepo by lazy { FeedsRepo(api, db) }

    private val _feedId = MutableStateFlow("")
    private val _state = MutableStateFlow<State>(State.LoadingFeed)
    private val state = _state.asStateFlow()

    private var _binding: FragmentFeedSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFeedSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        _feedId.update { feedId }

        _feedId.onEach {
            if (it.isBlank()) {
                _state.update { State.LoadingFeed }
                return@onEach
            }

            val feed = feedsRepo.selectById(it).first()!!
            _state.update { State.ShowingFeedSettings(feed) }
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        state.onEach { binding.setState(it) }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setOpenEntriesInBrowser(feedId: String, openEntriesInBrowser: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val feed = feedsRepo.selectById(feedId).first()!!
            feedsRepo.insertOrReplace(feed.copy(extOpenEntriesInBrowser = openEntriesInBrowser))
        }
    }

    private fun setShowPreviewImages(feedId: String, value: Boolean?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val feed = feedsRepo.selectById(feedId).first()!!
            feedsRepo.insertOrReplace(feed.copy(extShowPreviewImages = value))
        }
    }

    private fun setBlockedWords(feedId: String, blockedWords: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val feed = feedsRepo.selectById(feedId).first()!!
            feedsRepo.insertOrReplace(feed.copy(extBlockedWords = blockedWords))
        }
    }

    private fun formatBlockedWords(blockedWords: String): String {
        val separatedWords = blockedWords.split(",")

        return if (separatedWords.isEmpty()) {
            ""
        } else {
            buildString {
                separatedWords.forEach {
                    append(it.trim())
                    append(",")
                }
            }.dropLast(1)
        }
    }

    private fun FragmentFeedSettingsBinding.setState(state: State) {
        when (state) {
            is State.LoadingFeed -> {
                progress.isVisible = true
                settings.isVisible = false
            }

            is State.ShowingFeedSettings -> {
                toolbar.title = state.feed.title
                progress.isVisible = false
                settings.isVisible = true

                syncOpenEntriesInBrowser(state.feed.extOpenEntriesInBrowser ?: false)
                syncBlockedWords(state.feed.extBlockedWords)
                syncShowPreviewImages(state.feed.extShowPreviewImages)
            }
        }
    }

    private fun syncOpenEntriesInBrowser(openEntriesInBrowser: Boolean) {
        binding.openEntriesInBrowser.apply {
            isChecked = openEntriesInBrowser

            setOnCheckedChangeListener { _, isChecked ->
                runCatching { setOpenEntriesInBrowser(feedId, isChecked) }
                    .onFailure { showErrorDialog(it) }
            }
        }
    }

    private fun syncBlockedWords(blockedWords: String) {
        binding.blockedWords.text = formatBlockedWords(blockedWords)

        binding.blockedWordsPanel.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.blocked_words))
                .setView(R.layout.dialog_blocked_words)
                .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            val dialog = dialogInterface as AlertDialog
                            val blockedWordsView =
                                dialog.findViewById<TextInputEditText>(R.id.blockedWords)!!
                            val formattedBlockedWords =
                                formatBlockedWords(blockedWordsView.text.toString())
                            setBlockedWords(feedId, formattedBlockedWords)
                            binding.blockedWords.text = formattedBlockedWords.replace(",", ", ")
                        }.onFailure {
                            showErrorDialog(it)
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()

            val blockedWordsView = dialog.findViewById<TextInputEditText>(R.id.blockedWords)!!
            blockedWordsView.append(blockedWords)
            blockedWordsView.requestFocus()
            blockedWordsView.postDelayed({ showKeyboard(blockedWordsView) }, 300)
        }
    }

    private fun syncShowPreviewImages(showPreviewImages: Boolean?) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.showPreviewImages.text = when (showPreviewImages) {
                true -> getString(R.string.show)
                false -> getString(R.string.hide)
                else -> getString(R.string.follow_settings)
            }

            binding.showPreviewImagesPanel.setOnClickListener {
                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.preview_images))
                    .setView(R.layout.dialog_show_preview_images)
                    .show()

                val btnShow = dialog.findViewById<RadioButton>(R.id.show)!!
                val btnHide = dialog.findViewById<RadioButton>(R.id.hide)!!
                val btnFollowSettings = dialog.findViewById<RadioButton>(R.id.followSettings)!!

                val checkedButton = when (showPreviewImages) {
                    true -> btnShow
                    false -> btnHide
                    else -> btnFollowSettings
                }

                checkedButton.isChecked = true

                val saveValue = fun(value: Boolean?) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching { setShowPreviewImages(feedId, value) }
                            .onSuccess { syncShowPreviewImages(value) }
                            .onFailure { showErrorDialog(it) }
                    }

                    dialog.dismiss()
                }

                btnShow.setOnClickListener { saveValue.invoke(true) }
                btnHide.setOnClickListener { saveValue.invoke(false) }
                btnFollowSettings.setOnClickListener { saveValue.invoke(null) }
            }
        }
    }

    sealed class State {
        object LoadingFeed : State()
        data class ShowingFeedSettings(val feed: org.vestifeed.db.Feed) : State()
    }
}