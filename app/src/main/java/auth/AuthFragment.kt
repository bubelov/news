package auth

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import common.PreferencesRepository
import common.getColorFromAttr
import co.appreactor.news.databinding.FragmentAuthBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.AccountImporter.IAccountAccessGranted
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.ui.UiExceptionManager
import entries.EntriesFilter
import kotlinx.coroutines.runBlocking
import org.koin.android.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class AuthFragment : Fragment() {

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
        hideStatusBarBackground()
        invertStatusBarTextColorInLightMode()

        binding.standaloneMode.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                model.setAuthType(PreferencesRepository.AUTH_TYPE_STANDALONE)

                model.savePreferences {
                    syncOnStartup = false
                    backgroundSyncIntervalMillis = TimeUnit.HOURS.toMillis(12)
                }

                showNews()
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

    private fun hideStatusBarBackground() {
        lifecycle.addObserver(object : LifecycleObserver {
            var oldColor: Int = 0

            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun onResume() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    oldColor = requireActivity().window.statusBarColor

                    requireActivity().window.statusBarColor = ContextCompat.getColor(
                        requireContext(),
                        android.R.color.transparent
                    )
                }
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun onPause() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requireActivity().window.statusBarColor = oldColor
                }
            }
        })
    }

    private fun invertStatusBarTextColorInLightMode() {
        lifecycle.addObserver(object : LifecycleObserver {
            var oldColor: Int = 0

            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun onResume() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val uiMode = requireContext().resources.configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK

                    if (uiMode != Configuration.UI_MODE_NIGHT_YES) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            view?.windowInsetsController?.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            view?.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        }
                    }

                    oldColor = requireActivity().window.navigationBarColor
                    requireActivity().window.navigationBarColor =
                        requireContext().getColorFromAttr(R.attr.colorSurface)
                }
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun onPause() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        view?.windowInsetsController?.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        view?.systemUiVisibility = 0
                    }

                    requireActivity().window.navigationBarColor = oldColor
                }
            }
        })
    }
}