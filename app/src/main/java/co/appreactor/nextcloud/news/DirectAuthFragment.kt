package co.appreactor.nextcloud.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            lifecycleScope.launchWhenResumed {
                login.isEnabled = false

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
                        navigate(R.id.newsFragment)
                    }
                } else {
                    login.isEnabled = true

                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(getString(R.string.direct_login_failed))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }
}