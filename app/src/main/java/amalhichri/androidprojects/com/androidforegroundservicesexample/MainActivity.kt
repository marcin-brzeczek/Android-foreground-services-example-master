package amalhichri.androidprojects.com.androidforegroundservicesexample

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)


        findViewById<View>(R.id.startBtn).setOnClickListener(View.OnClickListener { v ->
            val lState = MyForegroundService.state
            if (lState == Statics.STATE_SERVICE.NOT_INIT) {
                if (!NetworkHelper.isInternetAvailable(v.context)) {
                    showError(v)
                    return@OnClickListener
                }
                val startIntent = Intent(v.context, MyForegroundService::class.java)
                startIntent.action = Statics.ACTION.START_ACTION
                startService(startIntent)
            } else if (lState == Statics.STATE_SERVICE.PREPARE || lState == Statics.STATE_SERVICE.PLAY) {
                val lPauseIntent = Intent(v.context, MyForegroundService::class.java)
                lPauseIntent.action = Statics.ACTION.PAUSE_ACTION
                val lPendingPauseIntent = PendingIntent.getService(v.context, 0, lPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                try {
                    lPendingPauseIntent.send()
                } catch (e: PendingIntent.CanceledException) {
                    e.printStackTrace()
                }
            } else if (lState == Statics.STATE_SERVICE.PAUSE) {
                if (!NetworkHelper.isInternetAvailable(v.context)) {
                    showError(v)
                    return@OnClickListener
                }
                val lPauseIntent = Intent(v.context, MyForegroundService::class.java)
                lPauseIntent.action = Statics.ACTION.PLAY_ACTION
                val lPendingPauseIntent = PendingIntent.getService(v.context, 0, lPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                try {
                    lPendingPauseIntent.send()
                } catch (e: PendingIntent.CanceledException) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun showError(v: View) {
        Toast.makeText(applicationContext, "No internet access!",
            Toast.LENGTH_SHORT).show()
    }
}