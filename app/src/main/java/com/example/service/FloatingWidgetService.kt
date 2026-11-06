package com.example.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.model.ConnectionStatus
import com.example.model.ProxyState
import com.example.model.RoutingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingWidgetService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingRootView: FrameLayout? = null

    // Compact Bubble Views
    private lateinit var collapsedBubbleView: LinearLayout
    private lateinit var bubbleStatusDot: View
    private lateinit var bubbleTimerText: TextView
    private lateinit var bubblePlusBtn: FrameLayout
    private lateinit var bubblePlusText: TextView

    // Expanded HUD Views
    private lateinit var expandedHudCard: LinearLayout
    private lateinit var hudTitleText: TextView
    private lateinit var hudProxyDetailText: TextView
    private lateinit var hudTimerText: TextView
    private lateinit var hudStatusBadgeText: TextView
    private lateinit var hudErrorText: TextView
    private lateinit var hudAppsRoutingText: TextView
    private lateinit var hudAccountBalanceText: TextView
    private lateinit var hudTrafficStatsText: TextView
    private lateinit var hudActionPlusButton: LinearLayout
    private lateinit var hudPlusIconText: TextView
    private lateinit var hudPlusSubText: TextView
    private lateinit var hudCloseOverlayBtn: LinearLayout
    private lateinit var hudOpenAppBtn: LinearLayout
    private lateinit var hudCollapseBtn: ImageView

    private var layoutParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateCollectJob: Job? = null

    private var isExpanded = false
    private var screenWidth = 1080
    private var screenHeight = 1920

    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "floating_widget_channel"
        private const val TAG = "FloatingWidgetService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed startForeground in FloatingWidgetService", t)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show floating widget: Overlay permission not granted")
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        updateScreenDimensions()
        initViews()
        observeProxyState()
    }

    private fun updateScreenDimensions() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initViews() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot initialize views: Overlay permission missing")
            return
        }
        val wm = windowManager ?: return

        floatingRootView = FrameLayout(this)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = screenHeight / 3
        }

        // 1. COLLAPSED FLOATING BUBBLE (Compact pill with separate '+' action button)
        collapsedBubbleView = createCollapsedBubble()

        // 2. EXPANDED HUD CONTROLLER
        expandedHudCard = createExpandedHud()
        expandedHudCard.visibility = View.GONE

        floatingRootView?.addView(collapsedBubbleView)
        floatingRootView?.addView(expandedHudCard)

        setupDragAndInteractions(collapsedBubbleView)

        try {
            wm.addView(floatingRootView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floatingRootView to WindowManager", e)
        }
    }

    /**
     * Compact floating bubble:
     * - Left side: Status dot + live timer / mini text (tapping expands the HUD)
     * - Right side: Distinct circular '+' / '⏹' button (tapping toggles connection immediately without expanding!)
     */
    private fun createCollapsedBubble(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(5), dp(5))

            // Sleek dark pill with bright neon border
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#070B14"))
                setStroke(dp(1), Color.parseColor("#00FF66"))
            }
            background = bg
            elevation = dp(10).toFloat()

            // Status indicator dot
            bubbleStatusDot = View(context).apply {
                val dotBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#EF476F")) // Red disconnected default
                }
                background = dotBg
                val dotSize = dp(8)
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginEnd = dp(6)
                }
            }
            addView(bubbleStatusDot)

            // Mini timer text (e.g. 00:00:00 or DH PROXY)
            bubbleTimerText = TextView(context).apply {
                text = "DH PROXY"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, dp(8), 0)
            }
            addView(bubbleTimerText)

            // Dedicated '+' / '⏹' Action Button inside the compact bubble
            bubblePlusBtn = FrameLayout(context).apply {
                val btnBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#00FF66"))
                }
                background = btnBg
                val btnSize = dp(32)
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)

                bubblePlusText = TextView(context).apply {
                    text = "+"
                    setTextColor(Color.parseColor("#05080F"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                addView(bubblePlusText)
            }
            addView(bubblePlusBtn)
        }
    }

    private fun createExpandedHud(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)

            // Dark cyber card background with neon green accent border
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#090F1C"))
                setStroke(dp(1.5f.toInt()), Color.parseColor("#00FF66"))
            }
            background = bg
            elevation = dp(16).toFloat()

            val cardWidth = dp(290)
            layoutParams = FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT)

            // Top Header: Title + Collapse Minimize Button
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                val titleColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    hudTitleText = TextView(context).apply {
                        text = "DH PROXY • DH PREMIUM"
                        setTextColor(Color.parseColor("#00FF66"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    addView(hudTitleText)

                    val devText = TextView(context).apply {
                        text = "Developing by DHRUBO"
                        setTextColor(Color.parseColor("#FFD166"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    addView(devText)
                }
                addView(titleColumn)

                // Minimize icon (collapses back to compact bubble)
                hudCollapseBtn = ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    setColorFilter(Color.parseColor("#94A3B8"))
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    setOnClickListener {
                        collapseToBubble()
                    }
                }
                addView(hudCollapseBtn)
            }
            addView(headerRow)

            // Proxy detail text (Server & Port)
            hudProxyDetailText = TextView(context).apply {
                text = "127.0.0.1:1080 (SOCKS5)"
                setTextColor(Color.parseColor("#94A3B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, dp(4))
            }
            addView(hudProxyDetailText)

            // Routing indicator
            hudAppsRoutingText = TextView(context).apply {
                text = "Mode: All Apps"
                setTextColor(Color.parseColor("#38BDF8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, 0, 0, dp(8))
            }
            addView(hudAppsRoutingText)

            // Status Badge
            hudStatusBadgeText = TextView(context).apply {
                text = "DISCONNECTED"
                setTextColor(Color.parseColor("#EF476F"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                val badgeBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(Color.parseColor("#261726"))
                }
                background = badgeBg
                setPadding(dp(8), dp(3), dp(8), dp(3))
            }
            addView(hudStatusBadgeText)

            // Error message text (hidden when no error)
            hudErrorText = TextView(context).apply {
                setTextColor(Color.parseColor("#EF476F"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(4), 0, dp(4))
                visibility = View.GONE
            }
            addView(hudErrorText)

            // LIVE TIMER DISPLAY (00:00:00)
            hudTimerText = TextView(context).apply {
                text = "00:00:00"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(6), 0, dp(10))
            }
            addView(hudTimerText)

            // ACTION PLUS BUTTON (Connect / Disconnect in HUD)
            hudActionPlusButton = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val btnBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#00FF66"))
                }
                background = btnBg
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                hudPlusIconText = TextView(context).apply {
                    text = "+"
                    setTextColor(Color.parseColor("#090D16"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, dp(8), 0)
                }
                addView(hudPlusIconText)

                hudPlusSubText = TextView(context).apply {
                    text = "Connect Proxy"
                    setTextColor(Color.parseColor("#090D16"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(hudPlusSubText)

                setOnClickListener {
                    ProxyController.toggleConnection(this@FloatingWidgetService)
                }
            }
            addView(hudActionPlusButton)

            // ACCOUNT BALANCE & SESSION TRAFFIC CONTAINER
            val statsCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val cardBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#121826"))
                    setStroke(dp(1), Color.parseColor("#1E293B"))
                }
                background = cardBg
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(10)
                }

                // Balance Label & Value
                val balanceHeader = TextView(context).apply {
                    text = "ACCOUNT DATA BALANCE"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(balanceHeader)

                hudAccountBalanceText = TextView(context).apply {
                    text = "Balance unavailable"
                    setTextColor(Color.parseColor("#38BDF8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(2), 0, dp(6))
                }
                addView(hudAccountBalanceText)

                // Session Traffic Label & Value
                val trafficHeader = TextView(context).apply {
                    text = "SESSION TRAFFIC (DEVICE)"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(trafficHeader)

                hudTrafficStatsText = TextView(context).apply {
                    text = "↓ 0 B   ↑ 0 B"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.MONOSPACE
                    setPadding(0, dp(2), 0, 0)
                }
                addView(hudTrafficStatsText)
            }
            addView(statsCard)

            // Select Specific Apps button inside Floating HUD
            val hudSpecificAppsBtn = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val btnBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#7B2CBF"))
                }
                background = btnBg
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }

                val icon = ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_menu_agenda)
                    setColorFilter(Color.parseColor("#C084FC"))
                    layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
                }
                addView(icon)

                val label = TextView(context).apply {
                    text = "  Select Specific Apps (Per-App) ⚙️"
                    setTextColor(Color.parseColor("#E9D5FF"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(label)

                setOnClickListener {
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("NAV_TAB", 2)
                    }
                    context.startActivity(appIntent)
                    collapseToBubble()
                }
            }
            addView(hudSpecificAppsBtn)

            // Open Full App Button
            hudOpenAppBtn = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val openBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#1B2538"))
                    setStroke(dp(1), Color.parseColor("#3B82F6"))
                }
                background = openBg
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }

                val openText = TextView(context).apply {
                    text = "Open Full App ↗"
                    setTextColor(Color.parseColor("#60A5FA"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(openText)

                setOnClickListener {
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(appIntent)
                    collapseToBubble()
                }
            }
            addView(hudOpenAppBtn)

            // Close (×) Button at the bottom:
            // Tapping × hides/removes the floating overlay only; it does NOT disconnect the VPN or close the app!
            hudCloseOverlayBtn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val closeBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#1A1118"))
                    setStroke(dp(1), Color.parseColor("#EF476F"))
                }
                background = closeBg
                setPadding(dp(10), dp(7), dp(10), dp(7))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }

                val closeText = TextView(context).apply {
                    text = "✕ Hide Floating Window"
                    setTextColor(Color.parseColor("#EF476F"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                addView(closeText)

                val noteText = TextView(context).apply {
                    text = "Hides overlay • VPN stays running"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                    gravity = Gravity.CENTER
                }
                addView(noteText)

                setOnClickListener {
                    // Turn off floating overlay only without touching VPN connection
                    ProxyController.setFloatingEnabled(this@FloatingWidgetService, false)
                }
            }
            addView(hudCloseOverlayBtn)
        }
    }

    private fun expandToHud() {
        isExpanded = true
        collapsedBubbleView.visibility = View.GONE
        expandedHudCard.visibility = View.VISIBLE

        val lp = layoutParams ?: return
        val cardWidth = dp(290)
        if (lp.x + cardWidth > screenWidth) {
            lp.x = (screenWidth - cardWidth - dp(16)).coerceAtLeast(16)
        }
        windowManager?.updateViewLayout(floatingRootView, lp)
    }

    private fun collapseToBubble() {
        isExpanded = false
        expandedHudCard.visibility = View.GONE
        collapsedBubbleView.visibility = View.VISIBLE
        snapToNearestEdge()
    }

    /**
     * Touch and Drag handling:
     * - Allows dragging the bubble anywhere on screen.
     * - Touch slop prevents accidental touches during dragging.
     * - Direct tap on '+' button: triggers connect / disconnect immediately without expanding the HUD.
     * - Direct tap on timer/pill area: expands to HUD.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndInteractions(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        lp.x = initialX + dx
                        lp.y = (initialY + dy).coerceIn(dp(40), screenHeight - dp(100))
                        windowManager?.updateViewLayout(floatingRootView, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Tapped! Check if tap was on '+' action button or elsewhere on bubble
                        val hitRect = Rect()
                        bubblePlusBtn.getHitRect(hitRect)
                        val touchX = event.x.toInt()
                        val touchY = event.y.toInt()

                        if (hitRect.contains(touchX, touchY)) {
                            // Direct tap on '+' action button -> Toggle connection immediately!
                            ProxyController.toggleConnection(this@FloatingWidgetService)
                        } else {
                            // Tapped bubble info area -> Expand HUD
                            expandToHud()
                        }
                    } else {
                        snapToNearestEdge()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToNearestEdge() {
        val lp = layoutParams ?: return
        val currentX = lp.x
        val bubbleWidth = if (collapsedBubbleView.width > 0) collapsedBubbleView.width else dp(140)
        val targetX = if (currentX + bubbleWidth / 2 > screenWidth / 2) {
            screenWidth - bubbleWidth - dp(16)
        } else {
            dp(16)
        }

        val animator = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                lp.x = anim.animatedValue as Int
                windowManager?.updateViewLayout(floatingRootView, lp)
            }
        }
        animator.start()
    }

    private fun observeProxyState() {
        stateCollectJob?.cancel()
        stateCollectJob = serviceScope.launch {
            ProxyController.state.collectLatest { state ->
                updateUiForState(state)
            }
        }
    }

    private fun updateUiForState(state: ProxyState) {
        val isConnected = state.status == ConnectionStatus.CONNECTED
        val isConnecting = state.status == ConnectionStatus.CONNECTING

        // Update Bubble View
        if (isConnected) {
            bubbleTimerText.text = state.formattedTime()
            (bubbleStatusDot.background as? GradientDrawable)?.setColor(Color.parseColor("#10B981"))
            (collapsedBubbleView.background as? GradientDrawable)?.apply {
                setColor(Color.parseColor("#081C16"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            // Connected state: Plus button shows Stop symbol (⏹)
            bubblePlusText.text = "⏹"
            bubblePlusText.setTextColor(Color.WHITE)
            (bubblePlusBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#EF476F"))
        } else if (isConnecting) {
            bubbleTimerText.text = "Connecting..."
            (bubbleStatusDot.background as? GradientDrawable)?.setColor(Color.parseColor("#FFB703"))
            (collapsedBubbleView.background as? GradientDrawable)?.apply {
                setColor(Color.parseColor("#1C180E"))
                setStroke(dp(1), Color.parseColor("#FFB703"))
            }
            bubblePlusText.text = "⏳"
            bubblePlusText.setTextColor(Color.parseColor("#070B14"))
            (bubblePlusBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#FFB703"))
        } else {
            bubbleTimerText.text = "DH PROXY"
            (bubbleStatusDot.background as? GradientDrawable)?.setColor(Color.parseColor("#EF476F"))
            (collapsedBubbleView.background as? GradientDrawable)?.apply {
                setColor(Color.parseColor("#070B14"))
                setStroke(dp(1), Color.parseColor("#00FF66"))
            }
            bubblePlusText.text = "+"
            bubblePlusText.setTextColor(Color.parseColor("#070B14"))
            (bubblePlusBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#00FF66"))
        }

        // Update Expanded HUD
        val config = state.activeConfig
        if (config != null) {
            hudProxyDetailText.text = "${config.name}\n${config.host}:${config.port} • ${config.protocol.name}"
        } else {
            hudProxyDetailText.text = "No Proxy Selected"
        }

        hudAppsRoutingText.text = when (state.routingMode) {
            RoutingMode.ALL_APPS -> "Mode: All Apps Tunnelled"
            RoutingMode.SELECTED_ONLY -> "Mode: ${state.selectedAppCount} Specific Apps"
            RoutingMode.BYPASS_SELECTED -> "Mode: Bypass ${state.selectedAppCount} Apps"
        }

        hudTimerText.text = if (isConnected) state.formattedTime() else "00:00:00"

        // Account Balance (Never show 0 MB on failure; display formatted balance)
        hudAccountBalanceText.text = state.formattedBalance()

        // Device Session Traffic
        hudTrafficStatsText.text = "↓ ${state.formattedDataIn()}   ↑ ${state.formattedDataOut()}"

        when (state.status) {
            ConnectionStatus.CONNECTED -> {
                hudStatusBadgeText.text = "CONNECTED"
                hudStatusBadgeText.setTextColor(Color.parseColor("#10B981"))
                (hudStatusBadgeText.background as? GradientDrawable)?.setColor(Color.parseColor("#0D2818"))

                hudErrorText.visibility = View.GONE

                hudPlusIconText.text = "⏹"
                hudPlusSubText.text = "Disconnect Proxy"
                (hudActionPlusButton.background as? GradientDrawable)?.setColor(Color.parseColor("#EF476F"))
                hudPlusIconText.setTextColor(Color.WHITE)
                hudPlusSubText.setTextColor(Color.WHITE)
            }
            ConnectionStatus.CONNECTING -> {
                hudStatusBadgeText.text = "CONNECTING..."
                hudStatusBadgeText.setTextColor(Color.parseColor("#FFB703"))
                (hudStatusBadgeText.background as? GradientDrawable)?.setColor(Color.parseColor("#26220D"))

                hudErrorText.visibility = View.GONE

                hudPlusIconText.text = "⏳"
                hudPlusSubText.text = "Connecting..."
                (hudActionPlusButton.background as? GradientDrawable)?.setColor(Color.parseColor("#FFB703"))
                hudPlusIconText.setTextColor(Color.parseColor("#090D16"))
                hudPlusSubText.setTextColor(Color.parseColor("#090D16"))
            }
            ConnectionStatus.ERROR -> {
                hudStatusBadgeText.text = "CONNECTION FAILED"
                hudStatusBadgeText.setTextColor(Color.parseColor("#EF476F"))
                (hudStatusBadgeText.background as? GradientDrawable)?.setColor(Color.parseColor("#2E1016"))

                hudErrorText.visibility = View.VISIBLE
                hudErrorText.text = state.errorMessage ?: "Proxy connection failed. Check server."

                hudPlusIconText.text = "+"
                hudPlusSubText.text = "Retry Connect"
                (hudActionPlusButton.background as? GradientDrawable)?.setColor(Color.parseColor("#00FF66"))
                hudPlusIconText.setTextColor(Color.parseColor("#05080F"))
                hudPlusSubText.setTextColor(Color.parseColor("#05080F"))
            }
            else -> {
                hudStatusBadgeText.text = "DISCONNECTED"
                hudStatusBadgeText.setTextColor(Color.parseColor("#EF476F"))
                (hudStatusBadgeText.background as? GradientDrawable)?.setColor(Color.parseColor("#261726"))

                hudErrorText.visibility = View.GONE

                hudPlusIconText.text = "+"
                hudPlusSubText.text = "Connect Proxy"
                (hudActionPlusButton.background as? GradientDrawable)?.setColor(Color.parseColor("#00FF66"))
                hudPlusIconText.setTextColor(Color.parseColor("#05080F"))
                hudPlusSubText.setTextColor(Color.parseColor("#05080F"))
            }
        }
    }

    private fun dp(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        stateCollectJob?.cancel()
        floatingRootView?.let { root ->
            try {
                windowManager?.removeView(root)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        floatingRootView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FloatProxy Floating Controller",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating proxy button status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DH PROXY Floating Controller")
            .setContentText("Tap '+' on screen to connect or disconnect proxy")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
