package co.appreactor.nextcloud.news.opengraph

import co.appreactor.nextcloud.news.news.NewsItemsRepository
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber

class OpenGraphImagesSync(
    private val newsItemsRepository: NewsItemsRepository
) {

    private val client = OkHttpClient()

    suspend fun start() = withContext(Dispatchers.IO) {
        newsItemsRepository.all().collect { news ->
            Timber.d("Got ${news.size} news")

            news.sortedByDescending { it.pubDate }.forEach { newsItem ->
                if (newsItem.openGraphImageUrl.isNotBlank()
                    || newsItem.openGraphImageParsingFailed
                    || newsItem.url.startsWith("http://")) {
                    return@forEach
                }

                Timber.d("Processing item ${newsItem.id} (${newsItem.title})")
                Timber.d("URL: ${newsItem.url}")

                runCatching {
                    val request = Request.Builder().url(newsItem.url).build()
                    val response = client.newCall(request).execute()
                    Timber.d("Response code: ${response.code}")

                    if (response.isSuccessful) {
                        val html = response.body!!.string()
                        val document = Jsoup.parse(html)

                        val metas = document.select("meta[property=\"og:image\"]")

                        if (metas.isNotEmpty()) {
                            val imageUrl = metas.first().attr("content")

                            if (imageUrl.isNotBlank()) {
                                Timber.d("Image URL: $imageUrl")
                                val image = Picasso.get().load(imageUrl).get()!!

                                Timber.d("Downloaded image. Resolution: ${image.width} x ${image.height}")

                                if (image.width > 480 && image.height.toDouble() > image.width.toDouble() / 2.5) {
                                    newsItemsRepository.updateOpenGraphImageUrl(
                                        id = newsItem.id,
                                        url = imageUrl
                                    )
                                } else {
                                    Timber.d("Invalid image. Size: ${image.width} x ${image.height}")

                                    newsItemsRepository.updateOpenGraphImageParsingFailed(
                                        id = newsItem.id,
                                        failed = true
                                    )
                                }
                            } else {
                                Timber.w("Open Graph tag seems to be corrupted")

                                newsItemsRepository.updateOpenGraphImageParsingFailed(
                                    id = newsItem.id,
                                    failed = true
                                )
                            }
                        } else {
                            Timber.d("This page has no Open Graph images")

                            newsItemsRepository.updateOpenGraphImageParsingFailed(
                                id = newsItem.id,
                                failed = true
                            )
                        }
                    }
                }.apply {
                    if (!isSuccess) {
                        newsItemsRepository.updateOpenGraphImageParsingFailed(
                            id = newsItem.id,
                            failed = true
                        )

                        Timber.e(exceptionOrNull())
                    }
                }
            }
        }
    }
}