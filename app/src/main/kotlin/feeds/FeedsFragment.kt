package feeds

import android.content.Context
import android.content.DialogInterface
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentFeedsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dialog.showDialog
import dialog.showErrorDialog
import entries.EntriesFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import navigation.openUrl
import navigation.showKeyboard
import opml.format
import opml.toDocument
import org.koin.androidx.viewmodel.ext.android.viewModel

class FeedsFragment : Fragment() {

    private val model: FeedsModel by viewModel()

    private var _binding: FragmentFeedsBinding? = null
    private val binding get() = _binding!!

    private val listAdapter = createFeedsAdapter()
    private val listItemDecoration by lazy { ListItemDecoration(requireContext()) }

    private val importFeedsLauncher = createImportFeedsLauncher()
    private val exportFeedsLauncher = createExportFeedsLauncher()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initToolbar()
        initList()
        initImportButton()
        initFab()

        model.state
            .onEach { setState(it) }
            .catch { showErrorDialog(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        handleAddFeedIntent()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initToolbar() {
        binding.toolbar.setOnMenuItemClickListener {
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

    private fun initList() {
        binding.list.apply {
            setHasFixedSize(true)
            adapter = listAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(listItemDecoration)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (canScrollVertically(1) || !canScrollVertically(-1)) {
                        binding.fab.show()
                    } else {
                        binding.fab.hide()
                    }
                }
            })
        }
    }

    private fun initImportButton() {
        binding.importOpml.setOnClickListener { importFeedsLauncher.launch("*/*") }
    }

    private fun initFab() {
        binding.fab.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.add_feed))
                .setView(R.layout.dialog_add_feed)
                .setPositiveButton(R.string.add) { dialogInterface, _ -> onAddClick(dialogInterface) }
                .setNegativeButton(R.string.cancel, null)
                .show()

            val url = dialog.findViewById<EditText>(R.id.url)!!

