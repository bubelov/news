package feedsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
        binding.apply {
            toolbar.apply {
                lifecycleScope.launchWhenResumed {
                    title = model.getFeedTitle(args.feedId)
                }

                setNavigationOnClickListener {
                    findNavController().popBackStack()
                }
            }

            lifecycleScope.launchWhenResumed {
                openEntriesInBrowser.isChecked = model.isOpenEntriesInBrowser(args.feedId)

                openEntriesInBrowser.setOnCheckedChangeListener { _, isChecked ->
                    lifecycleScope.launchWhenResumed {
                        model.setOpenEntriesInBrowser(args.feedId, isChecked)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}