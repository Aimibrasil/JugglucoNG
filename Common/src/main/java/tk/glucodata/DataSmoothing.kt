package tk.glucodata

import android.content.Context
import tk.glucodata.settings.SettingsRegistry

object DataSmoothing {
    private const val PREFS_NAME = SettingsRegistry.FILE
    private const val LAST_ENABLED_MINUTES_KEY = "dashboard_data_smoothing_last_enabled_minutes"
    private const val MAX_CHUNK_INTERVAL_MINUTES = 5
    private const val DEFAULT_ENABLED_MINUTES = MAX_CHUNK_INTERVAL_MINUTES

    private val allowedMinutes = intArrayOf(0, 2, 3, 4, 5, 7, 10, 13)
    private val enabledMinutes = intArrayOf(2, 3, 4, 5, 7, 10, 13)

    @JvmStatic
    fun allowedMinutes(): IntArray = allowedMinutes.copyOf()

    @JvmStatic
    fun enabledMinutesOptions(): IntArray = enabledMinutes.copyOf()

    @JvmStatic
    fun sanitizeMinutes(minutes: Int): Int {
        return if (allowedMinutes.contains(minutes)) minutes else 0
    }

    @JvmStatic
    fun getMinutes(context: Context): Int {
        return sanitizeMinutes(SettingsRegistry.SMOOTHING_MINUTES.readInt(context))
    }

