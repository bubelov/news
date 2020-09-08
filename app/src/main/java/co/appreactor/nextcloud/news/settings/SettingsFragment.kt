package co.appreactor.nextcloud.news.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.nextcloud.news.NavGraphDirections
import co.appreactor.nextcloud.news.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class SettingsFragment : Fragment() {

    private val model: SettingsFragmentModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_settings,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            showReadNews.isChecked = model.getShowReadNews().first()

            showReadNews.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    model.setShowReadNews(isChecked)
                }
            }

            username.text = model.getAccountName()
        }

        logOut.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.log_out_warning)
                .setPositiveButton(
                    R.string.log_out
                ) { _, _ ->
                    lifecycleScope.launch {
                        model.clearData()
                        findNavController().popBackStack(R.id.newsFragment, true)
                        findNavController().navigate(NavGraphDirections.actionGlobalToAuthFragment())
                    }
                }.setNegativeButton(
                    R.string.cancel,
                    null
                ).show()
        }
    }
}