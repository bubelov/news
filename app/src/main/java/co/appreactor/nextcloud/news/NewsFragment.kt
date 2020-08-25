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
import kotlinx.android.synthetic.main.fragment_news.*
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class NewsFragment : Fragment() {

    private val model: NewsFragmentModel by viewModel()

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
            R.layout.fragment_news,
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
            showData()
        } catch (e: SSOException) {
            UiExceptionManager.showDialogForException(context, e)
        }
    }

    private suspend fun showData() {
        if (itemsAdapter.itemCount != 0) {
            itemsView.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(context)
                adapter = itemsAdapter
            }

            return
        }

        progress.isVisible = true

        val newsAndFeeds = model.getNewsAndFeeds()

        val onItemClick: (NewsItem) -> Unit = {
            lifecycleScope.launch {
                //model.markAsRead(it)

                //val intent = Intent(Intent.ACTION_VIEW)
                //intent.data = Uri.parse(it.url)
                //startActivity(intent)

                //itemsAdapter.updateItem(it.copy(unread = false))

                val action =
                    NewsFragmentDirections.actionNewsFragmentToNewsItemFragment(it.id)
                findNavController().navigate(action)
            }
        }

        itemsAdapter.onClick = onItemClick

        itemsAdapter.swapItems(newsAndFeeds.first, newsAndFeeds.second)

        itemsView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = itemsAdapter
        }

        progress.isVisible = false
    }
}