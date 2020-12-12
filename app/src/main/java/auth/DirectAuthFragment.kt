package auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import common.Preferences
import common.showDialog
import co.appreactor.news.databinding.FragmentDirectAuthBinding
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class DirectAuthFragment : Fragment() {

    private val model: DirectAuthFragmentModel by viewModel()

    private var _binding: FragmentDirectAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDirectAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
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
                        binding.serverUrl.text.toString(),
                        binding.username.text.toString(),
                        binding.password.text.toString()
                    )
                }.onSuccess {
                    model.setServer(
                        binding.serverUrl.text.toString(),
                        binding.username.text.toString(),
                        binding.password.text.toString()
                    )

                    model.setAuthType(Preferences.AUTH_TYPE_NEXTCLOUD_DIRECT)

                    findNavController().apply {
                        popBackStack()
                        navigate(R.id.entriesFragment)
                    }
                }.onFailure {
                    binding.progress.isVisible = false
                    Timber.e(it)
                    showDialog(
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