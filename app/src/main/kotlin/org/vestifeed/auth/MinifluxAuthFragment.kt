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
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.vestifeed.R
import org.vestifeed.api.miniflux.MinifluxApiBuilder
import org.vestifeed.app.App
import org.vestifeed.db.ConfQueries
import org.vestifeed.databinding.FragmentMinifluxAuthBinding
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entries.EntriesFilter
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.sync.BackgroundSyncScheduler

class MinifluxAuthFragment : AppFragment() {

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
            toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

            token.setOnEditorActionListener { _, actionId, keyEvent ->
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
        val token = binding.token.text.toString()
        val trustSelfSignedCerts = binding.trustSelfSignedCerts.isChecked

        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
            runCatching {
                val api = MinifluxApiBuilder().build(
                    url = url.toString().trim('/'),
                    token = token,
                    trustSelfSignedCerts = trustSelfSignedCerts,
                )
                api.getFeeds()
            }.onSuccess {
                val db = (requireContext().applicationContext as App).db
                val syncScheduler = BackgroundSyncScheduler(requireContext())

                db.confQueries.update {
                    it.copy(
                        backend = ConfQueries.BACKEND_MINIFLUX,
                        minifluxServerUrl = url.toString().trim('/'),
                        minifluxServerTrustSelfSignedCerts = trustSelfSignedCerts,
                        minifluxServerToken = token,
                    )
                }

                syncScheduler.schedule()

                parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

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

    private fun FragmentMinifluxAuthBinding.validate(): Boolean {
        urlLayout.error = when (url.text.toString().length) {
            0 -> getString(R.string.field_is_empty)
            else -> null
        }

        urlLayout.error = when (binding.url.text.toString().toHttpUrlOrNull()) {
            null -> getString(R.string.invalid_url)
            else -> null
        }

        if (binding.token.text.isNullOrEmpty()) {
            binding.tokenLayout.error = getString(R.string.field_is_empty)
        } else {
            binding.tokenLayout.error = null
        }

        return urlLayout.error == null && tokenLayout.error == null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
