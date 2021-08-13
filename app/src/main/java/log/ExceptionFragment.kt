package log

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentExceptionBinding
import common.AppFragment
import org.koin.android.viewmodel.ext.android.viewModel

class ExceptionFragment : AppFragment() {

    private val args by lazy {
        ExceptionFragmentArgs.fromBundle(requireArguments())
    }

    private val model: ExceptionViewModel by viewModel()

    private var _binding: FragmentExceptionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExceptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launchWhenResumed {
            val log = model.selectById(args.logId) ?: return@launchWhenResumed

            toolbar.apply {
                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                setNavigationOnClickListener { findNavController().popBackStack() }
                title = log.message
                inflateMenu(R.menu.menu_exception)

                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.share -> {
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, log.message)
                                putExtra(Intent.EXTRA_TEXT, log.stackTrace)
                            }

                            startActivity(Intent.createChooser(intent, ""))
                        }
                    }

                    true
                }
            }

            binding.stackTrace.text = log.stackTrace
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}