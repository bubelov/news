package auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentAuthBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.AccountImporter.IAccountAccessGranted
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.ui.UiExceptionManager
import common.*
import entries.EntriesFilter
import kotlinx.coroutines.runBlocking
import org.koin.android.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class AuthFragment : AppFragment(
    showToolbar = false,
) {

    private val model: AuthViewModel by viewModel()

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return runBlocking {
            when (model.getAuthType()) {
                PreferencesRepository.AUTH_TYPE_STANDALONE -> {
                    showNews()
                    null
                }

                PreferencesRepository.AUTH_TYPE_NEXTCLOUD_APP, PreferencesRepository.AUTH_TYPE_NEXTCLOUD_DIRECT -> {
                    showNews()
                    null
                }

                else -> {
                    _binding = FragmentAuthBinding.inflate(inflater, container, false)
                    binding.root
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.standaloneMode.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                model.setAuthType(PreferencesRepository.AUTH_TYPE_STANDALONE)

                model.savePreferences {
                    syncOnStartup = false
                    backgroundSyncIntervalMillis = TimeUnit.HOURS.toMillis(12)
                }

                app().setupBackgroundSync(override = true)

                showFeeds()
            }
        }

        binding.loginWithNextcloud.setOnClickListener {
            showAccountPicker()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val onAccessGranted = IAccountAccessGranted { account ->
            runBlocking {
                SingleAccountHelper.setCurrentAccount(context, account.name)
                model.setAuthType(PreferencesRepository.AUTH_TYPE_NEXTCLOUD_APP)
                app().setupBackgroundSync(override = true)
                showNews()
            }
        }

        when (resultCode) {
            AppCompatActivity.RESULT_CANCELED -> {
                binding.loginWithNextcloud.isEnabled = true
            }

            else -> {
                AccountImporter.onActivityResult(
                    requestCode,
                    resultCode,
                    data,
                    this,
                    onAccessGranted
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showAccountPicker() {
        binding.loginWithNextcloud.isEnabled = false

        try {
            AccountImporter.pickNewAccount(this)
        } catch (e: Exception) {
            if (e is SSOException) {
                UiExceptionManager.showDialogForException(
                    context, e
                ) { _, _ ->
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.do_you_want_to_connect_to_nextcloud)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            findNavController().navigate(R.id.action_authFragment_to_directAuthFragment)
                        }
                        .setNegativeButton(R.string.no, null)
                        .show()
                }
            }

            binding.loginWithNextcloud.isEnabled = true
        }
    }

    private fun showNews() {
        findNavController().apply {
            popBackStack()
            navigate(
                R.id.entriesFragment,
                bundleOf(Pair("filter", EntriesFilter.OnlyNotBookmarked))
            )
        }
    }

    private fun showFeeds() {
        findNavController().apply {
            popBackStack()
            navigate(R.id.feedsFragment)
        }
    }
}