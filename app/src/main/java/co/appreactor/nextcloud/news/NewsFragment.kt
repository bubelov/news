package co.appreactor.nextcloud.news

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.lifecycle.whenResumed
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.model.SingleSignOnAccount
import kotlinx.android.synthetic.main.fragment_news.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.NextcloudRetrofitApiBuilder

class NewsFragment : Fragment() {

    private val itemsAdapter = ItemsAdapter(
        items = mutableListOf(),
        feeds = mutableListOf()
    )

    init {
        lifecycleScope.launch {
            whenResumed {
                showAuthOrShowData()
            }
        }
    }

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

    private fun showAuthOrShowData() {
        val ssoAccount: SingleSignOnAccount

        try {
            ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(context)
            showData(ssoAccount)
        } catch (e: SSOException) {
            Toast.makeText(context, "Unauthorized!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showData(account: SingleSignOnAccount) {
        val callback: NextcloudAPI.ApiConnectedListener = object :
            NextcloudAPI.ApiConnectedListener {
            override fun onConnected() {}

            override fun onError(e: Exception) {
                // TODO
                AlertDialog.Builder(requireContext())
                    .setMessage("callback.onError: ${e.message}")
                    .show()
            }
        }

        val nextcloudApi = NextcloudAPI(
            requireContext(),
            account,
            GsonBuilder().create(),
            callback
        )

        val api = NextcloudRetrofitApiBuilder(
            nextcloudApi,
            "/index.php/apps/news/api/v1-2/"
        ).create(NewsApi::class.java)

        lifecycleScope.launch {
            whenCreated {
                progress.isVisible = true

                val feedsResponse = withContext(Dispatchers.IO) {
                    api.getFeeds()
                }

                val unreadItemsResponse = withContext(Dispatchers.IO) {
                    api.getUnreadItems()
                }

                val starredItemsResponse = withContext(Dispatchers.IO) {
                    api.getStarredItems()
                }

                val onItemClick: (Item) -> Unit = {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            api.markAsRead(MarkAsReadArgs(listOf(it.id)))
                        }

                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(it.url)
                        startActivity(intent)

                        itemsAdapter.updateItem(it.copy(unread = false))
                    }
                }

                val allItems = mutableListOf<Item>()
                allItems += unreadItemsResponse.items
                allItems += starredItemsResponse.items

                itemsAdapter.onClick = onItemClick

                itemsAdapter.swapItems(allItems, feedsResponse.feeds.toMutableList())

                itemsView.apply {
                    setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(context)
                    adapter = itemsAdapter
                }

                progress.isVisible = false
            }
        }
    }
}