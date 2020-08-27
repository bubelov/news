package co.appreactor.nextcloud.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import co.appreactor.nextcloud.news.db.NewsItem
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.ui.UiExceptionManager
import kotlinx.android.synthetic.main.fragment_starred_news.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class StarredNewsFragment : Fragment() {

    private val model: StarredNewsFragmentModel by viewModel()

    private val itemsAdapter = ItemsAdapter(
        items = mutableListOf(),
        feeds = mutableListOf()
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_starred_news,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            whenResumed {
                showAuthOrShowData()
            }
        }
    }

    private suspend fun showAuthOrShowData() {
        try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(context)
            model.sync()
            showData()
        } catch (e: SSOException) {
            UiExceptionManager.showDialogForException(context, e)
        }
    }

    private suspend fun showData() {
        progress.isVisible = true

        itemsView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = itemsAdapter
        }

        model.getNewsAndFeeds().collect { data ->
            if (data.first.isNotEmpty()) {
                progress.isVisible = false
            }

            val onItemClick: (NewsItem) -> Unit = {
                lifecycleScope.launch {
                    val action =
                        StarredNewsFragmentDirections.actionStarredNewsFragmentToNewsItemFragment(it.id)
                    findNavController().navigate(action)
                }
            }

            itemsAdapter.onClick = onItemClick

            itemsAdapter.swapItems(data.first.map { it.copy(unread = true) }, data.second)
        }
    }
}