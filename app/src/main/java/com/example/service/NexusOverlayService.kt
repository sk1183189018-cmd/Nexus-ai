package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.example.R

class NexusOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingBubble: ImageView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (floatingBubble != null) return START_STICKY

        // Create a floating neon orb view using a system overlay parameter
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 150
            y = 250
        }

        val bubble = ImageView(this).apply {
            // Use Android launcher or drawable resource
            setImageResource(android.R.drawable.presence_online)
            alpha = 0.95f
            contentDescription = "Nexus Overlay Bubble"
            minimumWidth = 140
            minimumHeight = 140
        }

        // Floating drag logic
        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var lastX = 0
            private var lastY = 0
            private var firstX = 0
            private var firstY = 0
            private var isDrag = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX.toInt()
                        lastY = event.rawY.toInt()
                        firstX = lastX
                        firstY = lastY
                        isDrag = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX.toInt() - lastX
                        val deltaY = event.rawY.toInt() - lastY
                        if (Math.abs(event.rawX - firstX) > 10 || Math.abs(event.rawY - firstY) > 10) {
                            isDrag = true
                        }
                        params.x += deltaX
                        params.y += deltaY
                        windowManager.updateViewLayout(bubble, params)
                        lastX = event.rawX.toInt()
                        lastY = event.rawY.toInt()
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDrag) {
                            // Clicked: open main assistant app
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingBubble = bubble
        try {
            windowManager.addView(floatingBubble, params)
            isOverlayShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingBubble != null) {
            windowManager.removeView(floatingBubble)
            floatingBubble = null
        }
        isOverlayShowing = false
    }

    companion object {
        var isOverlayShowing = false
            private set
    }
}
