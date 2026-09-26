package com.cepncz.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class CadView(context: Context) : View(context) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 170, 55)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 210, 90)
        style = Paint.Style.FILL
    }

    private var entities: List<NczEntity> = emptyList()
    private var layers: List<String> = emptyList()
    private val hiddenLayers = mutableSetOf<Int>()
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var minX = 0.0
    private var maxX = 1.0
    private var minY = 0.0
    private var maxY = 1.0

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(0.2f, 50f)
                invalidate()
                return true
            }
        })

    fun setEntities(items: List<NczEntity>, layerNames: List<String> = emptyList()) {
        entities = items
        layers = layerNames
        hiddenLayers.clear()
        val points = items.flatMap { it.points }
        if (points.isNotEmpty()) {
            minX = points.minOf { it.x }; maxX = points.maxOf { it.x }
            minY = points.minOf { it.y }; maxY = points.maxOf { it.y }
        }
        zoom = 1f; offsetX = 0f; offsetY = 0f
        invalidate()
    }

    fun setLayerVisible(layer: Int, visible: Boolean) {
        if (visible) hiddenLayers.remove(layer) else hiddenLayers.add(layer)
        invalidate()
    }
    fun setAllLayersVisible(visible: Boolean) {
        hiddenLayers.clear()
        if (!visible) entities.map { it.layer }.distinct().forEach { hiddenLayers.add(it) }
        invalidate()
    }
    fun isLayerVisible(layer: Int) = layer !in hiddenLayers
    fun layerName(layer: Int) = layers.getOrNull(layer)?.takeIf { it.isNotBlank() } ?: "Tabaka $layer"
    fun layerCounts(): Map<Int, Int> = entities.groupingBy { it.layer }.eachCount()

    private fun scale(): Float {
        if (width <= 60 || height <= 60) return 1f
        return min(
            (width - 60f) / (maxX - minX).coerceAtLeast(0.001).toFloat(),
            (height - 60f) / (maxY - minY).coerceAtLeast(0.001).toFloat()
        )
    }
    private fun screenX(x: Double) = ((30f + (x - minX).toFloat() * scale() - width / 2f) * zoom + width / 2f + offsetX)
    private fun screenY(y: Double) = ((30f + (maxY - y).toFloat() * scale() - height / 2f) * zoom + height / 2f + offsetY)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(24, 27, 31))
        if (entities.isEmpty()) {
            linePaint.textSize = 32f
            canvas.drawText("NCZ dosyası açın", 28f, 50f, linePaint)
            return
        }
        for (entity in entities) {
            if (entity.layer in hiddenLayers || entity.points.isEmpty()) continue
            when (entity.kind) {
                "Circle" -> {
                    val p = entity.points.first()
                    canvas.drawCircle(screenX(p.x), screenY(p.y), (entity.radius * scale() * zoom).toFloat(), linePaint)
                }
                "Arc" -> {
                    val p = entity.points.first()
                    val r = (entity.radius * scale() * zoom).toFloat()
                    val rect = RectF(screenX(p.x)-r, screenY(p.y)-r, screenX(p.x)+r, screenY(p.y)+r)
                    canvas.drawArc(rect, entity.startAngle.toFloat(), (entity.endAngle-entity.startAngle).toFloat(), false, linePaint)
                }
                "Text" -> {
                    val p = entity.points.first()
                    pointPaint.textSize = (entity.textHeight * scale() * zoom).toFloat().coerceIn(9f, 48f)
                    canvas.save()
                    canvas.rotate((-entity.rotation).toFloat(), screenX(p.x), screenY(p.y))
                    canvas.drawText(entity.text, screenX(p.x), screenY(p.y), pointPaint)
                    canvas.restore()
                }
                else -> {
                    if (entity.points.size == 1) {
                        val p = entity.points.first()
                        canvas.drawCircle(screenX(p.x), screenY(p.y), 3f, pointPaint)
                    } else {
                        val path = Path()
                        path.moveTo(screenX(entity.points.first().x), screenY(entity.points.first().y))
                        entity.points.drop(1).forEach { path.lineTo(screenX(it.x), screenY(it.y)) }
                        if (entity.kind == "Polygon") path.close()
                        canvas.drawPath(path, linePaint)
                    }
                }
            }
        }
    }

    private fun inspect(x: Float, y: Float) {
        var best: NczEntity? = null
        var bestDistance = Double.MAX_VALUE
        for (entity in entities) {
            if (entity.layer in hiddenLayers) continue
            for (p in entity.points) {
                val distance = hypot((screenX(p.x)-x).toDouble(), (screenY(p.y)-y).toDouble())
                if (distance < bestDistance) { bestDistance = distance; best = entity }
            }
        }
        best?.takeIf { bestDistance < 45 }?.let { entity ->
            val p = entity.points.first()
            Toast.makeText(context, entity.kind+" • "+layerName(entity.layer)+"\nX: %.3f  Y: %.3f".format(p.x,p.y), Toast.LENGTH_LONG).show()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX=event.x; lastY=event.y; moved=false }
                MotionEvent.ACTION_MOVE -> {
                    val dx=event.x-lastX; val dy=event.y-lastY
                    if (abs(dx)+abs(dy)>3) moved=true
                    offsetX+=dx; offsetY+=dy; lastX=event.x; lastY=event.y
                    invalidate()
                }
                MotionEvent.ACTION_UP -> if (!moved) inspect(event.x,event.y)
            }
        }
        return true
    }
}
