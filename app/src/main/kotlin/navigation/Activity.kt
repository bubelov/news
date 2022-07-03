package navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.ActivityBinding
import entries.EntriesFilter

class Activity : AppCompatActivity() {

    lateinit var binding: ActivityBinding

    private val navController by lazy { findNavController(R.id.nav_host_fragment) }

    private val navListener = NavController.OnDestinationChangedListener { _, destination, args ->
        when (destination.id) {
            R.id.newsFragment -> args!!.putParcelable("filter", EntriesFilter.NotBookmarked)
            R.id.bookmarksFragment -> args!!.putParcelable("filter", EntriesFilter.Bookmarked)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        navController.addOnDestinationChangedListener(navListener)
    }
}