package hnentries

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import anim.animateVisibilityChanges
import anim.showSmooth
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentHnEntriesBinding
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import conf.ConfRepo
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel

class HnEntriesFragment : Fragment(), OnItemReselectedListener {

    private val model: HnEntriesModel by viewModel()

    private var _binding: FragmentHnEntriesBinding? = null
    private val binding get() = _binding!!

    private val seenEntries = mutableSetOf<HnEntriesAdapter.Item>()

    private val adapter by lazy {
        HnEntriesAdapter(requireActivity()) { onListItemClick(it) }
            .apply { scrollToTopOnInsert() }
    }

    private val touchHelper: ItemTouchHelper? by lazy { createTouchHelper() }

    private val trackingListener = createTrackingListener()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        _binding = FragmentHnEntriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSwipeRefresh()
        initList()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { binding.setState(it) }
            }
        }
    }
    override fun onStop() {
        super.onStop()

        val state = model.state.value

        if (state is HnEntriesModel.State.ShowingCachedEntries && state.conf.mark_scrolled_entries_as_read) {
            seenEntries.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        scrollToTop()
    }

    private fun initSwipeRefresh() {
        binding.swipeRefresh.apply {
            setOnRefreshListener { model.onPullRefresh() }
        }
    }

    private fun initList() {
        if (binding.list.adapter == null) {
            binding.list.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = this@HnEntriesFragment.adapter
            }

            touchHelper?.attachToRecyclerView(binding.list)
        }
    }

    private fun FragmentHnEntriesBinding.setState(state: HnEntriesModel.State) {
        animateVisibilityChanges(
            views = listOf(toolbar, progress, message, retry, swipeRefresh),
            visibleViews = when (state) {
                is HnEntriesModel.State.InitialSync -> listOf(toolbar, progress)
                is HnEntriesModel.State.FailedToSync -> listOf(toolbar, retry)
                is HnEntriesModel.State.LoadingCachedEntries -> listOf(toolbar, progress)
                is HnEntriesModel.State.ShowingCachedEntries -> listOf(toolbar, swipeRefresh)
            },
        )

        updateToolbar(state)

        when (state) {
            is HnEntriesModel.State.InitialSync -> {
                var msg = "Downloading comments"
                msg += "\n Getting ${state.current} of ${state.total} "

                message.showSmooth()
                message.text = msg
            }

            is HnEntriesModel.State.FailedToSync -> {
                message.showSmooth()
                message.text =  state.cause
                retry.setOnClickListener { model.onRetry() }
            }

            HnEntriesModel.State.LoadingCachedEntries -> {}

            is HnEntriesModel.State.ShowingCachedEntries -> {

                if (state.entries.isEmpty()) {
                    message.showSmooth()
                    message.text = "EMPTY"
                }

                seenEntries.clear()
                adapter.submitList(state.entries) { if (state.scrollToTop) scrollToTop() }

                binding.list.removeOnScrollListener(trackingListener)
            }
        }
    }

    private fun updateToolbar(state: HnEntriesModel.State) {
        binding.toolbar.apply {
            binding.toolbar.apply {
                navigationIcon = DrawerArrowDrawable(context).also { it.progress = 1f }
                setNavigationOnClickListener { findNavController().popBackStack() }
            }

            if (state is HnEntriesModel.State.ShowingCachedEntries) {
                title = state.entries[0].title
            }

            updateSettingsButton()
        }
    }

    private fun updateSettingsButton() {
        binding.toolbar.menu!!.findItem(R.id.settings).setOnMenuItemClickListener {
            findNavController().navigate(HnEntriesFragmentDirections.actionHnEntriesFragmentToSettingsFragment())
            true
        }
    }

    private fun scrollToTop() {
        binding.list.layoutManager?.scrollToPosition(0)
    }

    private fun onListItemClick(item: HnEntriesAdapter.Item) {
        if (item.kidsCount > 0) {
            get<ConfRepo>().update { it.copy(current_hn_id = item.id) }
            val action = HnEntriesFragmentDirections.actionHnEntriesFragmentSelf(item.id)
            findNavController().navigate(action)
        }else{
            Toast.makeText(activity, "Item has no kids.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createTouchHelper(): ItemTouchHelper? {
        return ItemTouchHelper(object : HnSwipeHelper(
            requireContext(),
            R.drawable.ic_baseline_visibility_24,
            R.drawable.ic_baseline_bookmark_add_24,
        ) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                //TODO: here show user
                val entry = adapter.currentList[viewHolder.bindingAdapterPosition]
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                    }

                    ItemTouchHelper.RIGHT -> {
                    }
                }
            }
        })
    }


    private fun createTrackingListener(): OnScrollListener {
        return object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {

            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                    return
                }

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager

                if (layoutManager.findFirstVisibleItemPosition() == RecyclerView.NO_POSITION) {
                    return
                }

                if (layoutManager.findLastVisibleItemPosition() == RecyclerView.NO_POSITION) {
                    return
                }

                val visibleEntries =
                    (layoutManager.findFirstVisibleItemPosition()..layoutManager.findLastVisibleItemPosition()).map {
                        adapter.currentList[it]
                    }

                seenEntries.addAll(visibleEntries)
            }
        }
    }

    private fun HnEntriesAdapter.scrollToTopOnInsert() {
        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (_binding == null) {
                    return
                }

                if (positionStart == 0) {
                    (binding.list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                        0,
                        0,
                    )
                }
            }
        })
    }
}
