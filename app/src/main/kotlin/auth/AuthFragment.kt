package auth

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentAuthBinding
import entries.EntriesFilter
import org.koin.androidx.viewmodel.ext.android.viewModel

class AuthFragment : Fragment() {

    private val model: AuthModel by viewModel()

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return if (model.hasBackend()) {
            val intent = requireActivity().intent
            val sharedFeedUrl = (intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT))?.trim()

            if (sharedFeedUrl.isNullOrBlank()) {
                val directions = AuthFragmentDirections.actionAuthFragmentToNewsFragment(EntriesFilter.NotBookmarked)
                findNavController().navigate(directions)
            } else {
                val directions = AuthFragmentDirections.actionAuthFragmentToFeedsFragment(sharedFeedUrl)
                findNavController().navigate(directions)
            }

            null
        } else {
            _binding = FragmentAuthBinding.inflate(inflater, container, false)
            binding.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.initButtons()
    }

    override fun onResume() {
        super.onResume()
        (binding.icon.drawable as? Animatable)?.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun FragmentAuthBinding.initButtons() {
        useStandaloneBackend.setOnClickListener {
            // This animation hides layout shift caused by bottom nav visibility change
            binding.root.animate().alpha(0f).withEndAction {
                model.setStandaloneBackend()
                findNavController().navigate(AuthFragmentDirections.actionAuthFragmentToNewsFragment(EntriesFilter.NotBookmarked))
            }
        }

        useMinifluxBackend.setOnClickListener {
            findNavController().navigate(R.id.action_authFragment_to_minifluxAuthFragment)
        }

        useNextcloudBackend.setOnClickListener {
            findNavController().navigate(R.id.action_authFragment_to_nextcloudAuthFragment)
        }
    }
}