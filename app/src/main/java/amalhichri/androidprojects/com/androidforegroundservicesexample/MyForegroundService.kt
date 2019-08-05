package amalhichri.androidprojects.com.androidforegroundservicesexample

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.view.View
import android.widget.RemoteViews

class MyForegroundService : Service(), MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnBufferingUpdateListener {
    private val mUriRadioDefault = Uri.parse("https://nfw.ria.ru/flv/audio.aspx?ID=75651129&type=mp3")
    private val mLock = Any()
    private val mHandler = Handler()
    private var mPlayer: MediaPlayer? = null
    private var mUriRadio: Uri? = null
    private var mNotificationManager: NotificationManager? = null
    private var mWiFiLock: WifiManager.WifiLock? = null
    private var mWakeLock: PowerManager.WakeLock? = null
    private val mTimerUpdateHandler = Handler()
    private val mTimerUpdateRunnable = object : Runnable {

        override fun run() {
            mNotificationManager!!.notify(Statics.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification())
            mTimerUpdateHandler.postDelayed(this, Statics.DELAY_UPDATE_NOTIFICATION_FOREGROUND_SERVICE)
        }
    }
    private val mDelayedShutdown = Runnable {
        unlockWiFi()
        unlockCPU()
        stopForeground(true)
        stopSelf()
    }

    override fun onBind(arg0: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(MyForegroundService::class.java!!.getSimpleName(), "onCreate()")
        state = Statics.STATE_SERVICE.NOT_INIT
        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mUriRadio = mUriRadioDefault
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent == null) {
            stopForeground(true)
            stopSelf()
            return Service.START_NOT_STICKY
        }


