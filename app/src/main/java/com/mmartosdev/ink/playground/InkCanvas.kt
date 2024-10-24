package com.mmartosdev.ink.playground

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor

@Composable
@SuppressLint("ClickableViewAccessibility")
fun InkCanvas(
    modifier: Modifier = Modifier,
) {
    val brush = Brush.createWithColorIntArgb(
        family = StockBrushes.pressurePenLatest,
        colorIntArgb = Color.Black.toArgb(),
        size = 15f,
        epsilon = 0.1F
    )
    val context = LocalContext.current
    val inProgressStrokesView = InProgressStrokesView(context)
    var currentPointerId by remember { mutableStateOf<Int?>(null) }
    var currentStrokeId by remember { mutableStateOf<InProgressStrokeId?>(null) }
    Box(
        modifier = modifier,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            factory = { context ->
                val rootView = FrameLayout(context)
                val predictor = MotionEventPredictor.newInstance(rootView)
                inProgressStrokesView.apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                        override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                            inProgressStrokesView.removeFinishedStrokes(strokes.keys)
                        }
                    })
                }
                rootView.setOnTouchListener { view, event ->
                    predictor.record(event)
                    val predictedEvent = predictor.predict()
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            view.requestUnbufferedDispatch(event)
                            val pointerIndex = event.actionIndex
                            val pointerId = event.getPointerId(pointerIndex)
                            currentPointerId = pointerId
                            currentStrokeId =
                                inProgressStrokesView.startStroke(
                                    event = event,
                                    pointerId = pointerId,
                                    brush = brush
                                )
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val pointerId = checkNotNull(currentPointerId)
                            val strokeId = checkNotNull(currentStrokeId)

                            for (pointerIndex in 0 until event.pointerCount) {
                                if (event.getPointerId(pointerIndex) != pointerId) continue
                                inProgressStrokesView.addToStroke(
                                    event,
                                    pointerId,
                                    strokeId,
                                    predictedEvent,
                                )
                            }
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            val pointerIndex = event.actionIndex
                            val pointerId = event.getPointerId(pointerIndex)
                            check(pointerId == currentPointerId)
                            val strokeId = checkNotNull(currentStrokeId)
                            inProgressStrokesView.finishStroke(
                                event,
                                pointerId,
                                strokeId
                            )
                            view.performClick()
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            val pointerIndex = event.actionIndex
                            val pointerId = event.getPointerId(pointerIndex)
                            check(pointerId == currentPointerId)

                            val strokeId = checkNotNull(currentStrokeId)
                            inProgressStrokesView.cancelStroke(strokeId, event)
                            true
                        }

                        else -> false
                    }.also {
                        predictedEvent?.recycle()
                    }
                }
                rootView.addView(inProgressStrokesView)
                rootView
            },
        )
    }
}
