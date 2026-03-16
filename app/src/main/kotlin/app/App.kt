package app

import android.app.Application
import android.content.Context
import co.appreactor.news.R
import db.db
import di.Di
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class App : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        initAcra {
            reportFormat = StringFormat.JSON

            mailSender {
                mailTo = getString(R.string.crash_report_email)
            }

            dialog {
                resTheme = com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight
                resIcon = null
                title = getString(R.string.crash_title)
                text = getString(R.string.crash_summary)
                positiveButtonText = getString(R.string.share)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        Di.init(this)
    }
}