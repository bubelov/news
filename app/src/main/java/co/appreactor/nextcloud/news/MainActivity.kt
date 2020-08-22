package co.appreactor.nextcloud.news

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }

            redactHeader("Authorization")
            redactHeader("Cookie")
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(httpLoggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://cloud.bubelov.com/index.php/apps/news/api/v1-2/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val api = retrofit.create(NewsApi::class.java)

        lifecycleScope.launch {
            whenCreated {
                progress.isVisible = true

                val emailAndPasswordInBase64 = Base64.encodeToString(
                    "xxx:xxx".toByteArray(),
                    Base64.NO_WRAP
                )

                val feedsResponse = api.getFeeds(
                    authorization = "Basic $emailAndPasswordInBase64"
                )

                val itemsResponse = api.getUnreadItems(
                    authorization = "Basic $emailAndPasswordInBase64"
                )

                val itemsAdapter = ItemsAdapter(
                    itemsResponse.items,
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