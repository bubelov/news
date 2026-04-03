package org.vestifeed.auth

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import org.vestifeed.navigation.AppFragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.vestifeed.R
import org.vestifeed.api.nextcloud.NextcloudApiBuilder
import org.vestifeed.app.App
import org.vestifeed.databinding.FragmentNextcloudAuthBinding
import org.vestifeed.db.table.ConfSchema
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entries.EntriesFilter
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.sync.BackgroundSyncScheduler

class NextcloudAuthFragment : AppFragment() {

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
            toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

            password.setOnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_DONE || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
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
                val api = NextcloudApiBuilder().build(
                    url = url.toString().trim('/'),
                    username = username,
                    password = password,
                    trustSelfSignedCerts = trustSelfSignedCerts,
                )
                api.getFeeds()
            }.onSuccess {
                val db = (requireContext().applicationContext as App).db
                val syncScheduler = BackgroundSyncScheduler(requireContext())

                db.conf.update {
                    it.copy(
                        backend = ConfSchema.BACKEND_NEXTCLOUD,
                        nextcloudServerUrl = url.toString().trim('/'),
                        nextcloudServerTrustSelfSignedCerts = trustSelfSignedCerts,
                        nextcloudServerUsername = username,
                        nextcloudServerPassword = password,
                    )
                }

                syncScheduler.schedule()

                parentFragmentManager.commit {
                    replace(
                        R.id.fragmentContainerView,
                        EntriesFragment::class.java,
                        bundleOf("filter" to EntriesFilter.Unread),
                    )
                }
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