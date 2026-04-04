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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.vestifeed.parser.AtomLinkRel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.vestifeed.R
import org.vestifeed.anim.animateVisibilityChanges
import org.vestifeed.anim.showSmooth
import org.vestifeed.app.api
import org.vestifeed.app.db
import org.vestifeed.databinding.FragmentFeedsBinding
import org.vestifeed.db.table.Feed
import org.vestifeed.db.table.FeedQueries
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entries.EntriesFilter
import org.vestifeed.entries.EntriesFragment
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
    sealed class State {
        object Loading : State()
        data class ShowingFeeds(val feeds: List<FeedsAdapter.Item>) : State()
        data class ImportingFeeds(val imported: Int, val total: Int) : State()
    }

    private val state = MutableStateFlow<State>(State.Loading)

    private var _binding: FragmentFeedsBinding? = null
    private val binding get() = _binding!!

    private val importFeedsLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }

            viewLifecycleOwner.lifecycleScope.launch {
                importOpml(requireContext().contentResolver.openInputStream(uri)!!)
            }
        }

    private val exportFeedsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
            if (uri != null) {
                exportOpml(requireContext().contentResolver.openOutputStream(uri)!!)
            }
        }

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

        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.importFeeds -> importFeedsLauncher.launch("*/*")
                R.id.exportFeeds -> exportFeedsLauncher.launch("feeds.opml")
            }

            true
        }

        binding.list.apply {
            setHasFixedSize(true)
            adapter = createFeedsAdapter()
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(ListItemDecoration(requireContext()))

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

        binding.importOpml.setOnClickListener { importFeedsLauncher.launch("*/*") }

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

        viewLifecycleOwner.lifecycleScope.launch {
            state.update { State.Loading }
            val feeds = withContext(Dispatchers.IO) {
                db().feed.selectAll()
            }
            state.update { State.ShowingFeeds(feeds.map { it.toItem() }) }
        }

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

    private suspend fun importOpml(document: InputStream) {
        val outlines = try {
            withContext(Dispatchers.IO) {
                DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(document)
                    .toOpml()
                    .leafOutlines()
            }
        } catch (e: Throwable) {
            showErrorDialog(e)
            return
        }

        state.update { State.ImportingFeeds(0, outlines.size) }

        var feedsImported = 0
        var feedsExisted = 0
        var feedsFailed = 0
        val errors = mutableListOf<String>()

        val existingLinks = db().feed.selectAllLinks().flatten()

        for (outline in outlines) {
            val outlineUrl = (outline.xmlUrl ?: "").toHttpUrlOrNull()

            if (outlineUrl == null) {
                errors += "Invalid URL: ${outline.xmlUrl}"
                feedsFailed++
                continue
            }

            val feedAlreadyExists = existingLinks.any {
                it.href.toUri().normalize() == outlineUrl.toUri().normalize()
            }

            if (feedAlreadyExists) {
                feedsExisted++
            } else {
                try {
                    val feedWithEntries = api().addFeed(outlineUrl).getOrThrow()
                    db().feed.insertOrReplace(listOf(feedWithEntries.first))
                    feedsImported++
                } catch (e: Throwable) {
                    errors += "Failed to import feed ${outline.xmlUrl}\nReason: ${e.message}"
                    feedsFailed++
                }

                state.update {
                    State.ImportingFeeds(
                        imported = feedsImported + feedsExisted + feedsFailed,
                        total = outlines.size,
                    )
                }
            }
        }

        if (errors.isNotEmpty()) {
            val message = buildString {
                errors.forEach {
                    append(it)

                    if (errors.last() != it) {
                        append("\n\n")
                    }
                }
            }

            showErrorDialog(message)
        } else {
            val feeds = db().feed.selectAll()
            state.update { State.ShowingFeeds(feeds.map { it.toItem() }) }
        }
    }

    private fun exportOpml(out: OutputStream) {
        viewLifecycleOwner.lifecycleScope.launch {
            val feeds = db().feed.selectAll()

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

    private fun addFeed(unvalidatedUrl: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val hasHttpPrefix =
                    unvalidatedUrl.startsWith("http") or unvalidatedUrl.startsWith("https")

                val entries = if (hasHttpPrefix) {
                    val feedWithEntries = api().addFeed(unvalidatedUrl.toHttpUrl()).getOrThrow()
                    db().feed.insertOrReplace(listOf(feedWithEntries.first))
                    feedWithEntries.second
                } else {
                    val feedWithEntries =
                        api().addFeed("https://$unvalidatedUrl".toHttpUrl()).getOrThrow()
                    db().feed.insertOrReplace(listOf(feedWithEntries.first))
                    feedWithEntries.second
                }

                db().transaction {
                    db().entry.insertOrReplace(entries)
                }
            }.onSuccess {
                state.update { State.Loading }
                val feeds = withContext(Dispatchers.IO) {
                    db().feed.selectAll()
                }
                state.update { State.ShowingFeeds(feeds.map { it.toItem() }) }
            }.onFailure { e ->
                showErrorDialog(e)
            }
        }
    }

    private fun renameFeed(feedId: String, newTitle: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val feed = db().feed.selectById(feedId)
                    ?: throw Exception("Cannot find feed $feedId in cache")
                val trimmedNewTitle = newTitle.trim()
                api().updateFeedTitle(feedId, trimmedNewTitle)
                db().feed.insertOrReplace(listOf(feed.copy(title = trimmedNewTitle)))
            }.onFailure { e -> showErrorDialog(e) }
        }
    }

    private fun deleteFeed(feedId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                api().deleteFeed(feedId)

                db().transaction {
                    db().feed.deleteById(feedId)
                    db().entry.deleteByFeedId(feedId)
                }
            }.onFailure { e -> showErrorDialog(e) }
        }
    }

    private fun Feed.toItem(): FeedsAdapter.Item {
        val selfLink = links.firstOrNull { it.rel is AtomLinkRel.Self }?.href
            ?: links.firstOrNull()?.href
            ?: "https://example.com".toHttpUrl()

        return FeedsAdapter.Item(
            id = id,
            title = title,
            selfLink = selfLink,
            alternateLink = links.firstOrNull { it.rel is AtomLinkRel.Alternate }?.href,
            unreadCount = db().entry.selectByFeedId(id).filterNot { it.extRead }.size.toLong(),
            confUseBuiltInBrowser = db().conf.select().useBuiltInBrowser,
        )
    }

    private fun FragmentFeedsBinding.setState(state: State) {
        animateVisibilityChanges(
            views = listOf(toolbar, list, progress, message, importOpml, fab),
            visibleViews = when (state) {
                is State.Loading -> listOf(toolbar, progress)
                is State.ShowingFeeds -> listOf(toolbar, list, fab)
                is State.ImportingFeeds -> listOf(toolbar, message)
            },
        )

        when (state) {
            is State.Loading -> {}

            is State.ShowingFeeds -> {
                (binding.list.adapter as? FeedsAdapter)?.submitList(state.feeds)

                if (state.feeds.isEmpty()) {
                    message.showSmooth()
                    message.text = getString(R.string.you_have_no_feeds)
                    importOpml.showSmooth()
                }
            }

            is State.ImportingFeeds -> {
                message.text = getString(
                    R.string.importing_feeds_n_of_n,
                    state.imported,
                    state.total,
                )
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
}
