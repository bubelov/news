package co.appreactor.nextcloud.news.auth

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
import co.appreactor.nextcloud.news.R
import co.appreactor.nextcloud.news.di.apiModule
import co.appreactor.nextcloud.news.di.appModule
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.AccountImporter.IAccountAccessGranted
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.ui.UiExceptionManager
import kotlinx.android.synthetic.main.fragment_auth.*
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class AuthFragment : Fragment() {

    private val model: AuthFragmentModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return runBlocking {
            if (!model.isLoggedIn(requireContext())) {
                inflater.inflate(
                    R.layout.fragment_auth,
                    container,
                    false
                )
            } else {
                showNews()
                null
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        hideStatusBarBackground()
        invertStatusBarTextColorInLightMode()

        loginViaApp.setOnClickListener {
            showAccountPicker()
        }

        directLogin.setOnClickListener {
            findNavController().navigate(AuthFragmentDirections.actionAuthFragmentToDirectAuthFragment())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val onAccessGranted = IAccountAccessGranted { account ->
            SingleAccountHelper.setCurrentAccount(context, account.name)
            showNews()
        }

        when (resultCode) {
            AppCompatActivity.RESULT_CANCELED -> {
                loginViaApp.isEnabled = true
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
        loginViaApp.isEnabled = false

        try {
            AccountImporter.pickNewAccount(this)
        } catch (e: Exception) {
            if (e is SSOException) {
                UiExceptionManager.showDialogForException(context, e)
            }

            loginViaApp.isEnabled = true
        }
    }

    private fun showNews() {
        stopKoin()

        startKoin {
            androidContext(requireContext())
            modules(listOf(appModule, apiModule))
        }

        findNavController().apply {
            popBackStack()
            navigate(R.id.newsFragment)
        }
    }

    private fun hideStatusBarBackground() {
        lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun onResume() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requireActivity().window.statusBarColor = ContextCompat.getColor(
                        requireContext(),
                        android.R.color.transparent
                    )
                }
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun onPause() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requireActivity().window.statusBarColor = ContextCompat.getColor(
                        requireContext(),
                        R.color.color_primary_dark
                    )
                }
            }
        })
    }

    private fun invertStatusBarTextColorInLightMode() {
        lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun onResume() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val uiMode = requireContext().resources.configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK

                    if (uiMode != Configuration.UI_MODE_NIGHT_YES) {
                        view?.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    }
                }
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun onPause() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    view?.systemUiVisibility = 0
                }
            }
        })
    }
}