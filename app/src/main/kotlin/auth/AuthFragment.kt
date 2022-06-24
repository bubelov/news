package auth

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentAuthBinding
import common.BaseFragment
import common.ConfRepository
import entries.EntriesFilter
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class AuthFragment : BaseFragment() {

    private val model: AuthModel by viewModel()

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val conf = runBlocking { model.loadConf() }

        return if (conf.backend.isBlank()) {
            _binding = FragmentAuthBinding.inflate(inflater, container, false)
            binding.root
        } else {
            val intent = requireActivity().intent
            val sharedFeedUrl = (intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT))?.trim()

            if (sharedFeedUrl.isNullOrBlank()) {
                findNavController().navigate(AuthFragmentDirections.actionAuthFragmentToNewsFragment(EntriesFilter.NotBookmarked))
            } else {
                val directions = AuthFragmentDirections.actionAuthFragmentToFeedsFragment(sharedFeedUrl)
                findNavController().navigate(directions)
            }

            null
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
            lifecycleScope.launchWhenResumed {
                model.saveConf {
                    it.copy(
                        backend = ConfRepository.BACKEND_STANDALONE,
                        syncOnStartup = false,
                        backgroundSyncIntervalMillis = TimeUnit.HOURS.toMillis(12),
                    )
                }

                model.setBackend(ConfRepository.BACKEND_STANDALONE)
                model.scheduleBackgroundSync()

                binding.root.animate().alpha(0f).setDuration(150).withEndAction {
                    findNavController().navigate(AuthFragmentDirections.actionAuthFragmentToNewsFragment(EntriesFilter.NotBookmarked))
                }
            }
        }

        useMinifluxBackend.setOnClickListener {
            binding.root.animate().alpha(0f).setDuration(150).withEndAction {
                findNavController().navigate(R.id.action_authFragment_to_minifluxAuthFragment)
            }
        }

        useNextcloudBackend.setOnClickListener {
            binding.root.animate().alpha(0f).setDuration(150).withEndAction {
                findNavController().navigate(R.id.action_authFragment_to_nextcloudAuthFragment)
            }
        }
    }
}