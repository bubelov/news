package entry

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.core.view.iterator
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEntryBinding
import common.AppFragment
import common.hide
import common.openLink
import common.show
import common.showErrorDialog
import db.Entry
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EntryFragment : AppFragment() {

    private val args: EntryFragmentArgs by navArgs()

    private val model: EntryViewModel by viewModel()

    private var _binding: FragmentEntryBinding? = null
    private val binding get() = _binding!!

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

        toolbar?.apply {
            setupUpNavigation()
            inflateMenu(R.menu.menu_entry)
        }

        binding.apply {
            viewLifecycleOwner.lifecycleScope.launch {
                model.onViewCreated(
                    entryId = args.entryId,
                    summaryView = binding.summaryView,
                    lifecycleScope = lifecycleScope,
                )

                model.state.collectLatest { setState(it ?: return@collectLatest) }
            }

            scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
                if (scrollView.canScrollVertically(1)) {
                    fab.show()
                } else {
                    fab.hide()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setState(state: EntryViewModel.State) {
        binding.apply {
            val menu = toolbar?.menu

            when (state) {
                EntryViewModel.State.Progress -> {
                    menu?.iterator()?.forEach { it.isVisible = false }
                    contentContainer.hide()
                    progress.show(animate = true)
                    fab.hide()
                }

                is EntryViewModel.State.Success -> {
                    menu?.findItem(R.id.toggleBookmarked)?.isVisible = true
                    menu?.findItem(R.id.comments)?.apply {
                        isVisible = state.entry.commentsUrl.isNotBlank()
                        setOnMenuItemClickListener {
                            val link = runCatching {
                                Uri.parse(state.entry.commentsUrl)
                            }.getOrElse {
                                showErrorDialog(it)
                                return@setOnMenuItemClickListener true
                            }

                            openLink(link, model.conf.useBuiltInBrowser)
                            true
                        }
                    }
                    menu?.findItem(R.id.feedSettings)?.isVisible = true
                    menu?.findItem(R.id.share)?.isVisible = true

                    contentContainer.show(animate = true)
                    toolbar?.title = state.feedTitle

                    toolbar?.setOnMenuItemClickListener {
                        onMenuItemClick(
                            menuItem = it,
                            entry = state.entry
                        )
                    }

                    updateBookmarkedButton(state.entry.bookmarked)
                    title.text = state.entry.title
                    val format =
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    date.text = format.format(state.entry.published)
                    state.parsedContent.applyStyle(summaryView)
                    summaryView.text = state.parsedContent
                    summaryView.movementMethod = LinkMovementMethod.getInstance()
                    progress.hide()

                    if (state.entry.link.isEmpty()) {
                        fab.hide()
                    } else {
                        fab.show()

                        fab.setOnClickListener {
                            val link = kotlin.runCatching {
                                Uri.parse(state.entry.link)
                            }.getOrElse {
                                showErrorDialog(it)
                                return@setOnClickListener
                            }

                            openLink(link, model.conf.useBuiltInBrowser)
                        }
                    }
                }

                is EntryViewModel.State.Error -> {
                    menu?.iterator()?.forEach { it.isVisible = false }
                    contentContainer.hide()
                    showErrorDialog(state.message) { findNavController().popBackStack() }
                }
            }
        }
    }

    private fun onMenuItemClick(menuItem: MenuItem?, entry: Entry): Boolean {
        when (menuItem?.itemId) {
            R.id.toggleBookmarked -> {
                lifecycleScope.launchWhenResumed {
                    model.setBookmarked(
                        entry.id,
                        !entry.bookmarked,
                    )
                }

                return true
            }

            R.id.feedSettings -> {
                findNavController().navigate(
                    EntryFragmentDirections.actionEntryFragmentToFeedSettingsFragment(
                        feedId = entry.feedId,
                    )
                )

                return true
            }

            R.id.share -> {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, entry.title)
                    putExtra(Intent.EXTRA_TEXT, entry.link)
                }

                startActivity(Intent.createChooser(intent, ""))
                return true
            }
        }

        return false
    }

    private fun updateBookmarkedButton(bookmarked: Boolean) {
        toolbar?.menu?.findItem(R.id.toggleBookmarked)?.apply {
            if (bookmarked) {
                setIcon(R.drawable.ic_baseline_bookmark_24)
                setTitle(R.string.remove_bookmark)
            } else {
                setIcon(R.drawable.ic_baseline_bookmark_border_24)
                setTitle(R.string.bookmark)
            }
        }
    }

    private fun SpannableStringBuilder.applyStyle(textView: TextView) {
        val spans = getSpans(0, length - 1, Any::class.java)

        spans.forEach {
            when (it) {
                is BulletSpan -> {
                    val radius = resources.getDimension(R.dimen.bullet_radius).toInt()
                    val gap = resources.getDimension(R.dimen.bullet_gap).toInt()

                    val span = if (Build.VERSION.SDK_INT >= 28) {
                        BulletSpan(gap, textView.currentTextColor, radius)
                    } else {
                        BulletSpan(gap, textView.currentTextColor)
                    }

                    setSpan(
                        span,
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

                    val span = if (Build.VERSION.SDK_INT >= 28) {
                        QuoteSpan(color, stripe, gap)
                    } else {
                        QuoteSpan(color)
                    }

                    setSpan(
                        span,
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
}