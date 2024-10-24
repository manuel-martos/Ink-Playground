package com.mmartosdev.ink.playground

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor

sealed interface StrokeAction {
    data object Start : StrokeAction
    data object Update : StrokeAction
    data object Finish : StrokeAction
    data object Cancel : StrokeAction
    data object Skip : StrokeAction
}

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
    Box(
        modifier = modifier,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            factory = { context ->
                InProgressStrokesView(context).apply {
                    val motionEventPredictor = MotionEventPredictor.newInstance(this)
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                        override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                            removeFinishedStrokes(strokes.keys)
                        }
                    })
                    setOnTouchListener(StrokeAuthoringTouchListener(brush, motionEventPredictor, this))
                }
            },
        )
    }
}

class StrokeAuthoringTouchListener(
    private val brush: Brush,
    private val motionEventPredictor: MotionEventPredictor,
    private val inProgressStrokesView: InProgressStrokesView,
) : View.OnTouchListener {

    var currentPointerId by mutableStateOf<Int?>(null)
    var currentStrokeId by mutableStateOf<InProgressStrokeId?>(null)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val predictedEvent = motionEventPredictor.run {
            record(event)
            predict()
        }

        return when (mapEventToAction(event)) {
            StrokeAction.Start -> {
                handleStartStroke(
                    event = event,
                    view = view,
                    defaultBrush = brush,
                )
                true
            }

            StrokeAction.Update -> {
                handleUpdateStroke(
                    event = event,
                    predictedEvent = predictedEvent,
                )
                true
            }

            StrokeAction.Finish -> {
                handleFinishStroke(
                    event = event,
                )
                true
            }

            StrokeAction.Cancel -> {
                handleCancelStroke(
                    event = event,
                )
                true
            }

            StrokeAction.Skip -> false
        }.also {
            doPostHandlerAction(event, view)
            predictedEvent?.recycle()
        }
    }

    private fun mapEventToAction(
        event: MotionEvent,
    ): StrokeAction =
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> StrokeAction.Start
            MotionEvent.ACTION_MOVE -> StrokeAction.Update
            MotionEvent.ACTION_UP -> StrokeAction.Finish
            MotionEvent.ACTION_CANCEL -> StrokeAction.Cancel
            else -> StrokeAction.Skip
        }

    private fun handleStartStroke(
        event: MotionEvent,
        defaultBrush: Brush,
        view: View,
    ) {
        view.requestUnbufferedDispatch(event)
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        currentPointerId = pointerId
        currentStrokeId = inProgressStrokesView.startStroke(
            event = event,
            pointerId = pointerId,
            brush = defaultBrush
        )
    }

    private fun handleUpdateStroke(
        event: MotionEvent,
        predictedEvent: MotionEvent?,
    ) {
        val pointerId = checkNotNull(currentPointerId)
        val strokeId = checkNotNull(currentStrokeId)

        // TODO: Check if there is a chance to have more than one pointer ID within event pointers
        for (pointerIndex in 0 until event.pointerCount) {
            if (event.getPointerId(pointerIndex) != pointerId) continue
            inProgressStrokesView.addToStroke(
                event,
                pointerId,
                strokeId,
                predictedEvent,
            )
        }
    }

    private fun handleFinishStroke(
        event: MotionEvent,
    ) {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        if (pointerId == currentPointerId) {
            inProgressStrokesView.finishStroke(
                event,
                pointerId,
                currentStrokeId!!
            )
        }
    }

    private fun handleCancelStroke(
        event: MotionEvent,
    ) {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        check(pointerId == currentPointerId)

        inProgressStrokesView.cancelStroke(
            strokeId = currentStrokeId!!,
            event = event,
        )
    }

    private fun doPostHandlerAction(event: MotionEvent, view: View) {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            view.performClick()
        }
    }
}
