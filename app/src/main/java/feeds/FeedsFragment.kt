package feeds

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentFeedsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import common.*
import entries.EntriesFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class FeedsFragment : AppFragment() {

    private val model: FeedsViewModel by viewModel()

    private var _binding: FragmentFeedsBinding? = null
    private val binding get() = _binding!!

    private val adapter =
        FeedsAdapter(scope = lifecycleScope, callback = object : FeedsAdapterCallback {
            override fun onSettingsClick(feed: FeedsAdapterItem) {
                findNavController().navigate(
                    FeedsFragmentDirections.actionFeedsFragmentToFeedSettingsFragment(
                        feedId = feed.id,
                    )
                )
            }

            override fun onFeedClick(feed: FeedsAdapterItem) {
                findNavController().navigate(
                    FeedsFragmentDirections.actionFeedsFragmentToFeedEntriesFragment(
                        EntriesFilter.OnlyFromFeed(feedId = feed.id)
                    )
                )
            }

            override fun onOpenHtmlLinkClick(feed: FeedsAdapterItem) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(feed.alternateLink)
                startActivity(intent)
            }

            override fun openLinkClick(feed: FeedsAdapterItem) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(feed.selfLink)
                startActivity(intent)
            }

            override fun onRenameClick(feed: FeedsAdapterItem) {
                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.rename))
                    .setView(R.layout.dialog_rename_feed)
                    .setPositiveButton(R.string.rename) { dialogInterface, _ ->
                        lifecycleScope.launchWhenResumed {
                            runCatching {
                                val dialog = dialogInterface as AlertDialog
                                val title = dialog.findViewById<TextInputEditText>(R.id.title)!!
                                model.renameFeed(feed.id, title.text.toString())
                            }.onFailure {
                                showErrorDialog(it)
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { hideKeyboard() }
                    .show()

                val title = dialog.findViewById<TextInputEditText>(R.id.title)!!
                title.append(feed.title)
                title.requestFocus()

                requireContext().showKeyboard()
            }

            override fun onDeleteClick(feed: FeedsAdapterItem) {
                lifecycleScope.launchWhenResumed {
                    runCatching {
                        model.deleteFeed(feed.id)
                    }.onFailure {
                        showErrorDialog(it)
                    }
                }
            }
        })

    @Suppress("BlockingMethodInNonBlockingContext")
    private val importFeedsLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        lifecycleScope.launchWhenResumed {
            withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri)?.use {
                    runCatching {
                        model.importFeeds(it.bufferedReader().readText())
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            showErrorDialog(it)
                        }
                    }
                }
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private val exportFeedsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        lifecycleScope.launchWhenResumed {
            withContext(Dispatchers.IO) {
                requireContext().contentResolver.openOutputStream(uri)?.use {
                    it.write(model.getFeedsOpml())
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.apply {
            setTitle(R.string.feeds)
            inflateMenu(R.menu.menu_feeds)

            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.importFeeds -> {
                        importFeedsLauncher.launch("*/*")
                    }

                    R.id.exportFeeds -> {
                        exportFeedsLauncher.launch("feeds.opml")
                    }
                }

                true
            }
        }

        binding.apply {
            list.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(requireContext())
                adapter = this@FeedsFragment.adapter
                addItemDecoration(ListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (list.canScrollVertically(1) || !list.canScrollVertically(-1)) {
                            fab.show()
                        } else {
                            fab.hide()
                        }
                    }
                })
            }

            lifecycleScope.launchWhenResumed {
                if (model.state.value is FeedsViewModel.State.Inactive) {
                    model.loadFeeds()
                }
            }

            lifecycleScope.launchWhenResumed {
                model.state.collectLatest { state ->
                    list.hide()
                    progress.hide()
                    message.hide()
                    importOpml.hide()
                    fab.hide()

                    Timber.d("State: ${state.javaClass.simpleName}")

                    when (state) {
                        is FeedsViewModel.State.Inactive -> {

                        }

                        FeedsViewModel.State.Loading -> {
                            progress.show(animate = true)
                        }

                        is FeedsViewModel.State.ShowingFeeds -> {
                            Timber.d("Got ${state.feeds.size} feeds")

                            if (state.feeds.isEmpty()) {
                                message.show(animate = true)
                                message.text = getString(R.string.you_have_no_feeds)

                                importOpml.show(animate = true)
                            }

                            list.show()
                            adapter.submitList(state.feeds)

                            fab.show()
                        }

                        is FeedsViewModel.State.ImportingFeeds -> {
                            progress.show(animate = true)

                            message.show(animate = true)

                            state.progress.collectLatest { progress ->
                                message.text = getString(
                                    R.string.importing_feeds_n_of_n,
                                    progress.imported,
                                    progress.total,
                                )
                            }
                        }

                        is FeedsViewModel.State.ShowingImportResult -> {
                            val message = buildString {
                                append(getString(R.string.added_d, state.result.added))
                                append("\n")
                                append(getString(R.string.exists_d, state.result.exists))
                                append("\n")
                                append(getString(R.string.failed_d, state.result.failed))

                                if (state.result.errors.isNotEmpty()) {
                                    append("\n\n")
                                }

                                state.result.errors.forEach {
                                    append(it)

                                    if (state.result.errors.last() != it) {
                                        append("\n\n")
                                    }
                                }
                            }

                            showDialog(
                                title = getString(R.string.import_title),
                                message = message,
                            ) {
                                lifecycleScope.launchWhenResumed {
                                    model.loadFeeds()
                                }
                            }
                        }
                    }
                }
            }

            importOpml.setOnClickListener {
                importFeedsLauncher.launch("*/*")
            }

            fab.setOnClickListener {
                val alert = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.add_feed))
                    .setView(R.layout.dialog_add_feed)
                    .setPositiveButton(R.string.add) { dialogInterface, _ ->
                        val dialog = dialogInterface as AlertDialog

                        lifecycleScope.launchWhenResumed {
                            runCatching {
                                dialog.findViewById<TextInputEditText>(R.id.url)?.apply {
                                    model.createFeed(text.toString())
                                }
                            }.onFailure {
                                showErrorDialog(it)
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { hideKeyboard() }
                    .show()

                alert.findViewById<View>(R.id.urlLayout)?.requestFocus()

                alert.findViewById<EditText>(R.id.url)?.apply {
                    setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            alert.dismiss()

                            lifecycleScope.launchWhenResumed {
                                runCatching {
                                    model.createFeed(text.toString())
                                }.onFailure {
                                    showErrorDialog(it)
                                }
                            }

                            return@setOnEditorActionListener true
                        }

                        false
                    }
                }

                requireContext().showKeyboard()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun hideKeyboard() {
        requireActivity().window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
    }
}