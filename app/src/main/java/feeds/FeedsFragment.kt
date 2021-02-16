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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import common.Result
import entries.EntriesFilter
import kotlinx.coroutines.flow.collect
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class FeedsFragment : Fragment() {

    private val model: FeedsFragmentModel by viewModel()

    private var _binding: FragmentFeedsBinding? = null
    private val binding get() = _binding!!

    private val adapter =
        FeedsAdapter(scope = lifecycleScope, callback = object : FeedsAdapterCallback {
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.swipeRefresh.isEnabled = false

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
            model.onViewReady()
        }

        lifecycleScope.launchWhenResumed {
            model.items.collect { result ->
                binding.listViewProgress.isVisible = false

                when (result) {
                    Result.Progress -> {
                        binding.listViewProgress.isVisible = true

                        binding.listViewProgress.alpha = 0f
                        binding.listViewProgress.animate().alpha(1f).duration = 1000
                    }

                    is Result.Success -> {
                        binding.empty.isVisible = result.data.isEmpty()
                        adapter.submitList(result.data)
                    }

                    else -> {

                    }
                }
            }
        }

        binding.fab.setOnClickListener {
            val alert = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.add_feed))
                .setView(R.layout.dialog_add_feed)
                .setPositiveButton(R.string.add) { dialogInterface, _ ->
                    val dialog = dialogInterface as AlertDialog

                    lifecycleScope.launchWhenResumed {
                        binding.swipeRefresh.isRefreshing = true

                        runCatching {
                            val urlView = dialog.findViewById<TextInputEditText>(R.id.url)!!
                            model.createFeed(urlView.text.toString())
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