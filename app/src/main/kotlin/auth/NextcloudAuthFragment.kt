package auth

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentNextcloudAuthBinding
import dialog.showErrorDialog
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.androidx.viewmodel.ext.android.viewModel

class NextcloudAuthFragment : Fragment() {

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

        binding.apply {
            toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

            password.setOnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_DONE || keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                    connect()
                    return@setOnEditorActionListener true
                }

                false
            }

            connect.setOnClickListener { connect() }
        }
    }

    private fun connect() {
        if (!binding.validate()) {
            return
        }

        binding.progress.isVisible = true

        val url = binding.url.text.toString().toHttpUrl()
        val username = binding.username.text.toString()
        val password = binding.password.text.toString()
        val trustSelfSignedCerts = binding.trustSelfSignedCerts.isChecked

        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
            runCatching {
                model.testBackend(
                    url = url,
                    username = username,
                    password = password,
                    trustSelfSignedCerts = trustSelfSignedCerts,
                )
            }.onSuccess {
                model.setBackend(
                    url = url,
                    username = username,
                    password = password,
                    trustSelfSignedCerts = trustSelfSignedCerts,
                )

                findNavController().navigate(R.id.action_minifluxAuthFragment_to_newsFragment)
            }.onFailure {
                binding.progress.isVisible = false
                showErrorDialog(it.message ?: getString(R.string.direct_login_failed))
            }
        }
    }

    private fun FragmentNextcloudAuthBinding.validate(): Boolean {
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