package feedsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentFeedSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dialog.showErrorDialog
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import navigation.showKeyboard
import org.koin.androidx.viewmodel.ext.android.viewModel

class FeedSettingsFragment : Fragment() {

    private val args: FeedSettingsFragmentArgs by navArgs()

    private val model: FeedSettingsModel by viewModel()

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

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        model.feedId.update { args.feedId }
        model.state.onEach { binding.setState(it) }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun FragmentFeedSettingsBinding.setState(state: FeedSettingsModel.State) {
        when (state) {
            is FeedSettingsModel.State.LoadingFeed -> {
                progress.isVisible = true
                settings.isVisible = false
            }

            is FeedSettingsModel.State.ShowingFeedSettings -> {
                toolbar.title = state.feed.title
                progress.isVisible = false
                settings.isVisible = true

                syncOpenEntriesInBrowser(state.feed.openEntriesInBrowser)
                syncShowPreviewImages(state.feed.showPreviewImages)
                syncBlockedWords(state.feed.blockedWords)
            }
        }
    }

    private fun syncOpenEntriesInBrowser(openEntriesInBrowser: Boolean) {
        binding.openEntriesInBrowser.apply {
            isChecked = openEntriesInBrowser

            setOnCheckedChangeListener { _, isChecked ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { model.setOpenEntriesInBrowser(args.feedId, isChecked) }
                        .onFailure { showErrorDialog(it) }
                }
            }
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
                        runCatching { model.setShowPreviewImages(args.feedId, value) }
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

    private fun syncBlockedWords(blockedWords: String) {
        binding.blockedWords.text = model.formatBlockedWords(blockedWords)

        binding.blockedWordsPanel.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.blocked_words))
                .setView(R.layout.dialog_blocked_words)
                .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            val dialog = dialogInterface as AlertDialog
                            val blockedWordsView = dialog.findViewById<TextInputEditText>(R.id.blockedWords)!!
                            val formattedBlockedWords = model.formatBlockedWords(blockedWordsView.text.toString())
                            model.setBlockedWords(args.feedId, formattedBlockedWords)
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
}