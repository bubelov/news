package exception

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentAppExceptionBinding
import common.resetToolbar
import common.toolbar
import org.koin.android.viewmodel.ext.android.viewModel

class AppExceptionFragment : Fragment() {

    private val args by lazy {
        AppExceptionFragmentArgs.fromBundle(requireArguments())
    }

    private val model: AppExceptionViewModel by viewModel()

    private var _binding: FragmentAppExceptionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppExceptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launchWhenResumed {
            val exception = model.selectById(args.exceptionId) ?: return@launchWhenResumed

            resetToolbar()

            toolbar().apply {
                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                setNavigationOnClickListener { findNavController().popBackStack() }
                title = exception.exceptionClass
                inflateMenu(R.menu.menu_logged_exception)

                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.share -> {
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, exception.exceptionClass)
                                putExtra(Intent.EXTRA_TEXT, exception.stackTrace)
                            }

                            startActivity(Intent.createChooser(intent, ""))
                        }
                    }

                    true
                }
            }

            binding.stackTrace.text = exception.stackTrace
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}