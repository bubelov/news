package co.appreactor.nextcloud.news

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.api.NextcloudAPI.ApiConnectedListener
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.model.SingleSignOnAccount
import com.nextcloud.android.sso.ui.UiExceptionManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.NextcloudRetrofitApiBuilder


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        showAuthOrShowData()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val onAccessGranted = AccountImporter.IAccountAccessGranted { account ->
            SingleAccountHelper.setCurrentAccount(applicationContext, account.name)
            showData(account)
        }

        when(resultCode) {
            RESULT_CANCELED -> {
                login.isVisible = true
            }

            else -> {
                AccountImporter.onActivityResult(
                    requestCode,
                    resultCode,
                    data,
                    this,
                    onAccessGranted
                )
            }
        }
    }

    private fun showAccountPicker() {
        try {
            AccountImporter.pickNewAccount(this)
        } catch (e: SSOException) {
            UiExceptionManager.showDialogForException(this, e)
        }
    }

    private fun showAuthOrShowData() {
        val ssoAccount: SingleSignOnAccount

        try {
            ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(applicationContext)
            showData(ssoAccount)
        } catch (e: SSOException) {
            login.isVisible = true

            login.setOnClickListener {
                it.isVisible = false
                showAccountPicker()
            }
        }
    }

    private fun showData(account: SingleSignOnAccount) {
        val callback: ApiConnectedListener = object : ApiConnectedListener {
            override fun onConnected() {}

            override fun onError(e: Exception) {
                // TODO
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("callback.onError: ${e.message}")
                    .show()
            }
        }

        val nextcloudApi = NextcloudAPI(
            applicationContext,
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

                val itemsAdapter = ItemsAdapter(
                    unreadItemsResponse.items + starredItemsResponse.items,
                    feedsResponse.feeds,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(it.url)
                        startActivity(intent)
                    }
                )

                itemsView.apply {
                    setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(this@MainActivity)
                    adapter = itemsAdapter
                }

                progress.isVisible = false
            }
        }
    }
}