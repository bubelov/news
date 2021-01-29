package feeds

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import common.showDialog
import common.showKeyboard
import co.appreactor.news.databinding.FragmentFeedsBinding
import db.Feed
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import entries.EntriesFilter
import kotlinx.coroutines.flow.collect
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class FeedsFragment : Fragment() {

    private val model: FeedsFragmentModel by viewModel()

    private var _binding: FragmentFeedsBinding? = null
    private val binding get() = _binding!!

    private val adapter = FeedsAdapter(callback = object : FeedsAdapterCallback {
        override fun onFeedClick(feed: Feed) {
            findNavController().navigate(
                FeedsFragmentDirections.actionFeedsFragmentToFeedEntriesFragment(
                    EntriesFilter.OnlyFromFeed(feedId = feed.id)
                )
            )
        }

        override fun onOpenHtmlLinkClick(feed: Feed) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(feed.alternateLink)
            startActivity(intent)
        }

        override fun openLinkClick(feed: Feed) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(feed.selfLink)
            startActivity(intent)
        }

        override fun onRenameClick(feed: Feed) {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.rename))
                .setView(R.layout.dialog_rename_feed)
                .setPositiveButton(R.string.rename) { dialogInterface, _ ->
                    val dialog = dialogInterface as AlertDialog

                    lifecycleScope.launchWhenResumed {
                        binding.actionProgress.isVisible = true
                        binding.fab.isVisible = false

                        runCatching {
                            val titleView = dialog.findViewById<TextInputEditText>(R.id.titleView)!!
                            model.renameFeed(feed.id, titleView.text.toString())
                        }.onFailure {
                            Timber.e(it)
                            showDialog(R.string.error, it.message ?: "")
                        }

                        binding.actionProgress.isVisible = false
                        binding.fab.isVisible = true
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener { hideKeyboard() }
                .show()

            val titleView = dialog.findViewById<TextInputEditText>(R.id.titleView)!!
            titleView.append(feed.title)
            titleView.requestFocus()

            requireContext().showKeyboard()
        }

        override fun onDeleteClick(feed: Feed) {
            lifecycleScope.launchWhenResumed {
                binding.actionProgress.isVisible = true
                binding.fab.isVisible = false

                runCatching {
                    model.deleteFeed(feed.id)
                }.onFailure {
                    Timber.e(it)
                    showDialog(R.string.error, it.message ?: "")
                }

                binding.actionProgress.isVisible = false
                binding.fab.isVisible = true
            }
        }
    })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.listView.setHasFixedSize(true)
        binding.listView.layoutManager = LinearLayoutManager(requireContext())
        binding.listView.adapter = adapter
        binding.listView.addItemDecoration(FeedsAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))

        binding.listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                binding.fab.isVisible = binding.listView.canScrollVertically(1)
            }
        })

        lifecycleScope.launchWhenResumed {
            binding.listViewProgress.isVisible = true

            model.getFeeds().collect { feeds ->
                binding.listViewProgress.isVisible = false
                binding.empty.isVisible = feeds.isEmpty()
                adapter.submitList(feeds)
            }
        }

        binding.fab.setOnClickListener {
            val alert = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.add_feed))
                .setView(R.layout.dialog_add_feed)
                .setPositiveButton(R.string.add) { dialogInterface, _ ->
                    val dialog = dialogInterface as AlertDialog

                    lifecycleScope.launchWhenResumed {
                        binding.actionProgress.isVisible = true
                        binding.fab.isVisible = false

                        runCatching {
                            val urlView = dialog.findViewById<TextInputEditText>(R.id.urlView)!!
                            model.createFeed(urlView.text.toString())
                        }.onFailure {
                            Timber.e(it)
                            showDialog(R.string.error, it.message ?: "")
                        }

                        binding.actionProgress.isVisible = false
                        binding.fab.isVisible = true
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener { hideKeyboard() }
                .show()

            alert.findViewById<View>(R.id.urlLayout)!!.requestFocus()
            requireContext().showKeyboard()
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