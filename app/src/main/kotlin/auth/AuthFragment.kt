package auth

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentAuthBinding
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.model.SingleSignOnAccount
import com.nextcloud.android.sso.ui.UiExceptionManager
import common.AppFragment
import common.ConfRepository
import common.app
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel
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
        val conf = runBlocking { model.selectConf().first() }

        return if (conf.authType.isBlank()) {
            _binding = FragmentAuthBinding.inflate(inflater, container, false)
            binding.root
        } else {
            findNavController().navigate(R.id.action_authFragment_to_entriesFragment)
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginWithMiniflux.setOnClickListener {
            findNavController().navigate(R.id.action_authFragment_to_minifluxAuthFragment)
        }

        binding.loginWithNextcloudApp.setOnClickListener {
            showAccountPicker()
        }

        binding.loginWithNextcloudServer.setOnClickListener {
            findNavController().navigate(R.id.action_authFragment_to_directAuthFragment)
        }

        binding.standaloneMode.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                model.upsertConf(
                    model.selectConf().first().copy(
                        authType = ConfRepository.AUTH_TYPE_STANDALONE,
                        syncOnStartup = false,
                        backgroundSyncIntervalMillis = TimeUnit.HOURS.toMillis(12),
                        initialSyncCompleted = true,
                    )
                )

                app().setupBackgroundSync(override = true)

                findNavController().navigate(R.id.action_authFragment_to_entriesFragment)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (binding.icon.drawable as? Animatable)?.start()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (resultCode) {
            AppCompatActivity.RESULT_CANCELED -> setButtonsEnabled(true)

            else -> {
                AccountImporter.onActivityResult(
                    requestCode,
                    resultCode,
                    data,
                    this
                ) { onNextcloudAccountAccessGranted(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onNextcloudAccountAccessGranted(account: SingleSignOnAccount) {
        SingleAccountHelper.setCurrentAccount(context, account.name)

        runBlocking {
            val conf = model.selectConf().first()
            model.upsertConf(conf.copy(authType = ConfRepository.AUTH_TYPE_NEXTCLOUD_APP))
        }

        app().setupBackgroundSync(override = true)

        findNavController().navigate(R.id.action_authFragment_to_entriesFragment)
    }

    private fun showAccountPicker() {
        setButtonsEnabled(false)

        try {
            AccountImporter.pickNewAccount(this)
        } catch (e: Exception) {
            if (e is SSOException) {
                UiExceptionManager.showDialogForException(context, e)
            }

            setButtonsEnabled(true)
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.loginWithMiniflux.isEnabled = enabled
        binding.loginWithNextcloudApp.isEnabled = enabled
        binding.loginWithNextcloudServer.isEnabled = enabled
        binding.standaloneMode.isEnabled = enabled
    }
}