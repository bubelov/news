package org.vestifeed.feeds

import android.content.Context
import android.content.DialogInterface
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.feedk.AtomLinkRel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.vestifeed.R
import org.vestifeed.anim.animateVisibilityChanges
import org.vestifeed.anim.showSmooth
import org.vestifeed.app.App
import org.vestifeed.app.api
import org.vestifeed.db.SelectAllWithUnreadEntryCount
import org.vestifeed.databinding.FragmentFeedsBinding
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entries.EntriesFilter
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.entries.EntriesRepo
import org.vestifeed.feedsettings.FeedSettingsFragment
import org.vestifeed.navigation.AppFragment
import org.vestifeed.navigation.openUrl
import org.vestifeed.navigation.showKeyboard
import org.vestifeed.opml.OpmlDocument
import org.vestifeed.opml.OpmlOutline
import org.vestifeed.opml.OpmlVersion
import org.vestifeed.opml.leafOutlines
import org.vestifeed.opml.toOpml
import org.vestifeed.opml.toPrettyString
import org.vestifeed.opml.toXmlDocument
import java.io.InputStream
import java.io.OutputStream
import javax.xml.parsers.DocumentBuilderFactory

class FeedsFragment : AppFragment() {

    private val db by lazy { (requireContext().applicationContext as App).db }
    private val api by lazy { api() }
    private val feedsRepo by lazy { FeedsRepo(api, db) }
    private val entriesRepo by lazy { EntriesRepo(api, db) }

    private val _state = MutableStateFlow<State>(State.Loading)
    private val state = _state.asStateFlow()

