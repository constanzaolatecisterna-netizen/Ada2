package com.example.floating

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.firebase.configureAppCheck
import com.example.runtime.AdaExternalAction
import com.example.runtime.AdaRuntimeHub
import com.example.runtime.AdaRuntimeSnapshot
import com.example.runtime.AdaVoiceCoordinator
import com.example.voice.NoiseFilterMode
import com.example.widget.AdaWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class AdaFloatingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager

    private var rootContainer: LinearLayout? = null
    private var orbView: FloatingAdaOrbView? = null
    private var panelView: LinearLayout? = null
    private var speakerBadgeView: TextView? = null
    private var statusView: TextView? = null
    private var responseView: TextView? = null
    private var wakeButtonView: TextView? = null
    private var filterButtonView: TextView? = null
    private var whatsappButtonView: TextView? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var isExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureChannel()
        startCompliantForeground()

        if (!Settings.canDrawOverlays(this)) {
            AdaRuntimeHub.setFloatingActive(this, false)
            stopSelf()
            return
        }

        configureAppCheck(this, null)
        AdaRuntimeHub.setFloatingActive(this, true)
        AdaVoiceCoordinator.onFloatingServiceStarted(this)
        createOverlayViews()

        serviceScope.launch {
            AdaRuntimeHub.state.collect { snapshot ->
                bindState(snapshot)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AdaRuntimeHub.setFloatingActive(this, false)
                AdaVoiceCoordinator.onFloatingServiceStopped(this)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_READ_MESSAGES -> {
                AdaRuntimeHub.dispatchAction(this, AdaExternalAction.ReadMessages)
            }
            ACTION_READ_ALERTS -> {
                AdaRuntimeHub.dispatchAction(this, AdaExternalAction.ReadAlerts)
            }
        }
        return START_STICKY
    }

    private fun startCompliantForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            200,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val readMsgsPending = PendingIntent.getService(
            this,
            201,
            Intent(this, AdaFloatingService::class.java).apply { action = ACTION_READ_MESSAGES },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPending = PendingIntent.getService(
            this,
            202,
            Intent(this, AdaFloatingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ada_orb_notif)
            .setContentTitle("ADA Flotante activa")
            .setContentText("Toca la burbuja para leer mensajes, alertas o hablar.")
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(0, "Mensajes", readMsgsPending)
            .addAction(0, "Cerrar", stopPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasMic = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val fgsType = if (hasMic) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_floating_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.notification_channel_floating_desc)
                }
                manager.createNotificationChannel(ch)
            }
        }
    }

    private fun createOverlayViews() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(180)
        }
        layoutParams = params

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val orbSize = dp(82)
        val orb = FloatingAdaOrbView(this).apply {
            layoutParams = LinearLayout.LayoutParams(orbSize, orbSize)
        }
        orbView = orb

        val panel = buildExpandedPanel().apply {
            visibility = View.GONE
        }
        panelView = panel

        attachDragAndClick(orb, params)

        container.addView(orb)
        container.addView(panel)
        rootContainer = container
        windowManager.addView(container, params)
    }

    private fun buildExpandedPanel(): LinearLayout {
        val cardBg = GradientDrawable().apply {
            setColor(Color.parseColor("#EB0A1220"))
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.parseColor("#5538BDF8"))
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(248), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            }
        }

        // Top row: Title + Close X
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "ADA Flotante"
            setTextColor(Color.parseColor("#FFF8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FF94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(10), dp(4), dp(6), dp(4))
            setOnClickListener {
                AdaRuntimeHub.setFloatingActive(this@AdaFloatingService, false)
                stopSelf()
            }
        }
        headerRow.addView(title)
        headerRow.addView(closeBtn)
        panel.addView(headerRow)

        val statusTxt = TextView(this).apply {
            text = "En espera"
            setTextColor(Color.parseColor("#FF38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, dp(2))
        }
        statusView = statusTxt
        panel.addView(statusTxt)

        val speakerTxt = TextView(this).apply {
            text = "Hablante: sin detectar"
            setTextColor(Color.parseColor("#FFA7F3D0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, 0, 0, dp(6))
        }
        speakerBadgeView = speakerTxt
        panel.addView(speakerTxt)

        val respTxt = TextView(this).apply {
            text = "Toca Hablar o lee tus mensajes."
            setTextColor(Color.parseColor("#FFCBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            maxLines = 2
            setPadding(0, 0, 0, dp(8))
        }
        responseView = respTxt
        panel.addView(respTxt)

        // Row 1: Hablar | Mensajes
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        row1.addView(makeChipButton("Hablar", "#3338BDF8", "#FFE0F2FE") {
            triggerVoiceFromFloating()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        })
        row1.addView(makeChipButton("Mensajes", "#226366F1", "#FFE2E8F0") {
            AdaRuntimeHub.dispatchAction(this@AdaFloatingService, AdaExternalAction.ReadMessages)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginStart = dp(4) }
        })
        panel.addView(row1)

        // Row 2: Notificaciones | Di "ADA"
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        row2.addView(makeChipButton("Notif.", "#26F59E0B", "#FFFDE68A") {
            AdaRuntimeHub.dispatchAction(this@AdaFloatingService, AdaExternalAction.ReadAlerts)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        })
        val wakeBtn = makeChipButton("Di \"ADA\": Off", "#1E293B", "#FFCBD5E1") {
            val next = !AdaRuntimeHub.state.value.wakeWordEnabled
            if (next && !hasMicPermission()) {
                openMainApp(AdaWidgetProvider.ACTION_OPEN_VOICE)
            } else {
                AdaVoiceCoordinator.setWakeWordEnabled(this@AdaFloatingService, next)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginStart = dp(4) }
        }
        wakeButtonView = wakeBtn
        row2.addView(wakeBtn)
        panel.addView(row2)

        // Row 3: Anti-noise mode | WhatsApp Auto | Abrir App
        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        val filterBtn = makeChipButton("Filtro: Estricto", "#1A10B981", "#FFA7F3D0") {
            val cur = AdaRuntimeHub.state.value.noiseFilterMode
            val next = if (cur == NoiseFilterMode.STRICT) NoiseFilterMode.BALANCED else NoiseFilterMode.STRICT
            AdaVoiceCoordinator.setNoiseFilterMode(this@AdaFloatingService, next)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(4) }
        }
        filterButtonView = filterBtn
        row3.addView(filterBtn)
        row3.addView(makeChipButton("Abrir App", "#1E293B", "#FF94A3B8") {
            openMainApp(null)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginStart = dp(4) }
        })
        panel.addView(row3)

        val waBtn = makeChipButton("WhatsApp Auto: On", "#1A10B981", "#FFA7F3D0") {
            val next = !AdaRuntimeHub.state.value.autoReadWhatsApp
            AdaRuntimeHub.dispatchAction(
                this@AdaFloatingService,
                AdaExternalAction.ToggleAutoReadWhatsApp(next)
            )
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34))
        }
        whatsappButtonView = waBtn
        panel.addView(waBtn)

        return panel
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun triggerVoiceFromFloating() {
        val snap = AdaRuntimeHub.state.value
        if (snap.isSpeaking) {
            AdaVoiceCoordinator.stopSpeech(this)
        }
        if (hasMicPermission()) {
            AdaVoiceCoordinator.startDirectListening(this)
        } else {
            openMainApp(AdaWidgetProvider.ACTION_OPEN_VOICE)
        }
    }

    private fun openMainApp(action: String?) {
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (action != null) {
                putExtra(AdaWidgetProvider.EXTRA_WIDGET_ACTION, action)
            }
        }
        startActivity(launch)
    }

    private fun makeChipButton(
        label: String,
        bgHex: String,
        textHex: String,
        onClick: () -> Unit,
    ): TextView {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor(bgHex))
            cornerRadius = dp(11).toFloat()
            setStroke(dp(1), Color.parseColor("#33FFFFFF"))
        }
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            background = bg
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setOnClickListener { onClick() }
        }
    }

    private fun attachDragAndClick(target: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var moved = false

        target.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    params.x = (initialX + dx).coerceAtLeast(0)
                    params.y = (initialY + dy).coerceAtLeast(dp(24))
                    rootContainer?.let { windowManager.updateViewLayout(it, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        isExpanded = !isExpanded
                        panelView?.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun bindState(snapshot: AdaRuntimeSnapshot) {
        orbView?.updateOrbVisual(
            listening = snapshot.isListening,
            speaking = snapshot.isSpeaking,
            thinking = snapshot.isThinking,
            wakeArmed = snapshot.wakeWordArmed,
            pulseTick = snapshot.speechPulseTick,
        )

        statusView?.text = when {
            snapshot.isListening -> "Escuchando voz..."
            snapshot.isSpeaking -> "ADA hablando..."
            snapshot.isThinking -> "Procesando..."
            snapshot.wakeWordArmed -> "Di \"ADA\" (anti-ruido activo)"
            snapshot.wakeWordEnabled -> "Escucha \"ADA\" en pausa"
            else -> "Lista · Toca para hablar"
        }

        val speaker = snapshot.lastSpeaker
        speakerBadgeView?.text = if (speaker != null) {
            "Hablante: ${speaker.displayName} (${speaker.confidencePercent}%)"
        } else {
            "Perfil activo: ${snapshot.activeProfileName}"
        }

        responseView?.text = snapshot.transcriptPreview.takeIf { it.isNotBlank() }
            ?: snapshot.responseText

        wakeButtonView?.text = if (snapshot.wakeWordEnabled) "Di \"ADA\": On" else "Di \"ADA\": Off"
        filterButtonView?.text = "Filtro: ${snapshot.noiseFilterMode.label}"
        whatsappButtonView?.text = if (snapshot.autoReadWhatsApp) "WhatsApp Auto: On" else "WhatsApp Auto: Off"
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    override fun onDestroy() {
        AdaRuntimeHub.setFloatingActive(this, false)
        AdaVoiceCoordinator.onFloatingServiceStopped(this)
        orbView?.stopAnimator()
        serviceScope.cancel()
        rootContainer?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        rootContainer = null
        super.onDestroy()
    }

    private class FloatingAdaOrbView(context: Context) : View(context) {
        private var listening = false
        private var speaking = false
        private var thinking = false
        private var wakeArmed = false
        private var lastPulseTick = 0L
        private var lastPulseAtMs = 0L

        private var motionAnimator: ValueAnimator? = null
        private val density = resources.displayMetrics.density

        private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            pathEffect = DashPathEffect(floatArrayOf(12f * density, 9f * density), 0f)
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        private val spherePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val lightSweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arcLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 1.9f * density
        }
        private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF04111F")
        }
        private val tempRect = RectF()

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            startAnimator()
        }

        override fun onDetachedFromWindow() {
            stopAnimator()
            super.onDetachedFromWindow()
        }

        fun startAnimator() {
            if (motionAnimator != null) return
            motionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1_200L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { invalidate() }
                start()
            }
        }

        fun stopAnimator() {
            motionAnimator?.cancel()
            motionAnimator = null
        }

        fun updateOrbVisual(
            listening: Boolean,
            speaking: Boolean,
            thinking: Boolean,
            wakeArmed: Boolean,
            pulseTick: Long,
        ) {
            if (pulseTick != lastPulseTick) {
                lastPulseTick = pulseTick
                lastPulseAtMs = SystemClock.uptimeMillis()
            }
            this.listening = listening
            this.speaking = speaking
            this.thinking = thinking
            this.wakeArmed = wakeArmed
            if (isAttachedToWindow && motionAnimator == null) {
                startAnimator()
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = SystemClock.uptimeMillis()
            val t = now / 1000.0
            val wordElapsedMs = (now - lastPulseAtMs).coerceIn(0L, 400L)
            val wordBoost = if (speaking && lastPulseAtMs > 0L) {
                (1f - wordElapsedMs / 320f).coerceIn(0f, 1f)
            } else {
                0f
            }

            // Steady sphere position & radius; only the holographic light moves
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) * 0.34f

            val primaryColor = when {
                listening -> Color.parseColor("#FF7DD3FC")
                speaking -> Color.parseColor("#FF34D399")
                thinking -> Color.parseColor("#FFA78BFA")
                wakeArmed -> Color.parseColor("#FF38BDF8")
                else -> Color.parseColor("#FF38BDF8")
            }
            val secondaryColor = when {
                speaking -> Color.parseColor("#FF059669")
                thinking -> Color.parseColor("#FF4F46E5")
                else -> Color.parseColor("#FF6366F1")
            }

            // Expanding light ripple waves while speaking or listening
            if (speaking || listening) {
                val waveSpeed = if (speaking) 1.8 else 1.15
                for (i in 0 until 2) {
                    val phase = ((t * waveSpeed + i * 0.5) % 1.0).toFloat()
                    val waveR = r * (1.02f + phase * 0.40f)
                    val baseAlpha = if (speaking) 160f + wordBoost * 75f else 120f
                    val alpha = ((1f - phase) * baseAlpha).toInt().coerceIn(0, 255)
                    wavePaint.color = Color.argb(
                        alpha,
                        Color.red(primaryColor),
                        Color.green(primaryColor),
                        Color.blue(primaryColor),
                    )
                    wavePaint.strokeWidth = (2.6f - phase * 1.4f).coerceAtLeast(1f) * density
                    canvas.drawCircle(cx, cy, waveR, wavePaint)
                }
            }

            // Rotating dashed orbital light halo
            canvas.save()
            val haloRotDeg = ((t * if (speaking) 95.0 else if (listening) 65.0 else 28.0) % 360.0).toFloat()
            canvas.rotate(haloRotDeg, cx, cy)
            haloPaint.color = Color.argb(
                if (speaking || listening || wakeArmed) 185 else 95,
                Color.red(primaryColor),
                Color.green(primaryColor),
                Color.blue(primaryColor),
            )
            canvas.drawCircle(cx, cy, r * 1.16f, haloPaint)
            canvas.restore()

            // Pulsing outer radial light aura
            val glowBreath = (0.5 + 0.5 * sin(t * if (speaking) 9.0 else 2.4)).toFloat()
            val auraR = r * (1.30f + if (speaking) 0.12f * glowBreath + wordBoost * 0.10f else 0.04f * glowBreath)
            val auraCenterAlpha = when {
                speaking -> (100f + 55f * glowBreath + wordBoost * 65f).toInt().coerceIn(0, 230)
                listening || thinking -> (95f + 35f * glowBreath).toInt().coerceIn(0, 200)
                else -> (72f + 22f * glowBreath).toInt().coerceIn(0, 160)
            }
            auraPaint.shader = RadialGradient(
                cx,
                cy,
                auraR,
                intArrayOf(
                    Color.argb(auraCenterAlpha, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0.42f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, auraR, auraPaint)

            // Outer ring + steady sphere with orbiting internal light hotspot
            ringPaint.color = if (wakeArmed || listening || speaking) primaryColor else Color.parseColor("#8838BDF8")
            canvas.drawCircle(cx, cy, r, ringPaint)

            val coreR = r * 0.86f
            val lightX = when {
                speaking -> (cos(t * 9.0) * coreR * 0.30).toFloat()
                listening || thinking -> (cos(t * 4.5) * coreR * 0.24).toFloat()
                else -> -coreR * 0.22f + (cos(t * 1.8) * coreR * 0.06).toFloat()
            }
            val lightY = when {
                speaking -> (sin(t * 13.5) * coreR * 0.28).toFloat()
                listening || thinking -> (sin(t * 4.5) * coreR * 0.22).toFloat()
                else -> -coreR * 0.24f + (sin(t * 1.8) * coreR * 0.06).toFloat()
            }
            val hotspotR = coreR * (1.28f + if (speaking) wordBoost * 0.22f else 0f)
            spherePaint.shader = RadialGradient(
                cx + lightX,
                cy + lightY,
                hotspotR,
                intArrayOf(Color.WHITE, primaryColor, secondaryColor),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, coreR, spherePaint)

            // Secondary sweeping volumetric inner light sheen
            if (speaking || listening) {
                val sweepX = (cos(-t * 6.8) * coreR * 0.35).toFloat()
                val sweepY = (sin(-t * 9.0) * coreR * 0.31).toFloat()
                val sheenAlpha = if (speaking) (92f + wordBoost * 65f).toInt().coerceIn(0, 190) else 54
                lightSweepPaint.shader = RadialGradient(
                    cx + sweepX,
                    cy + sweepY,
                    coreR * 0.72f,
                    intArrayOf(Color.argb(sheenAlpha, 255, 255, 255), Color.TRANSPARENT),
                    null,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(cx, cy, coreR, lightSweepPaint)
            }

            // Sweeping light arcs rotating around the sphere perimeter
            if (speaking || listening || thinking) {
                val arcR = r * 1.04f
                val startDeg = ((t * if (speaking) 170.0 else 110.0) % 360.0).toFloat()
                val arcAlpha = if (speaking) (165f + wordBoost * 75f).toInt().coerceIn(0, 245) else 115
                tempRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
                arcLightPaint.color = Color.argb(arcAlpha, 255, 255, 255)
                canvas.drawArc(tempRect, startDeg, 72f, false, arcLightPaint)
                arcLightPaint.color = Color.argb(
                    arcAlpha,
                    Color.red(primaryColor),
                    Color.green(primaryColor),
                    Color.blue(primaryColor),
                )
                canvas.drawArc(tempRect, startDeg + 180f, 72f, false, arcLightPaint)
            }

            // Centered blinking eyes (no mouth)
            val blinkWindow = (now % 3_600L)
            val isBlinking = blinkWindow in 0L..120L
            val eyeW = coreR * 0.17f
            val baseEyeH = coreR * 0.36f
            val eyeH = if (isBlinking) coreR * 0.08f else baseEyeH
            val eyeOffsetX = coreR * 0.30f

            tempRect.set(
                cx - eyeOffsetX - eyeW / 2f,
                cy - eyeH / 2f,
                cx - eyeOffsetX + eyeW / 2f,
                cy + eyeH / 2f,
            )
            canvas.drawRoundRect(tempRect, eyeW / 2f, eyeW / 2f, facePaint)

            tempRect.set(
                cx + eyeOffsetX - eyeW / 2f,
                cy - eyeH / 2f,
                cx + eyeOffsetX + eyeW / 2f,
                cy + eyeH / 2f,
            )
            canvas.drawRoundRect(tempRect, eyeW / 2f, eyeW / 2f, facePaint)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ada_floating_channel"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_STOP = "com.example.floating.STOP"
        private const val ACTION_READ_MESSAGES = "com.example.floating.READ_MESSAGES"
        private const val ACTION_READ_ALERTS = "com.example.floating.READ_ALERTS"

        fun start(context: Context) {
            val intent = Intent(context, AdaFloatingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AdaFloatingService::class.java))
            AdaRuntimeHub.setFloatingActive(context, false)
        }
    }
}
