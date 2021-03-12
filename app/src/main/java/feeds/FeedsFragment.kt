package feeds

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
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
import opml.readOpml
import opml.writeOpml
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class FeedsFragment : Fragment() {

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
                            binding.swipeRefresh.isRefreshing = true

                            runCatching {
                                val dialog = dialogInterface as AlertDialog
                                val title = dialog.findViewById<TextInputEditText>(R.id.title)!!
                                model.renameFeed(feed.id, title.text.toString())
                            }.onFailure {
                                Timber.e(it)
                                showDialog(R.string.error, it.message ?: "")
                            }

                            binding.swipeRefresh.isRefreshing = false
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
                    binding.swipeRefresh.isRefreshing = true

                    runCatching {
                        model.deleteFeed(feed.id)
                    }.onFailure {
                        Timber.e(it)
                        showDialog(R.string.error, it.message ?: "")
                    }

                    binding.swipeRefresh.isRefreshing = false
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
                        val feeds = readOpml(it.bufferedReader().readText())
                        model.importFeeds(feeds)
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
                    it.write(writeOpml(model.getAllFeeds()).toByteArray())
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
        binding.apply {
            swipeRefresh.isEnabled = false

            toolbar.apply {
                setNavigationOnClickListener {
                    findNavController().popBackStack()
                }

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

            list.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(requireContext())
                adapter = this@FeedsFragment.adapter
                addItemDecoration(FeedsAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))

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
                model.onViewReady()
            }

            lifecycleScope.launchWhenResumed {
                model.state.collectLatest { state ->
                    swipeRefresh.hide()
                    progress.hide()
                    message.hide()
                    importOpml.hide()
                    fab.hide()

                    Timber.d("State: ${state.javaClass.simpleName}")

                    when (state) {
                        is FeedsViewModel.State.Inactive -> {

                        }

                        FeedsViewModel.State.LoadingFeeds -> {
                            progress.show(animate = true)
                        }

                        is FeedsViewModel.State.LoadedFeeds -> {
                            swipeRefresh.show()

                            if (state.feeds.isEmpty()) {
                                message.show(animate = true)
                                message.text = getString(R.string.feeds_list_is_empty)

                                importOpml.show(animate = true)
                            }

                            Timber.d("Got ${state.feeds.size} feeds")
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

                        is FeedsViewModel.State.DisplayingImportResult -> {
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
                                    model.state.value = FeedsViewModel.State.Inactive
                                    model.onViewReady()
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
                            swipeRefresh.isRefreshing = true

                            runCatching {
                                val urlView = dialog.findViewById<TextInputEditText>(R.id.url)!!
                                model.createFeed(urlView.text.toString())
                            }.onFailure {
                                Timber.e(it)
                                showDialog(R.string.error, it.message ?: "")
                            }

                            swipeRefresh.isRefreshing = false
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { hideKeyboard() }
                    .show()

                alert.findViewById<View>(R.id.urlLayout)!!.requestFocus()
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