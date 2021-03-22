package feedsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import co.appreactor.news.databinding.FragmentFeedSettingsBinding
import org.koin.android.viewmodel.ext.android.viewModel

class FeedSettingsFragment : Fragment() {

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
        val feed = model.getFeed(args.feedId)

        if (feed == null) {
            findNavController().popBackStack()
            return
        }

        binding.apply {
            toolbar.apply {
                title = feed.title
                setNavigationOnClickListener { findNavController().popBackStack() }
            }

            openEntriesInBrowser.apply {
                isChecked = feed.openEntriesInBrowser

                setOnCheckedChangeListener { _, isChecked ->
                    model.setOpenEntriesInBrowser(args.feedId, isChecked)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}