package co.appreactor.news.auth

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.common.App
import co.appreactor.news.common.Preferences
import co.appreactor.news.common.getColorFromAttr
import co.appreactor.news.di.appModule
import co.appreactor.news.di.dbModule
import co.appreactor.news.di.nextcloudNewsApiModule
import co.appreactor.news.di.standaloneNewsApiModule
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.AccountImporter.IAccountAccessGranted
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.ui.UiExceptionManager
import kotlinx.android.synthetic.main.fragment_auth.*
import kotlinx.coroutines.runBlocking
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.core.module.Module

class AuthFragment : Fragment() {

    private val model: AuthFragmentModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return runBlocking {
            when (model.getAuthType()) {
                Preferences.AUTH_TYPE_STANDALONE -> {
                    showNews(standaloneNewsApiModule)
                    null
                }

                Preferences.AUTH_TYPE_NEXTCLOUD_APP, Preferences.AUTH_TYPE_NEXTCLOUD_DIRECT -> {
                    showNews(nextcloudNewsApiModule)
                    null
                }

                else -> {
                    inflater.inflate(
                        R.layout.fragment_auth,
                        container,
                        false
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        hideStatusBarBackground()
        invertStatusBarTextColorInLightMode()

        standaloneMode.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                model.setAuthType(Preferences.AUTH_TYPE_STANDALONE)
                showNews(standaloneNewsApiModule)
            }
        }

        loginWithNextcloud.setOnClickListener {
            showAccountPicker()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val onAccessGranted = IAccountAccessGranted { account ->
            runBlocking {
                SingleAccountHelper.setCurrentAccount(context, account.name)
                model.setAuthType(Preferences.AUTH_TYPE_NEXTCLOUD_APP)
                showNews(nextcloudNewsApiModule)
            }
        }

        when (resultCode) {
            AppCompatActivity.RESULT_CANCELED -> {
                loginWithNextcloud.isEnabled = true
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

    private fun showAccountPicker() {
        loginWithNextcloud.isEnabled = false

        try {
            AccountImporter.pickNewAccount(this)
        } catch (e: Exception) {
            if (e is SSOException) {
                UiExceptionManager.showDialogForException(context, e)
            }

            loginWithNextcloud.isEnabled = true
        }
    }

    private fun showNews(apiModule: Module) {
        val app = requireContext().applicationContext as App
        app.setUp(appModule, dbModule, apiModule)

        findNavController().apply {
            popBackStack()
            navigate(R.id.entriesFragment)
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
                        view?.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    }

                    oldColor = requireActivity().window.navigationBarColor
                    requireActivity().window.navigationBarColor =
                        requireContext().getColorFromAttr(R.attr.colorSurface)
                }
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun onPause() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    view?.systemUiVisibility = 0
                    requireActivity().window.navigationBarColor = oldColor
                }
            }
        })
    }
}