package com.mmartosdev.ink.playground

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Stable
class StrokeAuthoringState(
    internal val inProgressStrokesView: InProgressStrokesView,
) : InProgressStrokesFinishedListener {
    var currentStrokeId: InProgressStrokeId? = null
    var currentPointerId: Int? = null
    val finishedStrokes = mutableStateOf(emptySet<Stroke>())
    internal val motionEventPredictor: MotionEventPredictor = MotionEventPredictor.newInstance(inProgressStrokesView)

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        finishedStrokes.value += strokes.values
        inProgressStrokesView.removeFinishedStrokes(strokes.keys)
    }
}

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
    inProgressStrokesView: InProgressStrokesView = rememberInProgressStrokesView(),
    strokeAuthoringState: StrokeAuthoringState = rememberStrokeAuthoringState(inProgressStrokesView),
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
            factory = {
                inProgressStrokesView.apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    setOnTouchListener(StrokeAuthoringTouchListener(strokeAuthoringState, brush))
                }
            },
        )
    }
}

class StrokeAuthoringTouchListener(
    private val strokeAuthoringState: StrokeAuthoringState,
    private val brush: Brush,
) : View.OnTouchListener {

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val predictedEvent = strokeAuthoringState.motionEventPredictor.run {
            record(event)
            predict()
        }

        return when (mapEventToAction(event)) {
            StrokeAction.Start -> {
                handleStartStroke(
                    event = event,
                    view = view,
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
        view: View,
    ) {
        view.requestUnbufferedDispatch(event)
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        strokeAuthoringState.currentPointerId = pointerId
        strokeAuthoringState.currentStrokeId = strokeAuthoringState.inProgressStrokesView.startStroke(
            event = event,
            pointerId = pointerId,
            brush = brush,
        )
    }

    private fun handleUpdateStroke(
        event: MotionEvent,
        predictedEvent: MotionEvent?,
    ) {
        val pointerId = checkNotNull(strokeAuthoringState.currentPointerId)
        val strokeId = checkNotNull(strokeAuthoringState.currentStrokeId)

        for (pointerIndex in 0 until event.pointerCount) {
            if (event.getPointerId(pointerIndex) != pointerId) continue
            strokeAuthoringState.inProgressStrokesView.addToStroke(
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
        if (pointerId == strokeAuthoringState.currentPointerId) {
            strokeAuthoringState.inProgressStrokesView.finishStroke(
                event,
                pointerId,
                strokeAuthoringState.currentStrokeId!!
            )
        }
    }

    private fun handleCancelStroke(
        event: MotionEvent,
    ) {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        check(pointerId == strokeAuthoringState.currentPointerId)

        strokeAuthoringState.inProgressStrokesView.cancelStroke(
            strokeId = strokeAuthoringState.currentStrokeId!!,
            event = event,
        )
    }

    private fun doPostHandlerAction(event: MotionEvent, view: View) {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            view.performClick()
        }
    }
}

@Composable
fun rememberInProgressStrokesView(): InProgressStrokesView {
    val context = LocalContext.current
    return remember { InProgressStrokesView(context) }
}

@Composable
fun rememberStrokeAuthoringState(
    inProgressStrokesView: InProgressStrokesView,
): StrokeAuthoringState = remember(inProgressStrokesView) {
    StrokeAuthoringState(inProgressStrokesView).also { listener: InProgressStrokesFinishedListener ->
        inProgressStrokesView.addFinishedStrokesListener(listener)
    }
}
