package crash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import co.appreactor.news.R
import co.appreactor.news.databinding.ActivityCrashBinding
import kotlin.system.exitProcess

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityCrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.share) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_TEXT, binding.stackTrace.text.toString())
                    type = "text/plain"
                }

                startActivity(intent)
                true
            } else {
                false
            }
        }

        binding.stackTrace.text = intent.getStringExtra(Intent.EXTRA_TEXT)
    }

    override fun onBackPressed() {
        exitProcess(1)
    }
}