            url.setOnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_DONE || keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                    dialog.dismiss()

                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching { model.addFeed(url.text.toString()) }
                            .onFailure { showErrorDialog(it) }
                    }

                    return@setOnEditorActionListener true
                }

                false
            }

            url.requestFocus()
            url.postDelayed({ showKeyboard(url) }, 300)
        }
    }

    private fun setState(state: FeedsModel.State) = binding.apply {
        Log.d("feeds", "New state: ${state.javaClass}")

        when (state) {
            FeedsModel.State.Loading -> {
                list.isVisible = false
                progress.isVisible = true
                message.isVisible = false
                importOpml.isVisible = false
                fab.hide()
            }

            is FeedsModel.State.ShowingFeeds -> {
                listAdapter.submitList(state.feeds)
                list.isVisible = true
                progress.isVisible = false
                message.isVisible = false

                if (state.feeds.isEmpty()) {
                    message.isVisible = true
                    message.text = getString(R.string.you_have_no_feeds)
                    importOpml.isVisible = true
                } else {
                    message.isVisible = false
                    importOpml.isVisible = false
                }

                fab.show()
            }

            is FeedsModel.State.ImportingFeeds -> {
                list.isVisible = false

                progress.isVisible = true
                message.isVisible = true

                message.text = getString(
                    R.string.importing_feeds_n_of_n,
                    state.progress.imported,
                    state.progress.total,
                )

                importOpml.isVisible = false
                fab.hide()
            }
        }
    }

    private fun handleAddFeedIntent() {
        val args = FeedsFragmentArgs.fromBundle(requireArguments())

        if (args.url.isNotBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { model.addFeed(args.url) }
                    .onFailure { showErrorDialog(it) }
            }
        }
    }

    private fun onAddClick(dialogInterface: DialogInterface) {
        val url = (dialogInterface as AlertDialog).findViewById<TextInputEditText>(R.id.url)?.text.toString()

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { model.addFeed(url) }
                .onFailure { showErrorDialog(it) }
        }
    }

    private fun onRenameClick(feedId: String, dialogInterface: DialogInterface) {
        val title = (dialogInterface as AlertDialog).findViewById<TextInputEditText>(R.id.title)!!

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { model.rename(feedId, title.text.toString()) }
                .onFailure { showErrorDialog(it) }
        }
    }

    private fun createFeedsAdapter(): FeedsAdapter {
        return FeedsAdapter(callback = object : FeedsAdapter.Callback {
            override fun onClick(item: FeedsAdapter.Item) {
                findNavController().navigate(
                    FeedsFragmentDirections.actionFeedsFragmentToFeedEntriesFragment(
                        filter = EntriesFilter.BelongToFeed(feedId = item.id),
                    )
                )
            }

            override fun onSettingsClick(item: FeedsAdapter.Item) {
                findNavController().navigate(
                    FeedsFragmentDirections.actionFeedsFragmentToFeedSettingsFragment(
                        feedId = item.id,
                    )
                )
            }

            override fun onOpenSelfLinkClick(item: FeedsAdapter.Item) {
                openUrl(
                    url = item.selfLink.toString(),
                    useBuiltInBrowser = item.confUseBuiltInBrowser,
                )
            }

            override fun onOpenAlternateLinkClick(item: FeedsAdapter.Item) {
                openUrl(
                    url = item.alternateLink.toString(),
                    useBuiltInBrowser = item.confUseBuiltInBrowser,
                )
            }

            override fun onRenameClick(item: FeedsAdapter.Item) {
                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.rename))
                    .setView(R.layout.dialog_rename_feed)
                    .setPositiveButton(R.string.rename) { dialogInterface, _ ->
                        onRenameClick(
                            feedId = item.id,
                            dialogInterface = dialogInterface,
                        )
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()

                val title = dialog.findViewById<TextInputEditText>(R.id.title)!!
                title.append(item.title)

                title.requestFocus()
                title.postDelayed({ showKeyboard(title) }, 300)
            }

            override fun onDeleteClick(item: FeedsAdapter.Item) {
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { model.delete(item.id) }
                        .onFailure { showErrorDialog(it) }
                }
            }
        })
    }

    private fun createImportFeedsLauncher(): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }

            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    val result = withContext(Dispatchers.Default) {
                        requireContext().contentResolver.openInputStream(uri)!!.use { inputStream ->
                            model.importOpml(inputStream.bufferedReader().readText())
                        }
                    }

                    val message = buildString {
                        append(getString(R.string.added_d, result.feedsAdded))
                        append("\n")
                        append(
                            getString(
                                R.string.exists_d,
                                result.feedsUpdated,
                            )
                        )
                        append("\n")
                        append(
                            getString(
                                R.string.failed_d,
                                result.feedsFailed,
                            )
                        )

                        if (result.errors.isNotEmpty()) {
                            append("\n\n")
                        }

                        result.errors.forEach {
                            append(it)

                            if (result.errors.last() != it) {
                                append("\n\n")
                            }
                        }
                    }

                    showDialog(
                        title = getString(R.string.import_title),
                        message = message,
                    )
                }.onFailure {
                    showErrorDialog(it)
                }
            }
        }
    }

    private fun createExportFeedsLauncher(): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
            lifecycleScope.launchWhenResumed {
                withContext(Dispatchers.Default) {
                    requireContext().contentResolver.openOutputStream(uri!!)?.use {
                        it.write(model.generateOpml().toDocument().format().toByteArray())
                    }
                }
            }
        }
    }

    class ListItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

        private val gapInPixels = context.resources.getDimensionPixelSize(R.dimen.dp_8)

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val adapter = parent.adapter

            if (adapter == null || adapter.itemCount == 0) {
                super.getItemOffsets(outRect, view, parent, state)
                return
            }

            val position = parent.getChildLayoutPosition(view)

            val left = 0
            val top = if (position == 0) gapInPixels else 0
            val right = 0
            val bottom = if (position == adapter.itemCount - 1) gapInPixels else 0

            outRect.set(left, top, right, bottom)
        }
    }
}