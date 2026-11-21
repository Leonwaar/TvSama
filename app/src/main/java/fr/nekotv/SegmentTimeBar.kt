package fr.nekotv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import androidx.media3.ui.TimeBar
import androidx.media3.ui.DefaultTimeBar

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SegmentTimeBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : DefaultTimeBar(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var mediaDuration = 0L
    private var playbackPosition = 0L
    private var scrubPosition: Long? = null

    init {
        addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) { scrubPosition = position }
            override fun onScrubMove(timeBar: TimeBar, position: Long) { scrubPosition = position }
            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) { scrubPosition = null }
        })
    }

    override fun setPosition(position: Long) {
        playbackPosition = position
        super.setPosition(position)
    }

    private var segments: List<SkipSegment> = emptyList()

    fun setSegments(value: List<SkipSegment>) {
        if (segments == value) return
        segments = value
        invalidate()
    }

    override fun setDuration(duration: Long) {
        mediaDuration = duration
        super.setDuration(duration)
    }

    internal fun markerBounds(segment: IntroSegment): RectF? {
        if (mediaDuration <= 0 || segment.start < 0 || segment.end <= segment.start || segment.start >= mediaDuration) return null
        // The XML fixes the maximum scrubber diameter at 16dp, matching Media3's 8dp inset.
        val left = paddingLeft + 8 * density
        val right = width - paddingRight - 8 * density
        if (right <= left) return null
        return RectF(left + (right - left) * (segment.start.toDouble() / mediaDuration).toFloat(),
            height / 2f - 2 * density,
            left + (right - left) * (segment.end.coerceAtMost(mediaDuration).toDouble() / mediaDuration).toFloat(),
            height / 2f + 2 * density)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (part in segments) {
            val bounds = markerBounds(part.range) ?: continue
            paint.color = if (part.ending) 0xFFFFD166.toInt() else 0xFF71E4E8.toInt()
            // Leave the native thumb visible, including while seeking.
            val left = paddingLeft + 8 * density
            val right = width - paddingRight - 8 * density
            val cursor = left + (right - left) *
                ((scrubPosition ?: playbackPosition).coerceIn(0, mediaDuration).toDouble() / mediaDuration).toFloat()
            val radius = if (isEnabled) 8 * density else 0f
            if (bounds.left < cursor - radius) {
                canvas.drawRect(bounds.left, bounds.top, minOf(bounds.right, cursor - radius), bounds.bottom, paint)
            }
            if (bounds.right > cursor + radius) {
                canvas.drawRect(maxOf(bounds.left, cursor + radius), bounds.top, bounds.right, bounds.bottom, paint)
            }
        }
    }
}
