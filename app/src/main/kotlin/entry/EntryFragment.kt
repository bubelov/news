package entry

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.text.style.QuoteSpan
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.feedk.AtomLinkRel
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEntryBinding
import conf.ConfRepo
import db.Entry
import db.Link
import di.Di
import dialog.showErrorDialog
import enclosures.EnclosuresAdapter
import enclosures.EnclosuresRepo
import entries.EntriesRepo
import feeds.FeedsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import navigation.EntryFragmentArgs
import navigation.NavDirections
import navigation.findNavController
import navigation.navArgs
import navigation.openUrl
import sync.Sync
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EntryFragment : Fragment() {

    private val args: EntryFragmentArgs by navArgs()

    private val app: Application by lazy { Di.get(Application::class.java) }
    private val enclosuresRepo: EnclosuresRepo by lazy { Di.get(EnclosuresRepo::class.java) }
    private val entriesRepository: EntriesRepo by lazy { Di.get(EntriesRepo::class.java) }
    private val feedsRepository: FeedsRepo by lazy { Di.get(FeedsRepo::class.java) }
    private val newsApiSync: Sync by lazy { Di.get(Sync::class.java) }
    private val confRepo: ConfRepo by lazy { Di.get(ConfRepo::class.java) }

    private val conf = confRepo.conf

    private var _binding: FragmentEntryBinding? = null
    private val binding get() = _binding!!

    private val enclosuresAdapter = createEnclosuresAdapter()

    private val _state = MutableStateFlow<State>(State.Progress)
    private val state = _state.asStateFlow()

    private val entryArgs = MutableStateFlow<EntryArgs?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            android.util.Log.d("EntryFragment", "Toolbar back button clicked!")
            findNavController().popBackStack()
        }
        
        // Also add click listener to toolbar navigation icon directly
        binding.toolbar.navigationIcon?.let { icon ->
            android.util.Log.d("EntryFragment", "Navigation icon is set: $icon")
        }

        binding.enclosures.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = enclosuresAdapter
            addItemDecoration(CardListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_16)))
        }

        entryArgs.update {
            EntryArgs(
                entryId = args.entryId,
                summaryView = binding.summaryView,
                lifecycleScope = lifecycleScope,
            )
        }

        lifecycleScope.launch { enclosuresRepo.deletePartialDownloads() }

        combine(entryArgs, entriesRepository.selectCount()) { args, _ ->
            if (args == null) {
                _state.update { State.Progress }
                return@combine
            }

            runCatching {
                val entry = entriesRepository.selectById(args.entryId).first()

                if (entry == null) {
                    val message = getString(R.string.cannot_find_entry_with_id_s, args.entryId)
                    _state.update { State.Error(message) }
                    return@combine
                }

                val feed = feedsRepository.selectById(entry.feedId).first()

                if (feed == null) {
                    val message = getString(R.string.cannot_find_feed_with_id_s, entry.feedId)
                    _state.update { State.Error(message) }
                    return@combine
                }

                _state.update {
                    State.Success(
                        feedTitle = feed.title,
                        entry = entry,
                        entryLinks = entry.links,
                        parsedContent = parseEntryContent(
                            entry.contentText ?: "",
                            TextViewImageGetter(
                                textView = args.summaryView,
                                scope = args.lifecycleScope,
                                baseUrl = null,
                            ),
                        ),
                    )
                }
            }.onFailure { throwable ->
                _state.update { State.Error(throwable.message ?: "") }
            }
        }.launchIn(lifecycleScope)

        state
            .onEach { setState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        binding.apply {
            scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
                if (scrollView.canScrollVertically(1)) {
                    fab.show()
                } else {
                    fab.hide()
                }
            }

            ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).let {
                    v.updatePadding(top = it.top)
                }
                insets
            }

            ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).let {
                    v.updatePadding(bottom = it.bottom)
                }
                insets
            }

            ViewCompat.setOnApplyWindowInsetsListener(fab) { v, insets ->
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).let {
                    v.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                        bottomMargin = it.bottom + resources.getDimensionPixelSize(R.dimen.dp_16)
                    }
                }
                insets
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setState(state: State) {
        binding.apply {
            val menu = binding.toolbar.menu!!

            when (state) {
                State.Progress -> {
                    menu.iterator().forEach { it.isVisible = false }
                    contentContainer.isVisible = false
                    progress.isVisible = true
                    fab.hide()
                }

                is State.Success -> {
                    menu.findItem(R.id.toggleBookmarked)?.isVisible = true
                    menu.findItem(R.id.comments)?.apply {
                        isVisible = state.entry.extCommentsUrl.isNotBlank()
                        setOnMenuItemClickListener {
                            openUrl(
                                state.entry.extCommentsUrl,
                                conf.value.useBuiltInBrowser
                            )
                            true
                        }
                    }
                    menu.findItem(R.id.feedSettings)?.isVisible = true
                    menu.findItem(R.id.share)?.isVisible = true

                    contentContainer.isVisible = true
                    binding.toolbar.title = state.feedTitle

                    binding.toolbar.setOnMenuItemClickListener {
                        onMenuItemClick(
                            menuItem = it,
                            entry = state.entry,
                            entryLinks = state.entryLinks,
                        )
                    }

                    updateBookmarkedButton(state.entry.extBookmarked)
                    title.text = state.entry.title
                    val format =
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                    date.text = format.format(state.entry.published)
                    state.parsedContent.applyStyle(summaryView)
                    summaryView.text = state.parsedContent
                    summaryView.movementMethod = LinkMovementMethod.getInstance()
                    progress.isVisible = false

                    enclosuresAdapter.submitList(state.entryLinks
                        .filter { it.rel is AtomLinkRel.Enclosure }
                        .filter { it.type?.startsWith("audio") ?: false }
                        .mapIndexed { index, enclosure ->
                            EnclosuresAdapter.Item(
                                entryId = state.entry.id,
                                enclosure = enclosure,
                                primaryText = getString(R.string.audio_n, index + 1),
                                secondaryText = enclosure.href.toString()
                            )
                        })

                    val firstHtmlLink =
                        state.entryLinks.firstOrNull { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }

                    if (firstHtmlLink == null) {
                        fab.hide()
                    } else {
                        fab.show()
                        fab.setOnClickListener {
                            openUrl(
                                firstHtmlLink.href.toString(),
                                conf.value.useBuiltInBrowser
                            )
                        }
                    }
                }

                is State.Error -> {
                    menu.iterator().forEach { it.isVisible = false }
                    contentContainer.isVisible = false
                    showErrorDialog(state.message) { findNavController().popBackStack() }
                }
            }
        }
    }

    private fun onMenuItemClick(
        menuItem: MenuItem?,
        entry: Entry,
        entryLinks: List<Link>,
    ): Boolean {
        when (menuItem?.itemId) {
            R.id.toggleBookmarked -> {
                lifecycleScope.launchWhenResumed {
                    setBookmarked(
                        entry.id,
                        !entry.extBookmarked,
                    )
                }

                return true
            }

            R.id.feedSettings -> {
                val args = NavDirections.EntryFragment.actionEntryFragmentToFeedSettingsFragment(feedId = entry.feedId)
                findNavController().navigate(R.id.feedSettingsFragment, args)

                return true
            }

            R.id.share -> {
                val firstAlternateLink =
                    entryLinks.firstOrNull { it.rel is AtomLinkRel.Alternate } ?: return true

                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, entry.title)
                    putExtra(Intent.EXTRA_TEXT, firstAlternateLink.href.toString())
                }

                startActivity(Intent.createChooser(intent, ""))
                return true
            }
        }

        return false
    }

    private fun updateBookmarkedButton(bookmarked: Boolean) {
        binding.toolbar.menu?.findItem(R.id.toggleBookmarked)?.apply {
            if (bookmarked) {
                setIcon(R.drawable.ic_baseline_bookmark_24)
                setTitle(R.string.remove_bookmark)
            } else {
                setIcon(R.drawable.ic_baseline_bookmark_border_24)
                setTitle(R.string.bookmark)
            }
        }
    }

    fun downloadAudioEnclosure(enclosure: Link) {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                runCatching { enclosuresRepo.downloadAudioEnclosure(enclosure) }
                    .onFailure { showErrorDialog(it) }
            }
        }
    }

    fun playAudioEnclosure(enclosure: Link) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(enclosure.extCacheUri!!.toUri(), enclosure.type)

        runCatching {
            startActivity(intent)
        }.onFailure {
            if (it is ActivityNotFoundException) {
                showErrorDialog(getString(R.string.you_have_no_apps_which_can_play_this_podcast))
            } else {
                showErrorDialog(it)
            }
        }
    }

    private fun deleteEnclosure(enclosure: Link) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { enclosuresRepo.deleteFromCache(enclosure) }
                .onFailure { showErrorDialog(it) }
        }
    }

    private fun SpannableStringBuilder.applyStyle(textView: TextView) {
        val spans = getSpans(0, length - 1, Any::class.java)

        spans.forEach {
            when (it) {
                is BulletSpan -> {
                    val radius = resources.getDimension(R.dimen.bullet_radius).toInt()
                    val gap = resources.getDimension(R.dimen.bullet_gap).toInt()

                    setSpan(
                        BulletSpan(gap, textView.currentTextColor, radius),
                        getSpanStart(it),
                        getSpanEnd(it),
                        0
                    )

                    removeSpan(it)
                }

                is QuoteSpan -> {
                    val color = binding.date.currentTextColor
                    val stripe = resources.getDimension(R.dimen.quote_stripe_width).toInt()
                    val gap = resources.getDimension(R.dimen.quote_gap).toInt()

                    setSpan(
                        QuoteSpan(color, stripe, gap),
                        getSpanStart(it),
                        getSpanEnd(it),
                        0
                    )

                    removeSpan(it)
                }

                is URLSpan -> {
                    if (it.url.startsWith("#")) {
                        removeSpan(it)
                    }
                }
            }
        }
    }

    private fun setBookmarked(
        entryId: String,
        bookmarked: Boolean,
    ) {
        lifecycleScope.launch {
            entriesRepository.updateBookmarkedAndBookmaredSynced(
                id = entryId,
                bookmarked = bookmarked,
                bookmarkedSynced = false,
            )

            newsApiSync.run(
                Sync.Args(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    private fun parseEntryContent(
        content: String,
        imageGetter: Html.ImageGetter,
    ): SpannableStringBuilder {
        val summary = HtmlCompat.fromHtml(
            content,
            HtmlCompat.FROM_HTML_MODE_LEGACY,
            imageGetter,
            null,
        ) as SpannableStringBuilder

        if (summary.isBlank()) {
            return summary
        }

        while (summary.contains("\n\n\n")) {
            val index = summary.indexOf("\n\n\n")
            summary.delete(index, index + 1)
        }

        while (summary.startsWith("\n\n")) {
            summary.delete(0, 1)
        }

        while (summary.endsWith("\n\n")) {
            summary.delete(summary.length - 2, summary.length - 1)
        }

        return summary
    }

    private fun createEnclosuresAdapter(): EnclosuresAdapter {
        return EnclosuresAdapter(object : EnclosuresAdapter.Callback {
            override fun onDownloadClick(item: EnclosuresAdapter.Item) {
                downloadAudioEnclosure(item.enclosure)
            }

            override fun onPlayClick(item: EnclosuresAdapter.Item) {
                playAudioEnclosure(item.enclosure)
            }

            override fun onDeleteClick(item: EnclosuresAdapter.Item) {
                deleteEnclosure(item.enclosure)
            }
        })
    }

    private class CardListAdapterDecoration(private val gapInPixels: Int) :
        RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val position = parent.getChildAdapterPosition(view)

            val bottomGap = if (position == (parent.adapter?.itemCount ?: 0) - 1) {
                gapInPixels
            } else {
                0
            }

            outRect.set(gapInPixels, gapInPixels, gapInPixels, bottomGap)
        }
    }

    private data class EntryArgs(
        val entryId: String,
        val summaryView: TextView,
        val lifecycleScope: LifecycleCoroutineScope,
    )

    private sealed class State {
        object Progress : State()

        data class Success(
            val feedTitle: String,
            val entry: Entry,
            val entryLinks: List<Link>,
            val parsedContent: SpannableStringBuilder,
        ) : State()

        data class Error(val message: String) : State()
    }

    private class TextViewImageGetter(
        private val textView: TextView,
        private val scope: LifecycleCoroutineScope,
        private val baseUrl: String?,
    ) : Html.ImageGetter {
        override fun getDrawable(source: String): android.graphics.drawable.Drawable? {
            return null
        }
    }
}