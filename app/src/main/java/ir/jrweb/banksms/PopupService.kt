package ir.jrweb.banksms

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class PopupService : Service() {

    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val closeRunnable = Runnable {
        removePopup()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra("TYPE") ?: return START_NOT_STICKY
        val amount = intent.getStringExtra("AMOUNT") ?: return START_NOT_STICKY
        val sender = intent.getStringExtra("SENDER") ?: "بانک"

        playCashSound(type)
        showTopPopup(type, amount, sender)
        return START_NOT_STICKY
    }

    private fun playCashSound(type: String) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            if (type == "DEPOSIT") {
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 140)
                handler.postDelayed({
                    toneGen.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 280)
                    handler.postDelayed({ toneGen.release() }, 350)
                }, 160)
            } else {
                toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 250)
                handler.postDelayed({ toneGen.release() }, 300)
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showTopPopup(type: String, amount: String, sender: String) {
        removePopup()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        popupView = inflater.inflate(R.layout.popup_overlay, null)

        val container = popupView!!.findViewById<LinearLayout>(R.id.popupContainer)
        val tvTitle = popupView!!.findViewById<TextView>(R.id.tvPopupTitle)
        val tvAmount = popupView!!.findViewById<TextView>(R.id.tvPopupAmount)
        val tvSender = popupView!!.findViewById<TextView>(R.id.tvPopupSender)
        val tvClose = popupView!!.findViewById<TextView>(R.id.tvPopupClose)

        val bgDrawable = GradientDrawable().apply {
            cornerRadius = 36f
            if (type == "DEPOSIT") {
                setColor(Color.parseColor("#1B8A3D"))
                tvTitle.text = "واریز به حساب"
            } else {
                setColor(Color.parseColor("#E65100"))
                tvTitle.text = "برداشت از حساب"
            }
        }
        container.background = bgDrawable

        tvAmount.text = amount
        tvSender.text = "فرستنده: $sender  |  بکشید تا بسته شود  |  JRweb.ir"

        tvClose.setOnClickListener {
            removePopup()
            stopSelf()
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 40
        }

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        container.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    params.x = initialX + dx
                    if (dy < 0) {
                        params.y = initialY + dy
                    }
                    view.alpha = (1f - (abs(dx) / 600f)).coerceIn(0.25f, 1f)
                    try {
                        windowManager?.updateViewLayout(popupView, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (abs(dx) > 180 || dy < -90) {
                        removePopup()
                        stopSelf()
                    } else {
                        params.x = 0
                        params.y = 40
                        view.alpha = 1f
                        try {
                            windowManager?.updateViewLayout(popupView, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(popupView, params)
        handler.postDelayed(closeRunnable, 60_000L)
    }

    private fun removePopup() {
        handler.removeCallbacks(closeRunnable)
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
            popupView = null
        }
    }

    override fun onDestroy() {
        removePopup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}