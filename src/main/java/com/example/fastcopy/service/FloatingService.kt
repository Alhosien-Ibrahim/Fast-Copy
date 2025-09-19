package com.example.fastcopy.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.fastcopy.R
import com.example.fastcopy.storage.DataStoreManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import android.util.Log

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var textView: TextView
    private lateinit var store: DataStoreManager

    private var dropdownMenu: View? = null
    private var dropdownContainer: LinearLayout? = null
    private var previousButton: ImageButton? = null
    private var closeFloatingButton: ImageButton? = null
    private var hideMenuButton: ImageButton? = null

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private var isDropdownShown = false
    private var dropdownParams: WindowManager.LayoutParams? = null
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false

    override fun onCreate() {
        super.onCreate()
        try {
            store = DataStoreManager(this)
            createNotificationChannel()
            startForeground(1, createNotification())
            showFloatingButton()
            startTextUpdater()

            // استعادة الفهرس المحفوظ عند بدء الخدمة
            scope.launch {
                val savedIndex = store.currentIndex.first()
                val lines = store.savedLines.first()
                if (savedIndex in 0 until lines.size) {
                    textView.text = (savedIndex + 1).toString()
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Error in onCreate", e)
        }
    }

    private fun showFloatingButton() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button_layout, null)
            textView = floatingView.findViewById(R.id.floatingText)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START
            params.x = 100
            params.y = 300

            windowManager.addView(floatingView, params)

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isMoving = false

            floatingView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        isLongPressTriggered = false

                        // بدء تتبع الضغطة المطولة
                        longPressRunnable = Runnable {
                            if (!isMoving) {
                                isLongPressTriggered = true
                                performLongPressAction(params.x, params.y + floatingView.height)
                            }
                        }
                        longPressHandler.postDelayed(longPressRunnable!!, 500)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = Math.abs(event.rawX - initialTouchX)
                        val deltaY = Math.abs(event.rawY - initialTouchY)

                        if (deltaX > 10 || deltaY > 10) {
                            isMoving = true
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }

                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(floatingView, params)

                            // تحديث موقع القائمة إذا كانت مفتوحة
                            if (isDropdownShown) {
                                updateDropdownPosition(params.x, params.y + floatingView.height)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }

                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        // تنفيذ النقرة العادية فقط إذا لم يتم تشغيل الضغطة المطولة
                        if (!isMoving && !isLongPressTriggered && Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
                            performClickAction()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        isLongPressTriggered = false
                        true
                    }
                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Error in showFloatingButton", e)
        }
    }

    private fun createDropdownMenu() {
        try {
            Log.d("FloatingService", "Creating dropdown menu")
            dropdownMenu = LayoutInflater.from(this).inflate(R.layout.floating_dropdown_menu, null)

            if (dropdownMenu == null) {
                Log.e("FloatingService", "Failed to inflate dropdown menu")
                return
            }

            dropdownContainer = dropdownMenu?.findViewById(R.id.dropdownContainer)
            previousButton = dropdownMenu?.findViewById(R.id.previousButton)
            closeFloatingButton = dropdownMenu?.findViewById(R.id.closeFloatingButton)
            hideMenuButton = dropdownMenu?.findViewById(R.id.hideMenuButton)

            Log.d("FloatingService", "Dropdown elements: container=$dropdownContainer, prev=$previousButton, close=$closeFloatingButton, hide=$hideMenuButton")

            // زر الرجوع للخلف
            previousButton?.setOnClickListener {
                scope.launch {
                    try {
                        val lines = store.savedLines.first()
                        var index = store.currentIndex.first()

                        if (index > 0 && lines.isNotEmpty()) {
                            // الرجوع للسطر السابق
                            index--
                            store.saveIndex(index)

                            // نسخ السطر الجديد
                            val text = lines[index]
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Copied Text", text)
                            clipboard.setPrimaryClip(clip)

                            Toast.makeText(this@FloatingService, "↩️ تم نسخ السطر ${index + 1}", Toast.LENGTH_SHORT).show()

                            // تحديث الرقم في الزر
                            textView.text = (index + 1).toString()
                        } else {
                            Toast.makeText(this@FloatingService, "⚠️ أنت في بداية القائمة", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("FloatingService", "Error in previousButton click", e)
                    }
                }
            }

            // زر إغلاق الزر العائم
            closeFloatingButton?.setOnClickListener {
                hideDropdownMenu()
                scope.launch {
                    store.saveFloatingEnabled(false)
                }
                stopSelf()
            }

            // زر إخفاء القائمة
            hideMenuButton?.setOnClickListener {
                hideDropdownMenu()
            }

            // منع إغلاق القائمة عند الضغط على الخلفية
            dropdownMenu?.setOnClickListener {
                // لا نفعل شيء - نمنع إغلاق القائمة
            }

            Log.d("FloatingService", "Dropdown menu created successfully")
        } catch (e: Exception) {
            Log.e("FloatingService", "Error in createDropdownMenu", e)
        }
    }

    private fun performLongPressAction(x: Int, y: Int) {
        try {
            if (!isDropdownShown) {
                // إنشاء القائمة إذا لم تكن موجودة
                if (dropdownMenu == null) {
                    createDropdownMenu()
                }

                // اهتزاز خفيف عند فتح القائمة (مع التحقق من الإذن)
                try {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(50)
                    }
                } catch (e: SecurityException) {
                    // تجاهل إذا لم يكن لدينا إذن الاهتزاز
                    Log.w("FloatingService", "No vibration permission")
                }

                showDropdownMenu(x, y)
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Error in performLongPressAction", e)
            Toast.makeText(this, "خطأ في عرض القائمة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDropdownMenu(x: Int, y: Int) {
        try {
            if (dropdownMenu == null) {
                Log.e("FloatingService", "dropdownMenu is null, cannot show")
                return
            }

            if (dropdownParams == null) {
                dropdownParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            }

            dropdownParams!!.gravity = Gravity.TOP or Gravity.START
            dropdownParams!!.x = x
            dropdownParams!!.y = y + 10 // مسافة صغيرة من الزر

            dropdownMenu!!.visibility = View.INVISIBLE
            windowManager.addView(dropdownMenu, dropdownParams)

            // أنيميشن الظهور
            dropdownMenu!!.post {
                dropdownContainer?.let { container ->
                    container.scaleY = 0f
                    container.alpha = 0f
                    dropdownMenu!!.visibility = View.VISIBLE

                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.duration = 300
                    animator.interpolator = AccelerateDecelerateInterpolator()
                    animator.addUpdateListener { animation ->
                        val value = animation.animatedValue as Float
                        container.scaleY = value
                        container.alpha = value
                    }
                    animator.start()
                } ?: run {
                    Log.e("FloatingService", "dropdownContainer is null")
                    dropdownMenu!!.visibility = View.VISIBLE
                }
            }

            isDropdownShown = true
            Log.d("FloatingService", "Dropdown menu shown successfully")
        } catch (e: Exception) {
            Log.e("FloatingService", "Error in showDropdownMenu", e)
            isDropdownShown = false
            Toast.makeText(this, "فشل عرض القائمة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideDropdownMenu() {
        try {
            if (!isDropdownShown || dropdownMenu == null || dropdownContainer == null) return

            val animator = ValueAnimator.ofFloat(1f, 0f)
            animator.duration = 200
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                dropdownContainer?.scaleY = value
                dropdownContainer?.alpha = value
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        dropdownMenu?.let {
                            windowManager.removeView(it)
                        }
                        isDropdownShown = false
                    } catch (e: Exception) {
                        Log.e("FloatingService", "Error removing dropdown", e)
                    }
                }
            })
            animator.start()
        } catch (e: Exception) {
            Log.e("FloatingService", "Error in hideDropdownMenu", e)
        }
    }

    private fun updateDropdownPosition(x: Int, y: Int) {
        try {
            dropdownParams?.let {
                it.x = x
                it.y = y + 10
                if (dropdownMenu != null && isDropdownShown) {
                    windowManager.updateViewLayout(dropdownMenu, it)
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Error in updateDropdownPosition", e)
        }
    }

    private fun performClickAction() {
        scope.launch {
            try {
                val lines = store.savedLines.first()
                val index = store.currentIndex.first()

                if (index < lines.size) {
                    // نسخ السطر الحالي (الذي يظهر رقمه في الزر)
                    val text = lines[index]

                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Copied Text", text)
                    clipboard.setPrimaryClip(clip)

                    Toast.makeText(this@FloatingService, "📋 تم نسخ السطر ${index + 1}", Toast.LENGTH_SHORT).show()

                    // الانتقال للسطر التالي
                    val nextIndex = (index + 1).coerceAtMost(lines.size - 1)
                    if (nextIndex != index) {
                        store.saveIndex(nextIndex)
                        textView.text = if (nextIndex >= lines.size - 1 && index == lines.size - 1) "تم" else (nextIndex + 1).toString()
                    } else {
                        // إذا كنا في آخر سطر
                        textView.text = "تم"
                        Toast.makeText(this@FloatingService, "✅ تم الانتهاء من جميع السطور", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("FloatingService", "Error in performClickAction", e)
            }
        }
    }

    private fun startTextUpdater() {
        scope.launch {
            while (isActive) {
                try {
                    val lines = store.savedLines.first()
                    val index = store.currentIndex.first()
                    if (index >= lines.size) {
                        textView.text = "تم"
                    } else {
                        textView.text = (index + 1).toString()
                    }
                } catch (e: Exception) {
                    Log.e("FloatingService", "Error in text updater", e)
                }
                delay(500)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::floatingView.isInitialized) {
                windowManager.removeView(floatingView)
            }
            if (isDropdownShown && dropdownMenu != null) {
                windowManager.removeView(dropdownMenu)
            }
            job.cancel()
        } catch (e: Exception) {
            Log.e("FloatingService", "Error in onDestroy", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "floating_service_channel",
                "Floating Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "floating_service_channel")
            .setContentTitle("Fast Copy يعمل")
            .setContentText("زر النسخ السريع مفعل")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}