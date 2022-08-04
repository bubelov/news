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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentFeedsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dialog.showErrorDialog
import entries.EntriesFilter
import kotlinx.coroutines.launch
import navigation.openUrl
import navigation.showKeyboard
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { binding.setState(it) }
            }
        }

        handleAddFeedIntent()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initToolbar() {
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.importFeeds -> importFeedsLauncher.launch("*/*")
                R.id.exportFeeds -> exportFeedsLauncher.launch("feeds.opml")
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
            val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.add_feed))
                .setView(R.layout.dialog_add_feed)
                .setPositiveButton(R.string.add) { dialogInterface, _ -> onAddClick(dialogInterface) }
                .setNegativeButton(R.string.cancel, null).show()

            val urlView = dialog.findViewById<EditText>(R.id.url)!!

            urlView.setOnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_DONE || keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                    dialog.dismiss()
                    model.addFeed(urlView.text.toString())
                    return@setOnEditorActionListener true
                }

                false
            }

            urlView.requestFocus()
            urlView.postDelayed({ showKeyboard(urlView) }, 300)
        }
    }

    private fun FragmentFeedsBinding.setState(state: FeedsModel.State) {
        views().forEach { it.isVisible = false }
        visibleViews(state).forEach { it.isVisible = true }

        when (state) {
            is FeedsModel.State.Loading -> {}

            is FeedsModel.State.ShowingFeeds -> {
                listAdapter.submitList(state.feeds)

                if (state.feeds.isEmpty()) {
                    message.isVisible = true
                    message.text = getString(R.string.you_have_no_feeds)
                    importOpml.isVisible = true
                } else {
                    message.isVisible = false
                    importOpml.isVisible = false
                }
            }

            is FeedsModel.State.ImportingFeeds -> {
                message.text = getString(
                    R.string.importing_feeds_n_of_n,
                    state.progress.imported,
                    state.progress.total,
                )
            }

            is FeedsModel.State.ShowingError -> {
                Log.d("feeds", "Showing error!")
                showErrorDialog(state.error) { model.onErrorAcknowledged() }
            }
        }
    }

    private fun FragmentFeedsBinding.views(): List<View> {
        return listOf(toolbar, list, progress, message, importOpml, fab)
    }

    private fun FragmentFeedsBinding.visibleViews(state: FeedsModel.State): List<View> {
        return when (state) {
            is FeedsModel.State.Loading -> listOf(toolbar, progress)
            is FeedsModel.State.ShowingFeeds -> listOf(toolbar, list, message, importOpml, fab)
            is FeedsModel.State.ImportingFeeds -> listOf(toolbar, message)
            is FeedsModel.State.ShowingError -> listOf(toolbar)
        }
    }

    private fun handleAddFeedIntent() {
        val args = FeedsFragmentArgs.fromBundle(requireArguments())

        if (args.url.isNotBlank()) {
            model.addFeed(args.url)
        }
    }

    private fun onAddClick(dialogInterface: DialogInterface) {
        val url = (dialogInterface as AlertDialog).findViewById<TextInputEditText>(R.id.url)?.text.toString()
        model.addFeed(url)
    }

    private fun onRenameClick(feedId: String, dialogInterface: DialogInterface) {
        val title = (dialogInterface as AlertDialog).findViewById<TextInputEditText>(R.id.title)!!
        model.renameFeed(feedId, title.text.toString())
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
                val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.rename))
                    .setView(R.layout.dialog_rename_feed).setPositiveButton(R.string.rename) { dialogInterface, _ ->
                        onRenameClick(
                            feedId = item.id,
                            dialogInterface = dialogInterface,
                        )
                    }.setNegativeButton(R.string.cancel, null).show()

                val title = dialog.findViewById<TextInputEditText>(R.id.title)!!
                title.append(item.title)

                title.requestFocus()
                title.postDelayed({ showKeyboard(title) }, 300)
            }

            override fun onDeleteClick(item: FeedsAdapter.Item) {
                model.deleteFeed(item.id)
            }
        })
    }

    private fun createImportFeedsLauncher(): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }

            model.importOpml(requireContext().contentResolver.openInputStream(uri)!!)
        }
    }

    private fun createExportFeedsLauncher(): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
            if (uri != null) {
                model.exportOpml(requireContext().contentResolver.openOutputStream(uri)!!)
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