        when (intent.action) {
            Statics.ACTION.START_ACTION -> {
                Log.i(TAG, "Received start Intent ")
                state = Statics.STATE_SERVICE.PREPARE
                startForeground(Statics.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification())
                destroyPlayer()
                initPlayer()
                play()
            }

            Statics.ACTION.PAUSE_ACTION -> {
                state = Statics.STATE_SERVICE.PAUSE
                mNotificationManager!!.notify(Statics.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification())
                Log.i(TAG, "Clicked Pause")
                destroyPlayer()
                mHandler.postDelayed(mDelayedShutdown, Statics.DELAY_SHUTDOWN_FOREGROUND_SERVICE)
            }

            Statics.ACTION.PLAY_ACTION -> {
                state = Statics.STATE_SERVICE.PREPARE
                mNotificationManager!!.notify(Statics.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification())
                Log.i(TAG, "Clicked Play")
                destroyPlayer()
                initPlayer()
                play()
            }

            Statics.ACTION.STOP_ACTION -> {
                Log.i(TAG, "Received Stop Intent")
                destroyPlayer()
                stopForeground(true)
                stopSelf()
            }

            else -> {
                stopForeground(true)
                stopSelf()
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        destroyPlayer()
        state = Statics.STATE_SERVICE.NOT_INIT
        try {
            mTimerUpdateHandler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        super.onDestroy()
    }

    private fun destroyPlayer() {
        if (mPlayer != null) {
            try {
                mPlayer!!.reset()
                mPlayer!!.release()
                Log.d(TAG, "Player destroyed")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mPlayer = null
            }
        }
        unlockWiFi()
        unlockCPU()
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        Log.d(TAG, "Player onError() what:$what")
        destroyPlayer()
        mHandler.postDelayed(mDelayedShutdown, Statics.DELAY_SHUTDOWN_FOREGROUND_SERVICE)
        mNotificationManager!!.notify(Statics.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification())
        state = Statics.STATE_SERVICE.PAUSE
        return false
    }

    private fun initPlayer() {
        mPlayer = MediaPlayer()
        mPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
        mPlayer!!.setOnErrorListener(this)
        mPlayer!!.setOnPreparedListener(this)
        mPlayer!!.setOnBufferingUpdateListener(this)
        mPlayer!!.setOnInfoListener { mp, what, extra ->
            Log.d(TAG, "Player onInfo(), what:$what, extra:$extra")
            false
        }
        lockWiFi()
        lockCPU()
    }

    private fun play() {
        try {
            mHandler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        synchronized(mLock) {
            try {
                if (mPlayer == null) {
                    initPlayer()
                }
                mPlayer!!.reset()
                mPlayer!!.setVolume(1.0f, 1.0f)
                mPlayer!!.setDataSource(this, mUriRadio!!)
                mPlayer!!.prepareAsync()
            } catch (e: Exception) {
                destroyPlayer()
                e.printStackTrace()
            }

        }
    }

    private fun prepareNotification(): Notification {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && mNotificationManager!!.getNotificationChannel(FOREGROUND_CHANNEL_ID) == null) {
            // The user-visible name of the channel.
            val name = getString(R.string.text_value_radio_notification)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(FOREGROUND_CHANNEL_ID, name, importance)
            mChannel.enableVibration(false)
            mNotificationManager!!.createNotificationChannel(mChannel)
        }
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.action = Statics.ACTION.MAIN_ACTION
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        } else {
            notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val lPauseIntent = Intent(this, MyForegroundService::class.java)
        lPauseIntent.action = Statics.ACTION.PAUSE_ACTION
        val lPendingPauseIntent = PendingIntent.getService(this, 0, lPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val playIntent = Intent(this, MyForegroundService::class.java)
        playIntent.action = Statics.ACTION.PLAY_ACTION
        val lPendingPlayIntent = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val lStopIntent = Intent(this, MyForegroundService::class.java)
        lStopIntent.action = Statics.ACTION.STOP_ACTION
        val lPendingStopIntent = PendingIntent.getService(this, 0, lStopIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val lRemoteViews = RemoteViews(packageName, R.layout.radio_notification)
        lRemoteViews.setOnClickPendingIntent(R.id.ui_notification_close_button, lPendingStopIntent)

        when (state) {

            Statics.STATE_SERVICE.PAUSE -> {
                lRemoteViews.setViewVisibility(R.id.ui_notification_progress_bar, View.INVISIBLE)
                lRemoteViews.setOnClickPendingIntent(R.id.ui_notification_player_button, lPendingPlayIntent)
                lRemoteViews.setImageViewResource(R.id.ui_notification_player_button, R.drawable.ic_play_arrow_white)
            }

            Statics.STATE_SERVICE.PLAY -> {
                lRemoteViews.setViewVisibility(R.id.ui_notification_progress_bar, View.INVISIBLE)
                lRemoteViews.setOnClickPendingIntent(R.id.ui_notification_player_button, lPendingPauseIntent)
                lRemoteViews.setImageViewResource(R.id.ui_notification_player_button, R.drawable.ic_pause_white)
            }

            Statics.STATE_SERVICE.PREPARE -> {
                lRemoteViews.setViewVisibility(R.id.ui_notification_progress_bar, View.VISIBLE)
                lRemoteViews.setOnClickPendingIntent(R.id.ui_notification_player_button, lPendingPauseIntent)
                lRemoteViews.setImageViewResource(R.id.ui_notification_player_button, R.drawable.ic_pause_white)
            }
        }

        val lNotificationBuilder: NotificationCompat.Builder
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            lNotificationBuilder = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
        } else {
            lNotificationBuilder = NotificationCompat.Builder(this)
        }
        lNotificationBuilder
            .setContent(lRemoteViews)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            lNotificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC)
        }
        return lNotificationBuilder.build()
    }

    override fun onPrepared(mp: MediaPlayer) {
        Log.d(TAG, "Player onPrepared()")
        state = Statics.STATE_SERVICE.PLAY
        mNotificationManager!!.notify(Statics.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification())
        try {
            mPlayer!!.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mPlayer!!.start()
        mTimerUpdateHandler.postDelayed(mTimerUpdateRunnable, 0)
    }

    override fun onBufferingUpdate(mp: MediaPlayer, percent: Int) {
        Log.d(TAG, "Player onBufferingUpdate():$percent")
    }

    private fun lockCPU() {
        val mgr = getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.javaClass.getSimpleName())
        mWakeLock!!.acquire()
        Log.d(TAG, "Player lockCPU()")
    }

    private fun unlockCPU() {
        if (mWakeLock != null && mWakeLock!!.isHeld) {
            mWakeLock!!.release()
            mWakeLock = null
            Log.d(TAG, "Player unlockCPU()")
        }
    }

    private fun lockWiFi() {
        val connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        if (lWifi != null && lWifi.isConnected) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB_MR1) {
                mWiFiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF, MyForegroundService::class.java!!.getSimpleName())
            } else {
                mWiFiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(
                    WifiManager.WIFI_MODE_FULL, MyForegroundService::class.java!!.getSimpleName())
            }
            mWiFiLock!!.acquire()
            Log.d(TAG, "Player lockWiFi()")
        }
    }

    private fun unlockWiFi() {
        if (mWiFiLock != null && mWiFiLock!!.isHeld) {
            mWiFiLock!!.release()
            mWiFiLock = null
            Log.d(TAG, "Player unlockWiFi()")
        }
    }

    companion object {

        private val FOREGROUND_CHANNEL_ID = "foreground_channel_id"
        private val TAG = MyForegroundService::class.java!!.getSimpleName()
        var state = Statics.STATE_SERVICE.NOT_INIT
            private set
    }
}
