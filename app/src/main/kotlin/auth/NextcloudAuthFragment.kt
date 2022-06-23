package auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentNextcloudAuthBinding
import common.AppFragment
import common.showErrorDialog
import common.sharedToolbar
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koin.androidx.viewmodel.ext.android.viewModel

class NextcloudAuthFragment : AppFragment() {

    private val model: NextcloudAuthModel by viewModel()

    private var _binding: FragmentNextcloudAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNextcloudAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedToolbar()?.apply {
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

            viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                binding.progress.isVisible = true

                val url = binding.serverUrl.text.toString().toHttpUrl()
                val username = binding.username.text.toString()
                val password = binding.password.text.toString()
                val trustSelfSignedCerts = binding.trustSelfSignedCerts.isChecked

                runCatching {
                    model.testServerConf(
                        url = url,
                        username = username,
                        password = password,
                        trustSelfSignedCerts = trustSelfSignedCerts,
                    )
                }.onSuccess {
                    model.saveServerConf(
                        url = url,
                        username = username,
                        password = password,
                        trustSelfSignedCerts = trustSelfSignedCerts,
                    )

                    model.scheduleBackgroundSync()

                    findNavController().navigate(R.id.action_nextcloudAuthFragment_to_newsFragment)
                }.onFailure {
                    binding.progress.isVisible = false
                    showErrorDialog(it.message ?: getString(R.string.direct_login_failed))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}