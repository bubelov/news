package auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentMinifluxAuthBinding
import common.app
import common.showErrorDialog
import common.toolbar
import okhttp3.HttpUrl.Companion.toHttpUrl
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

        toolbar()?.apply {
            navigationIcon = DrawerArrowDrawable(context).apply { progress = 1f }
            setNavigationOnClickListener { findNavController().popBackStack() }
            setTitle(R.string.miniflux_login)
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

                    app().setupBackgroundSync(override = true)

                    findNavController().navigate(R.id.action_minifluxAuthFragment_to_entriesFragment)
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