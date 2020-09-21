package co.appreactor.news.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.common.showDialog
import co.appreactor.news.di.apiModule
import co.appreactor.news.di.appModule
import kotlinx.android.synthetic.main.fragment_direct_auth.*
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class DirectAuthFragment : Fragment() {

    private val model: DirectAuthFragmentModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_direct_auth,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        login.setOnClickListener {
            if (serverUrl.text.isNullOrEmpty()) {
                serverUrlLayout.error = getString(R.string.field_is_empty)
            } else {
                serverUrlLayout.error = null
            }

            if (username.text.isNullOrEmpty()) {
                usernameLayout.error = getString(R.string.field_is_empty)
            } else {
                usernameLayout.error = null
            }

            if (password.text.isNullOrEmpty()) {
                passwordLayout.error = getString(R.string.field_is_empty)
            } else {
                passwordLayout.error = null
            }

            if (serverUrlLayout.error != null || usernameLayout.error != null || passwordLayout.error != null) {
                return@setOnClickListener
            }

            lifecycleScope.launchWhenResumed {
                progress.isVisible = true

                val success = model.isApiAvailable(
                    serverUrl.text.toString(),
                    username.text.toString(),
                    password.text.toString()
                )

                if (success) {
                    model.setServer(
                        serverUrl.text.toString(),
                        username.text.toString(),
                        password.text.toString()
                    )

                    stopKoin()

                    startKoin {
                        androidContext(requireContext())
                        modules(listOf(appModule, apiModule))
                    }

                    findNavController().apply {
                        popBackStack()
                        navigate(R.id.entriesFragment)
                    }
                } else {
                    progress.isVisible = false
                    showDialog(R.string.error, R.string.direct_login_failed)
                }
            }
        }
    }
}