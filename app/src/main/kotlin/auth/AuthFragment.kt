package auth

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentAuthBinding
import entries.EntriesFilter
import navigation.NavDirections
import navigation.findNavController
import org.koin.androidx.viewmodel.ext.android.viewModel

class AuthFragment : Fragment() {

    private val model: AuthModel by viewModel()

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
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
            binding.root.animate().alpha(0f).withEndAction {
                model.setStandaloneBackend()
                findNavController().navigate(
                    R.id.newsFragment,
                    NavDirections.AuthFragment.actionAuthFragmentToNewsFragment(EntriesFilter.NotBookmarked)
                )
            }
        }

        useMinifluxBackend.setOnClickListener {
            findNavController().navigate(R.id.minifluxAuthFragment)
        }

        useNextcloudBackend.setOnClickListener {
            findNavController().navigate(R.id.nextcloudAuthFragment)
        }
    }
}