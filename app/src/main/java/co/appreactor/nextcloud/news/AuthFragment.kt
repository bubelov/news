package co.appreactor.nextcloud.news

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
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.AccountImporter.IAccountAccessGranted
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.ui.UiExceptionManager
import kotlinx.android.synthetic.main.fragment_auth.*
import kotlinx.coroutines.launch


class AuthFragment : Fragment() {

    init {
        hideStatusBarBackground()
        invertStatusBarTextColorInLightMode()

        lifecycleScope.launch {
            whenStarted {
                try {
                    SingleAccountHelper.getCurrentSingleSignOnAccount(context)

                    findNavController().apply {
                        popBackStack()
                        navigate(R.id.newsFragment)
                    }
                } catch (e: SSOException) {
                    loginViaApp.setOnClickListener {
                        it.isEnabled = false
                        showAccountPicker()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_auth,
            container,
            false
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val onAccessGranted = IAccountAccessGranted { account ->
            SingleAccountHelper.setCurrentAccount(context, account.name)

            findNavController().apply {
                popBackStack()
                navigate(R.id.newsFragment)
            }
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
        try {
            AccountImporter.pickNewAccount(this)
        } catch (e: SSOException) {
            UiExceptionManager.showDialogForException(context, e)
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