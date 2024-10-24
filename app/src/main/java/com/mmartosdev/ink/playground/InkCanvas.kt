package com.mmartosdev.ink.playground

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor

@Stable
class StrokeAuthoringState(
    internal val inProgressStrokesView: InProgressStrokesView,
) : InProgressStrokesFinishedListener {
    var moveEventCount: Int = 0
    var currentStrokeId: InProgressStrokeId? = null
    var currentPointerId: Int? = null
    lateinit var motionEventPredictor: MotionEventPredictor
    val finishedStrokes = mutableStateOf(emptySet<Stroke>())

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

@Stable
fun interface StrokeActionInferer {
    fun mapStateToAction(strokeAuthoringState: StrokeAuthoringState): StrokeAction
}

@Composable
@SuppressLint("ClickableViewAccessibility")
fun InkCanvas(
    family: BrushFamily,
    size: Float,
    color: Color,
    strokeActionInferer: StrokeActionInferer,
    modifier: Modifier = Modifier,
    inProgressStrokesView: InProgressStrokesView = rememberInProgressStrokesView(),
    strokeAuthoringState: StrokeAuthoringState = rememberStrokeAuthoringState(inProgressStrokesView),
    strokeAuthoringTouchListener: StrokeAuthoringTouchListener = rememberStrokeAuthoringTouchListener(
        strokeAuthoringState = strokeAuthoringState,
        family = family,
        color = color,
        size = size,
        strokeActionInferer = strokeActionInferer,
    ),
) {
    val canvasStrokeRenderer = CanvasStrokeRenderer.create()
    Box(
        modifier = modifier,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            factory = { context ->
                val rootView = FrameLayout(context)
                val parentViewGroup = strokeAuthoringState.inProgressStrokesView.parent as? ViewGroup
                parentViewGroup?.apply {
                    removeView(strokeAuthoringState.inProgressStrokesView)
                }
                strokeAuthoringState.inProgressStrokesView.apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                }
                strokeAuthoringState.motionEventPredictor = MotionEventPredictor.newInstance(rootView)
                rootView.setOnTouchListener(strokeAuthoringTouchListener)
                rootView.addView(strokeAuthoringState.inProgressStrokesView)
                rootView
            },
            update = { rootView ->
                rootView.setOnTouchListener(strokeAuthoringTouchListener)
            }
        )
        Canvas(modifier = Modifier) {
            val canvasTransform = Matrix()
            drawContext.canvas.nativeCanvas.concat(canvasTransform)
            val canvas = drawContext.canvas.nativeCanvas

            strokeAuthoringState.finishedStrokes.value.forEach { stroke ->
                canvasStrokeRenderer.draw(
                    stroke = stroke,
                    canvas = canvas,
                    strokeToScreenTransform = canvasTransform,
                )
            }
        }
    }
}

class StrokeAuthoringTouchListener(
    private val strokeAuthoringState: StrokeAuthoringState,
    private val brush: Brush,
    private val strokeActionInferer: StrokeActionInferer,
) : View.OnTouchListener {

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val predictedEvent = strokeAuthoringState.motionEventPredictor.run {
            record(event)
            predict()
        }

        doPreHandlerAction(event)
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
            MotionEvent.ACTION_MOVE -> strokeActionInferer.mapStateToAction(strokeAuthoringState)
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
        strokeAuthoringState.currentPointerId = pointerId
        strokeAuthoringState.currentStrokeId = strokeAuthoringState.inProgressStrokesView.startStroke(
            event = event,
            pointerId = pointerId,
            brush = defaultBrush
        )
    }

    private fun handleUpdateStroke(
        event: MotionEvent,
        predictedEvent: MotionEvent?,
    ) {
        val pointerId = checkNotNull(strokeAuthoringState.currentPointerId)
        val strokeId = checkNotNull(strokeAuthoringState.currentStrokeId)

        // TODO: Check if there is a chance to have more than one pointer ID within event pointers
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

    private fun doPreHandlerAction(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_MOVE) {
            strokeAuthoringState.moveEventCount = 0
        }
    }

    private fun doPostHandlerAction(event: MotionEvent, view: View) {
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            strokeAuthoringState.moveEventCount++
        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
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

@Composable
fun rememberStrokeAuthoringTouchListener(
    strokeAuthoringState: StrokeAuthoringState,
    family: BrushFamily,
    color: Color,
    size: Float,
    strokeActionInferer: StrokeActionInferer,
): StrokeAuthoringTouchListener =
    remember(family, color, size, strokeActionInferer) {
        StrokeAuthoringTouchListener(
            strokeAuthoringState = strokeAuthoringState,
            brush = Brush.createWithColorIntArgb(
                family = family,
                colorIntArgb = color.toArgb(),
                size = size,
                epsilon = 0.1F
            ),
            strokeActionInferer = strokeActionInferer,
        )
    }
