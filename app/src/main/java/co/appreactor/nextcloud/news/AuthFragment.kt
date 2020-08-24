package co.appreactor.nextcloud.news

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
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
        lifecycleScope.launch {
            whenStarted {
                try {
                    SingleAccountHelper.getCurrentSingleSignOnAccount(context)
                    findNavController().navigate(R.id.action_authFragment_to_newsFragment)
                } catch (e: SSOException) {
                    login.setOnClickListener {
                        it.isVisible = false
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
            findNavController().navigate(R.id.action_authFragment_to_newsFragment)
        }

        when(resultCode) {
            AppCompatActivity.RESULT_CANCELED -> {
                login.isVisible = true
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
}