    private val hasActionInProgress = MutableStateFlow(false)
    private val importState = MutableStateFlow<ImportState?>(null)
    private val error = MutableStateFlow<Throwable?>(null)

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.statusBars()).let {
                v.updatePadding(top = it.top)
            }
            insets
        }

        initToolbar()
        initList()
        initImportButton()
        initFab()

        combine(
            feedsRepo.selectAllWithUnreadEntryCount(),
            hasActionInProgress,
            importState,
            error,
        ) { feeds, hasActionInProgress, importState, error ->
            if (error != null) {
                _state.update { State.ShowingError(error) }
                return@combine
            }

            if (importState != null) {
                _state.update { State.ImportingFeeds(importState) }
                return@combine
            }

            if (hasActionInProgress) {
                _state.update { State.Loading }
                return@combine
            }

            _state.update { State.ShowingFeeds(feeds.map { it.toItem() }) }
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                state.collect { binding.setState(it) }
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
                R.id.exportFeeds -> exportFeedsLauncher.launch("org.vestifeed.feeds.org.vestifeed.opml")
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
            val dialog =
                MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.add_feed))
                    .setView(R.layout.dialog_add_feed)
                    .setPositiveButton(R.string.add) { dialogInterface, _ ->
                        onAddClick(
                            dialogInterface
                        )
                    }
                    .setNegativeButton(R.string.cancel, null).show()

            val urlView = dialog.findViewById<EditText>(R.id.url)!!

            urlView.setOnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_DONE || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    dialog.dismiss()
                    addFeed(urlView.text.toString())
                    return@setOnEditorActionListener true
                }

                false
            }

            urlView.requestFocus()
            urlView.postDelayed({ showKeyboard(urlView) }, 300)
        }
    }

    private fun importOpml(document: InputStream) {
        viewLifecycleOwner.lifecycleScope.launch {
            val outlines = runCatching {
                withContext(Dispatchers.IO) {
                    DocumentBuilderFactory
                        .newInstance()
                        .newDocumentBuilder()
                        .parse(document)
                        .toOpml()
                        .leafOutlines()
                }
            }.getOrElse { e ->
                error.update { e }
                return@launch
            }

            importState.update { ImportState(0, outlines.size) }

            var feedsImported = 0
            var feedsExisted = 0
            var feedsFailed = 0
            val errors = mutableListOf<String>()

            val mutex = Mutex()

            val outlinesChannel = produce { outlines.forEach { send(it) } }
            val existingLinks = feedsRepo.selectLinks().first().flatten()

            val workers = buildList {
                repeat(15) {
                    add(
                        async {
                            for (outline in outlinesChannel) {
                                val outlineUrl = (outline.xmlUrl ?: "").toHttpUrlOrNull()

                                if (outlineUrl == null) {
                                    mutex.withLock {
                                        errors += "Invalid URL: ${outline.xmlUrl}"
                                        feedsFailed++
                                    }

                                    continue
                                }

                                val feedAlreadyExists = existingLinks.any {
                                    it.href.toUri().normalize() == outlineUrl.toUri().normalize()
                                }

                                if (feedAlreadyExists) {
                                    mutex.withLock { feedsExisted++ }
                                } else {
                                    runCatching {
                                        feedsRepo.insertByUrl(outlineUrl)
                                    }.onSuccess {
                                        mutex.withLock { feedsImported++ }
                                    }.onFailure {
                                        mutex.withLock {
                                            errors += "Failed to import feed ${outline.xmlUrl}\nReason: ${it.message}"
                                            feedsFailed++
                                        }
                                    }

                                    importState.update {
                                        ImportState(
                                            imported = feedsImported + feedsExisted + feedsFailed,
                                            total = outlines.size,
                                        )
                                    }
                                }
                            }
                        }

                    )
                }
            }

            workers.awaitAll()

            if (errors.isNotEmpty()) {
                val message = buildString {
                    errors.forEach {
                        append(it)

                        if (errors.last() != it) {
                            append("\n\n")
                        }
                    }
                }

                error.update { Exception(message) }
            }

            importState.update { null }
        }
    }

    private fun exportOpml(out: OutputStream) {
        viewLifecycleOwner.lifecycleScope.launch {
            val feeds = feedsRepo.selectAll().first()

            val outlines = feeds.map { feed ->
                val selfLink = feed.links.firstOrNull { it.rel is AtomLinkRel.Self }
                    ?: feed.links.firstOrNull()
                OpmlOutline(
                    text = feed.title,
                    outlines = emptyList(),
                    xmlUrl = selfLink?.href?.toString() ?: "",
                    htmlUrl = feed.links.firstOrNull { it.rel is AtomLinkRel.Alternate }?.href?.toString(),
                    extOpenEntriesInBrowser = feed.extOpenEntriesInBrowser,
                    extShowPreviewImages = feed.extShowPreviewImages,
                    extBlockedWords = feed.extBlockedWords,
                )
            }

            val opmlDocument = OpmlDocument(
                version = OpmlVersion.V_2_0,
                outlines = outlines,
            )

            withContext(Dispatchers.IO) {
                out.write(opmlDocument.toXmlDocument().toPrettyString().toByteArray())
            }
        }
    }

    private fun onErrorAcknowledged() {
        error.update { null }
    }

    private fun addFeed(unvalidatedUrl: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                hasActionInProgress.update { true }

                val hasHttpPrefix =
                    unvalidatedUrl.startsWith("http") or unvalidatedUrl.startsWith("https")

                val entries = if (hasHttpPrefix) {
                    feedsRepo.insertByUrl(unvalidatedUrl.toHttpUrl()).second
                } else {
                    feedsRepo.insertByUrl("https://$unvalidatedUrl".toHttpUrl()).second
                }

                entriesRepo.insertOrReplace(entries)
            }.onSuccess {
                hasActionInProgress.update { false }
            }.onFailure { e ->
                hasActionInProgress.update { false }
                error.update { e }
            }
        }
    }

    private fun renameFeed(feedId: String, newTitle: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                hasActionInProgress.update { true }
                feedsRepo.updateTitle(feedId, newTitle)
            }.onFailure { e -> error.update { e } }

            hasActionInProgress.update { false }
        }
    }

    private fun deleteFeed(feedId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                hasActionInProgress.update { true }
                feedsRepo.deleteById(feedId)
            }.onFailure { e -> error.update { e } }

            hasActionInProgress.update { false }
        }
    }

    private fun SelectAllWithUnreadEntryCount.toItem(): FeedsAdapter.Item {
        val selfLink = links.firstOrNull { it.rel is AtomLinkRel.Self }?.href
            ?: links.firstOrNull()?.href
            ?: "https://example.com".toHttpUrl()

        return FeedsAdapter.Item(
            id = id,
            title = title,
            selfLink = selfLink,
            alternateLink = links.firstOrNull { it.rel is AtomLinkRel.Alternate }?.href,
            unreadCount = unreadEntries,
            confUseBuiltInBrowser = db.confQueries.select().useBuiltInBrowser,
        )
    }

    private fun FragmentFeedsBinding.setState(state: State) {
        animateVisibilityChanges(
            views = listOf(toolbar, list, progress, message, importOpml, fab),
            visibleViews = when (state) {
                is State.Loading -> listOf(toolbar, progress)
                is State.ShowingFeeds -> listOf(toolbar, list, fab)
                is State.ImportingFeeds -> listOf(toolbar, message)
                is State.ShowingError -> listOf(toolbar)
            },
        )

        when (state) {
            is State.Loading -> {}

            is State.ShowingFeeds -> {
                listAdapter.submitList(state.feeds)

                if (state.feeds.isEmpty()) {
                    message.showSmooth()
                    message.text = getString(R.string.you_have_no_feeds)
                    importOpml.showSmooth()
                }
            }

            is State.ImportingFeeds -> {
                message.text = getString(
                    R.string.importing_feeds_n_of_n,
                    state.progress.imported,
                    state.progress.total,
                )
            }

            is State.ShowingError -> {
                showErrorDialog(state.error) { onErrorAcknowledged() }
            }
        }
    }

    private fun handleAddFeedIntent() {
        val url = requireArguments().getString("url", "")

        if (url.isNotBlank()) {
            addFeed(url)
            requireArguments().clear()
        }
    }

    private fun onAddClick(dialogInterface: DialogInterface) {
        val url =
            (dialogInterface as AlertDialog).findViewById<TextInputEditText>(R.id.url)?.text.toString()
        addFeed(url)
    }

    private fun onRenameClick(feedId: String, dialogInterface: DialogInterface) {
        val title = (dialogInterface as AlertDialog).findViewById<TextInputEditText>(R.id.title)!!
        renameFeed(feedId, title.text.toString())
    }

    private fun createFeedsAdapter(): FeedsAdapter {
        return FeedsAdapter(callback = object : FeedsAdapter.Callback {
            override fun onClick(item: FeedsAdapter.Item) {
                parentFragmentManager.commit {
                    replace(
                        R.id.fragmentContainerView,
                        EntriesFragment::class.java,
                        bundleOf("filter" to EntriesFilter.BelongToFeed(feedId = item.id)),
                    )
                    addToBackStack(null)
                }
            }

            override fun onSettingsClick(item: FeedsAdapter.Item) {
                parentFragmentManager.commit {
                    replace(
                        R.id.fragmentContainerView,
                        FeedSettingsFragment::class.java,
                        bundleOf("feedId" to item.id),
                    )
                    addToBackStack(null)
                }
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
                val dialog =
                    MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.rename))
                        .setView(R.layout.dialog_rename_feed)
                        .setPositiveButton(R.string.rename) { dialogInterface, _ ->
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
                deleteFeed(item.id)
            }
        })
    }

    private fun createImportFeedsLauncher(): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }

            importOpml(requireContext().contentResolver.openInputStream(uri)!!)
        }
    }

    private fun createExportFeedsLauncher(): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
            if (uri != null) {
                exportOpml(requireContext().contentResolver.openOutputStream(uri)!!)
            }
        }
    }

    class ListItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

        private val gapInPixels = context.resources.getDimensionPixelSize(R.dimen.dp_8)

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
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

    sealed class State {
        object Loading : State()
        data class ShowingFeeds(val feeds: List<FeedsAdapter.Item>) : State()
        data class ImportingFeeds(val progress: ImportState) : State()
        data class ShowingError(val error: Throwable) : State()
    }

    data class ImportState(
        val imported: Int,
        val total: Int,
    )
}
