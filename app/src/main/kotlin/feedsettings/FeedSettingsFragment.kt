package feedsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentFeedSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import common.AppFragment
import common.showErrorDialog
import common.showKeyboard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel

class FeedSettingsFragment : AppFragment() {

    private val args: FeedSettingsFragmentArgs by navArgs()

    private val model: FeedSettingsViewModel by viewModel()

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

        viewLifecycleOwner.lifecycleScope.launch {
            val feed = model.getFeed(args.feedId).first()

            if (feed == null) {
                findNavController().popBackStack()
                return@launch
            }

            toolbar?.apply {
                setupUpNavigation()
                title = feed.title
            }

            binding.apply {
                openEntriesInBrowser.apply {
                    isChecked = feed.openEntriesInBrowser

                    setOnCheckedChangeListener { _, isChecked ->
                        model.setOpenEntriesInBrowser(args.feedId, isChecked)
                    }
                }

                updatePreviewImagesPanel()

                blockedWords.text = feed.blockedWords.replace(",", ", ")

                blockedWordsPanel.setOnClickListener {
                    val dialog = MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.blocked_words))
                        .setView(R.layout.dialog_blocked_words)
                        .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                            lifecycleScope.launchWhenResumed {
                                runCatching {
                                    val dialog = dialogInterface as AlertDialog
                                    val blockedWords =
                                        dialog.findViewById<TextInputEditText>(R.id.blockedWords)!!
                                    val formattedBlockedWords =
                                        model.formatBlockedWords(blockedWords.text.toString())
                                    model.setBlockedWords(feed.id, formattedBlockedWords)
                                    binding.blockedWords.text = formattedBlockedWords.replace(",", ", ")
                                }.onFailure {
                                    showErrorDialog(it)
                                }
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .setOnDismissListener { hideKeyboard() }
                        .show()

                    val blockedWords = dialog.findViewById<TextInputEditText>(R.id.blockedWords)!!
                    val formattedBlockedWords = model.formatBlockedWords(blockedWords.text.toString())
                    model.setBlockedWords(feed.id, formattedBlockedWords)

                    requireContext().showKeyboard()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updatePreviewImagesPanel() {
        viewLifecycleOwner.lifecycleScope.launch {
            val feed = model.getFeed(args.feedId).first()!!

            binding.showPreviewImages.text = when (feed.showPreviewImages) {
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

                val checkedButton = when (feed.showPreviewImages) {
                    true -> btnShow
                    false -> btnHide
                    else -> btnFollowSettings
                }

                checkedButton.isChecked = true

                val saveValue = fun(value: Boolean?) {
                    runBlocking { model.setShowPreviewImages(args.feedId, value) }
                    dialog.dismiss()
                    updatePreviewImagesPanel()
                }

                btnShow.setOnClickListener { saveValue.invoke(true) }
                btnHide.setOnClickListener { saveValue.invoke(false) }
                btnFollowSettings.setOnClickListener { saveValue.invoke(null) }
            }
        }
    }

    private fun hideKeyboard() {
        requireActivity().window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
    }
}