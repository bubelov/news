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
import co.appreactor.news.databinding.FragmentMinifluxAuthBinding
import dialog.showErrorDialog
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.androidx.viewmodel.ext.android.viewModel

class MinifluxAuthFragment : Fragment() {

    private val model: MinifluxAuthModel by viewModel()

    private var _binding: FragmentMinifluxAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMinifluxAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
            initConnectButton()
        }
    }

    private fun FragmentMinifluxAuthBinding.initConnectButton() {
        connect.setOnClickListener {
            if (!validate()) {
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                progress.isVisible = true

                val url = url.text.toString().toHttpUrl()
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

                    findNavController().navigate(R.id.action_minifluxAuthFragment_to_newsFragment)
                }.onFailure {
                    binding.progress.isVisible = false
                    showErrorDialog(it.message ?: getString(R.string.direct_login_failed))
                }
            }
        }
    }

    private fun FragmentMinifluxAuthBinding.validate(): Boolean {
        urlLayout.error = when (url.text.toString().length) {
            0 -> getString(R.string.field_is_empty)
            else -> null
        }

        urlLayout.error = when (binding.url.text.toString().toHttpUrlOrNull()) {
            null -> getString(R.string.invalid_url)
            else -> null
        }

        usernameLayout.error = when (username.text.toString().length) {
            0 -> getString(R.string.field_is_empty)
            else -> null
        }

        if (binding.password.text.isNullOrEmpty()) {
            binding.passwordLayout.error = getString(R.string.field_is_empty)
        } else {
            binding.passwordLayout.error = null
        }

        return urlLayout.error == null && usernameLayout.error == null && passwordLayout.error == null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}