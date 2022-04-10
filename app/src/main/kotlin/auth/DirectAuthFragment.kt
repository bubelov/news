package auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentDirectAuthBinding
import common.AppFragment
import common.ConfRepository
import common.app
import common.showDialog
import entries.EntriesFilter
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class DirectAuthFragment : AppFragment() {

    private val model: DirectAuthViewModel by viewModel()

    private var _binding: FragmentDirectAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDirectAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar?.apply {
            setupUpNavigation()
            setTitle(R.string.nextcloud_login)
        }

        binding.login.setOnClickListener {
            if (binding.serverUrl.text.isNullOrEmpty()) {
                binding.serverUrlLayout.error = getString(R.string.field_is_empty)
            } else {
                binding.serverUrlLayout.error = null
            }

            if (binding.username.text.isNullOrEmpty()) {
                binding.usernameLayout.error = getString(R.string.field_is_empty)
            } else {
                binding.usernameLayout.error = null
            }

            if (binding.password.text.isNullOrEmpty()) {
                binding.passwordLayout.error = getString(R.string.field_is_empty)
            } else {
                binding.passwordLayout.error = null
            }

            if (binding.serverUrlLayout.error != null
                || binding.usernameLayout.error != null
                || binding.passwordLayout.error != null
            ) {
                return@setOnClickListener
            }

            lifecycleScope.launchWhenResumed {
                binding.progress.isVisible = true

                runCatching {
                    model.requestFeeds(
                        serverUrl = binding.serverUrl.text.toString(),
                        username = binding.username.text.toString(),
                        password = binding.password.text.toString(),
                        trustSelfSignedCerts = binding.trustSelfSignedCerts.isChecked,
                    )
                }.onSuccess {
                    model.setServer(
                        binding.serverUrl.text.toString(),
                        binding.username.text.toString(),
                        binding.password.text.toString(),
                        binding.trustSelfSignedCerts.isChecked,
                    )

                    model.setAuthType(ConfRepository.AUTH_TYPE_NEXTCLOUD_DIRECT)

                    app().setupBackgroundSync(override = true)

                    findNavController().apply {
                        popBackStack()
                        navigate(
                            R.id.entriesFragment,
                            bundleOf(Pair("filter", EntriesFilter.NotBookmarked))
                        )
                    }
                }.onFailure {
                    binding.progress.isVisible = false
                    Timber.e(it)
                    requireContext().showDialog(
                        R.string.error,
                        it.message ?: getString(R.string.direct_login_failed)
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}