    @JvmStatic
    fun getLastEnabledMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // The same key, but a different question: the fallback here is the last
        // window that was on, not the setting's own default of 0.
        val current = sanitizeMinutes(
            prefs.getInt(SettingsRegistry.SMOOTHING_MINUTES.key, DEFAULT_ENABLED_MINUTES)
        )
        val fallback = current.takeIf { it > 0 } ?: DEFAULT_ENABLED_MINUTES
        return sanitizeMinutes(prefs.getInt(LAST_ENABLED_MINUTES_KEY, fallback))
            .takeIf { it > 0 }
            ?: DEFAULT_ENABLED_MINUTES
    }

    @JvmStatic
    fun isEnabled(context: Context): Boolean = getMinutes(context) > 0

    @JvmStatic
    fun collapseChunks(context: Context): Boolean =
        SettingsRegistry.SMOOTHING_COLLAPSE_CHUNKS.readBool(context)

    /**
     * Whether the newest reading — the one shown as current and the one sent out — is estimated
     * by [CausalGlucoseFilter] instead of the edge of the averaging window. Only the method
     * changes: where smoothing applies is still decided by [targets].
     */
    @JvmStatic
    fun useCausalFilter(context: Context): Boolean =
        SettingsRegistry.SMOOTHING_CAUSAL_LIVE.readBool(context)

    /**
     * The three places a reading is used. Each is switched on its own: the chart (presentation),
     * the current reading (trend arrow, Δ, widgets, notification and every alert evaluated
     * against a value), and what is sent to other apps.
     */
    data class Targets(val chart: Boolean, val current: Boolean, val sent: Boolean) {
        internal fun toBits(): Int =
            (if (chart) CHART_BIT else 0) or (if (current) CURRENT_BIT else 0) or (if (sent) SENT_BIT else 0)

        companion object {
            private const val CHART_BIT = 1
            private const val CURRENT_BIT = 2
            private const val SENT_BIT = 4

            @JvmField val ALL = Targets(chart = true, current = true, sent = true)

            internal fun fromBits(bits: Int): Targets =
                Targets(bits and CHART_BIT != 0, bits and CURRENT_BIT != 0, bits and SENT_BIT != 0)
        }
    }

    /**
     * Where smoothing applies. Until the user picks targets, they are derived from the
     * graph-only / exchange-only pair this setting replaced (see [legacyTargets]), so an
     * upgrade keeps every reading going where it went before.
     */
    @JvmStatic
    fun targets(context: Context): Targets {
        val bits = SettingsRegistry.SMOOTHING_TARGETS.readInt(context)
        if (bits in 0..7) return Targets.fromBits(bits)
        return legacyTargets(
            graphOnly = SettingsRegistry.SMOOTHING_GRAPH_ONLY.readBool(context),
            exchangeOutputsOnly = SettingsRegistry.SMOOTHING_EXCHANGE_OUTPUTS_ONLY.readBool(context),
            collapseChunks = collapseChunks(context)
        )
    }

    /**
     * The old two-switch model as targets. "Collapse" kept one *smoothed* reading per interval,
     * so under graph-only it pulled sent readings back into smoothing.
     */
    internal fun legacyTargets(graphOnly: Boolean, exchangeOutputsOnly: Boolean, collapseChunks: Boolean = false) =
        Targets(
            chart = !exchangeOutputsOnly,
            current = !graphOnly && !exchangeOutputsOnly,
            sent = exchangeOutputsOnly || !graphOnly || collapseChunks
        )

    @JvmStatic
    fun shouldSmoothGraph(context: Context): Boolean = graphSmoothingMinutes(context) > 0

    @JvmStatic
    fun graphSmoothingMinutes(context: Context): Int = graphSmoothingMinutes(getMinutes(context), targets(context))

    /**
     * The window the app's own reading carries: the trend arrow, the Δ readout, the delta
     * alarms and every threshold evaluated against a value. Smoothing here is deliberate —
     * it is what keeps a single wild sample from deciding an arrow — and switching off the
     * "current" target is the way to opt out of it.
     */
    @JvmStatic
    fun localSmoothingMinutes(context: Context): Int = localSmoothingMinutes(getMinutes(context), targets(context))

    @JvmStatic
    fun shouldSmoothLocalData(context: Context): Boolean = localSmoothingMinutes(context) > 0

    @JvmStatic
    fun shouldSmoothExchangeOutputs(context: Context): Boolean = exchangeSmoothingMinutes(context) > 0

    /**
     * Averaging window, in minutes, that every outgoing reading must carry — 0 when
     * exchange outputs are configured to stay unsmoothed. Java-side outputs get this
     * applied for them by [CurrentDisplaySource.resolveCurrentForExchange]; the native
     * Nightscout uploader builds its entries straight from stored polls and has to ask
     * for the window itself (see NightscoutCalibration.getExchangeSmoothingSeconds).
     */
    @JvmStatic
    fun exchangeSmoothingMinutes(context: Context): Int =
        exchangeSmoothingMinutes(getMinutes(context), targets(context))

    @JvmStatic
    fun shouldCollapseExchangeOutputs(context: Context): Boolean =
        shouldCollapseExchangeOutputs(getMinutes(context), targets(context), collapseChunks(context))

    /**
     * How often, in minutes, an exchange output is fed: 0 means every reading.
     *
     * "Collapse into chunks" thins the *rate* of what leaves the phone; it never changes what is
     * sent. Whatever is sent is the newest reading under its own timestamp — the payload is not
     * built from the last point of a completed chunk, which would be up to two intervals old.
     * This applies uniformly to every exchange destination, including the xDrip/xInfuus loop feed
     * AAPS doses from: it used to be exempt from collapse because the old design broke the "own
     * timestamp" guarantee for it, not because thinning it was unsafe on its own.
     */
    @JvmStatic
    fun exchangeThrottleIntervalMinutes(context: Context): Int =
        exchangeThrottleIntervalMinutes(getMinutes(context), targets(context), collapseChunks(context))

    @JvmStatic
    fun setMinutes(context: Context, minutes: Int) {
        val sanitized = sanitizeMinutes(minutes)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(SettingsRegistry.SMOOTHING_MINUTES.key, sanitized)
            .apply()
        if (sanitized > 0) {
            setLastEnabledMinutes(context, sanitized)
        }
    }

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        setMinutes(
            context,
            if (enabled) getLastEnabledMinutes(context) else 0
        )
    }

    @JvmStatic
    fun setCollapseChunks(context: Context, enabled: Boolean) {
        // Derived targets depend on collapse (see [legacyTargets]); pin them first so that
        // changing it does not also switch sent readings on or off behind the user's back.
        val pinTargets = SettingsRegistry.SMOOTHING_TARGETS.readInt(context) !in 0..7
        val targets = if (pinTargets) targets(context) else null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply { targets?.let { putInt(SettingsRegistry.SMOOTHING_TARGETS.key, it.toBits()) } }
            .putBoolean(SettingsRegistry.SMOOTHING_COLLAPSE_CHUNKS.key, enabled)
            .apply()
    }

    @JvmStatic
    fun setTargets(context: Context, targets: Targets) {
        // The old pair is still written, as near as it can say it, for a watch on a build
        // that predates targets and reads only these two.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(SettingsRegistry.SMOOTHING_TARGETS.key, targets.toBits())
            .putBoolean(SettingsRegistry.SMOOTHING_GRAPH_ONLY.key, targets.chart && !targets.current)
            .putBoolean(SettingsRegistry.SMOOTHING_EXCHANGE_OUTPUTS_ONLY.key, !targets.chart)
            .apply()
    }

    @JvmStatic
    fun setUseCausalFilter(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SettingsRegistry.SMOOTHING_CAUSAL_LIVE.key, enabled)
            .apply()
    }

    @JvmStatic
    fun collapseIntervalMinutes(smoothingMinutes: Int): Int {
        val sanitized = sanitizeMinutes(smoothingMinutes)
        if (sanitized <= 0) {
            return 0
        }
        return minOf(sanitized, MAX_CHUNK_INTERVAL_MINUTES)
    }

    @JvmStatic
    @JvmOverloads
    fun smoothNativePoints(
        points: List<GlucosePoint>?,
        smoothingMinutes: Int,
        collapseChunks: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): List<GlucosePoint> {
        if (points.isNullOrEmpty()) {
            return emptyList()
        }
        val sanitizedMinutes = sanitizeMinutes(smoothingMinutes)
        if (sanitizedMinutes <= 0) {
            return points
        }
        if (points.size < 3) {
            return if (collapseChunks) {
                collapsePointsForDisplay(points, collapseIntervalMinutes(sanitizedMinutes), nowMillis)
            } else {
                points
            }
        }

        val halfWindowMs = (sanitizedMinutes * 60_000L) / 2L
        if (halfWindowMs <= 0L) {
            return points
        }

        val smoothedAuto = smoothSeries(points, halfWindowMs, useRawValue = false)
        val smoothedRaw = smoothSeries(points, halfWindowMs, useRawValue = true)
        val smoothed = ArrayList<GlucosePoint>(points.size)
        points.indices.forEach { index ->
            val source = points[index]
            val point = GlucosePoint(source.timestamp, smoothedAuto[index], smoothedRaw[index])
            point.color = source.color
            point.sensorSerial = source.sensorSerial
            point.sealedDisplayValue = source.sealedDisplayValue
            point.sealedDisplayViewMode = source.sealedDisplayViewMode
            point.uncertainty = source.uncertainty?.shifted(point.value - source.value)
            smoothed.add(point)
        }

        return if (collapseChunks) {
            collapsePointsForDisplay(smoothed, collapseIntervalMinutes(sanitizedMinutes), nowMillis)
        } else {
            smoothed
        }
    }

    /**
     * Whether two display points belong to the same sensor. A point without a serial is the merged
     * live sample of the selected sensor; aliases of one sensor match through
     * [SensorIdentity.matches], while imported readings do not.
     */
    internal fun sameCausalSource(a: GlucosePoint, b: GlucosePoint): Boolean =
        a.sensorSerial.isNullOrBlank() ||
            b.sensorSerial.isNullOrBlank() ||
            SensorIdentity.matches(a.sensorSerial, b.sensorSerial)

    private fun smoothSeries(
        points: List<GlucosePoint>,
        halfWindowMs: Long,
        useRawValue: Boolean
    ): FloatArray = GlucoseSmoothing.smoothLane(
        points = points,
        halfWindowMs = halfWindowMs,
        timestamp = { it.timestamp },
        selector = { if (useRawValue) it.rawValue else it.value },
    )

    /**
     * [smoothNativePoints] with [CausalGlucoseFilter] in place of the averaging window: every
     * point is estimated from the past only, so the newest one is not an edge fit and will not be
     * redrawn differently once later readings arrive. [smoothingMinutes] still gates it (0 = off)
     * and still sets the collapse interval.
     */
    @JvmStatic
    @JvmOverloads
    fun smoothNativePointsCausal(
        points: List<GlucosePoint>?,
        smoothingMinutes: Int,
        collapseChunks: Boolean,
        mgdlPerUnit: Float,
        nowMillis: Long = System.currentTimeMillis()
    ): List<GlucosePoint> {
        if (points.isNullOrEmpty()) return emptyList()
        val sanitizedMinutes = sanitizeMinutes(smoothingMinutes)
        if (sanitizedMinutes <= 0) return points

        val autoLane = CausalGlucoseFilter.filterLane(
            points, { it.timestamp }, { it.value }, mgdlPerUnit, sameSource = ::sameCausalSource
        )
        val rawLane = CausalGlucoseFilter.filterLane(
            points, { it.timestamp }, { it.rawValue }, mgdlPerUnit, sameSource = ::sameCausalSource
        )
        val filtered = ArrayList<GlucosePoint>(points.size)
        points.indices.forEach { index ->
            val source = points[index]
            val point = GlucosePoint(source.timestamp, autoLane[index], rawLane[index])
            point.color = source.color
            point.sensorSerial = source.sensorSerial
            point.sealedDisplayValue = source.sealedDisplayValue
            point.sealedDisplayViewMode = source.sealedDisplayViewMode
            point.uncertainty = source.uncertainty?.shifted(autoLane[index] - source.value)
            filtered.add(point)
        }
        return if (collapseChunks) {
            collapsePointsForDisplay(filtered, collapseIntervalMinutes(sanitizedMinutes), nowMillis)
        } else {
            filtered
        }
    }
    private fun isValidLane(value: Float): Boolean = value.isFinite() && value >= 0.1f

    internal fun collapsePointsForDisplay(
        points: List<GlucosePoint>,
        smoothingMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): List<GlucosePoint> {
        if (points.isEmpty() || smoothingMinutes <= 0) {
            return points
        }

        val bucketDurationMs = smoothingMinutes * 60_000L
        val openBucket = nowMillis / bucketDurationMs
        val collapsed = ArrayList<GlucosePoint>()
        var activeBucket = Long.MIN_VALUE
        var pending: GlucosePoint? = null
        var bucketAuto: GlucosePoint? = null
        var bucketRaw: GlucosePoint? = null

        // The bucket's last point, and for a lane it lacks the bucket's newest
        // point that has it, at its own time: Libre's History lane is one value
        // per 15 minutes, and moving it onto the last point could stretch two
        // neighbouring values past the chart's gap limit.
        fun flush() {
            val last = pending ?: return
            val autoHolder = bucketAuto?.takeIf { !isValidLane(last.value) }
            val rawHolder = bucketRaw?.takeIf { !isValidLane(last.rawValue) }
            listOfNotNull(autoHolder, rawHolder).distinct().sortedBy { it.timestamp }.forEach(collapsed::add)
            collapsed.add(last)
        }

        for (point in points) {
            val bucket = point.timestamp / bucketDurationMs
            if (bucket != activeBucket) {
                if (activeBucket < openBucket) flush()
                activeBucket = bucket
                bucketAuto = null
                bucketRaw = null
            }
            if (isValidLane(point.value)) bucketAuto = point
            if (isValidLane(point.rawValue)) bucketRaw = point
            pending = point
        }

        if (activeBucket < openBucket) flush()
        return when {
            collapsed.isNotEmpty() -> collapsed
            points.isNotEmpty() -> listOf(points.last())
            else -> points
        }
    }

    /** The window a target carries: [smoothingMinutes] when it is switched on, otherwise 0. */
    private fun windowFor(smoothingMinutes: Int, on: Boolean): Int {
        val sanitized = sanitizeMinutes(smoothingMinutes)
        return if (sanitized > 0 && on) sanitized else 0
    }

    /** Presentation. */
    @JvmStatic
    fun graphSmoothingMinutes(smoothingMinutes: Int, targets: Targets): Int =
        windowFor(smoothingMinutes, targets.chart)

    /** Evaluation: the current reading, its arrow and the alerts. */
    @JvmStatic
    fun localSmoothingMinutes(smoothingMinutes: Int, targets: Targets): Int =
        windowFor(smoothingMinutes, targets.current)

    internal fun exchangeSmoothingMinutes(smoothingMinutes: Int, targets: Targets): Int =
        windowFor(smoothingMinutes, targets.sent)

    internal fun shouldCollapseExchangeOutputs(smoothingMinutes: Int, targets: Targets, collapseChunks: Boolean): Boolean =
        collapseChunks && exchangeSmoothingMinutes(smoothingMinutes, targets) > 0

    internal fun exchangeThrottleIntervalMinutes(smoothingMinutes: Int, targets: Targets, collapseChunks: Boolean): Int =
        if (shouldCollapseExchangeOutputs(smoothingMinutes, targets, collapseChunks)) {
            collapseIntervalMinutes(smoothingMinutes)
        } else {
            0
        }

    // The pre-targets graph-only / exchange-only pair, kept as the specification of what
    // [legacyTargets] must preserve for settings that were never migrated.

    internal fun graphSmoothingMinutes(smoothingMinutes: Int, exchangeOutputsOnly: Boolean): Int =
        graphSmoothingMinutes(smoothingMinutes, legacyTargets(graphOnly = false, exchangeOutputsOnly = exchangeOutputsOnly))

    internal fun localSmoothingMinutes(smoothingMinutes: Int, graphOnly: Boolean, exchangeOutputsOnly: Boolean): Int =
        localSmoothingMinutes(smoothingMinutes, legacyTargets(graphOnly, exchangeOutputsOnly))

    internal fun shouldSmoothExchangeOutputs(
        smoothingMinutes: Int,
        graphOnly: Boolean,
        exchangeOutputsOnly: Boolean,
        collapseChunks: Boolean = false
    ): Boolean = exchangeSmoothingMinutes(smoothingMinutes, graphOnly, exchangeOutputsOnly, collapseChunks) > 0

    internal fun exchangeSmoothingMinutes(
        smoothingMinutes: Int,
        graphOnly: Boolean,
        exchangeOutputsOnly: Boolean,
        collapseChunks: Boolean = false
    ): Int = exchangeSmoothingMinutes(smoothingMinutes, legacyTargets(graphOnly, exchangeOutputsOnly, collapseChunks))

    internal fun shouldCollapseExchangeOutputs(
        smoothingMinutes: Int,
        graphOnly: Boolean,
        exchangeOutputsOnly: Boolean,
        collapseChunks: Boolean
    ): Boolean = shouldCollapseExchangeOutputs(
        smoothingMinutes, legacyTargets(graphOnly, exchangeOutputsOnly, collapseChunks), collapseChunks
    )

    internal fun exchangeThrottleIntervalMinutes(
        smoothingMinutes: Int,
        graphOnly: Boolean,
        exchangeOutputsOnly: Boolean,
        collapseChunks: Boolean
    ): Int = exchangeThrottleIntervalMinutes(
        smoothingMinutes, legacyTargets(graphOnly, exchangeOutputsOnly, collapseChunks), collapseChunks
    )

    private fun setLastEnabledMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(LAST_ENABLED_MINUTES_KEY, minutes)
            .apply()
    }
}
