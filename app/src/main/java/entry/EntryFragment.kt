package entry

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.*
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEntryBinding
import common.hide
import common.show
import common.showErrorDialog
import db.Entry
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class EntryFragment : Fragment() {

    private val args: EntryFragmentArgs by navArgs()

    private val model: EntryViewModel by viewModel()

    private var _binding: FragmentEntryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.apply {
            toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

            viewLifecycleOwner.lifecycleScope.launch {
                model.onViewCreated(
                    entryId = args.entryId,
                    imageGetter = TextViewImageGetter(binding.summaryView, lifecycleScope),
                )

                model.state.collect { setState(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setState(state: EntryViewModel.State?) {
        binding.apply {
            val menuItemBookmark = toolbar.menu.findItem(R.id.toggleBookmarked)
            val menuItemFeedSettings = toolbar.menu.findItem(R.id.feedSettings)
            val menuItemShare = toolbar.menu.findItem(R.id.share)

            when (state) {
                EntryViewModel.State.Progress -> {
                    menuItemBookmark.isVisible = false
                    menuItemFeedSettings.isVisible = false
                    menuItemShare.isVisible = false
                    contentContainer.hide()
                    progress.show(animate = true)
                    fab.hide()
                }

                is EntryViewModel.State.Success -> {
                    menuItemBookmark.isVisible = true
                    menuItemFeedSettings.isVisible = true
                    menuItemShare.isVisible = true
                    contentContainer.show(animate = true)
                    toolbar.title = state.feedTitle

                    toolbar.setOnMenuItemClickListener {
                        onMenuItemClick(
                            menuItem = it,
                            entry = state.entry
                        )
                    }

                    updateBookmarkedButton(state.entry.bookmarked)
                    title.text = state.entry.title
                    date.text = state.entry.published
                    state.parsedContent.applyStyle(summaryView)
                    summaryView.text = state.parsedContent
                    summaryView.movementMethod = LinkMovementMethod.getInstance()
                    progress.hide()
                    fab.show()

                    fab.setOnClickListener {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(state.entry.link)
                            )
                        )
                    }
                }

                is EntryViewModel.State.Error -> {
                    menuItemBookmark.isVisible = false
                    menuItemFeedSettings.isVisible = false
                    menuItemShare.isVisible = false
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
        binding.toolbar.menu.findItem(R.id.toggleBookmarked).apply {
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