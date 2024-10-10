package com.mmartosdev.ink.playground

import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.UiThread
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import com.mmartosdev.ink.playground.ui.theme.InkPlaygroundTheme

class MainActivity : ComponentActivity(), InProgressStrokesFinishedListener {
    private val finishedStrokesState = mutableStateOf(emptySet<Stroke>())
    private lateinit var inProgressStrokesView: InProgressStrokesView

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inProgressStrokesView = InProgressStrokesView(this)
        inProgressStrokesView.addFinishedStrokesListener(this)

        enableEdgeToEdge()
        setContent {
            InkPlaygroundTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                ) { innerPadding ->
                    var family by remember { mutableStateOf(StockBrushes.markerLatest) }
                    var size by remember { mutableFloatStateOf(5f) }
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                    ) {
                        InkPlayground(
                            family = family,
                            size = size,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            FilterChip(
                                selected = family == StockBrushes.markerLatest,
                                onClick = { family = StockBrushes.markerLatest },
                                label = { Text("Marker") }
                            )
                            FilterChip(
                                selected = family == StockBrushes.pressurePenLatest,
                                onClick = { family = StockBrushes.pressurePenLatest },
                                label = { Text("Pressure Pen") }
                            )
                            FilterChip(
                                selected = family == StockBrushes.highlighterLatest,
                                onClick = { family = StockBrushes.highlighterLatest },
                                label = { Text("Highlighter") }
                            )
                        }
                        Text("Stroke Size", style = MaterialTheme.typography.titleMedium)
                        Slider(
                            value = size,
                            onValueChange = { size = it },
                            valueRange = 1f..50f,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }


    @UiThread
    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        finishedStrokesState.value += strokes.values
        inProgressStrokesView.removeFinishedStrokes(strokes.keys)
    }


    @Composable
    private fun InkPlayground(
        family: BrushFamily,
        size: Float,
        modifier: Modifier = Modifier,
    ) {
        val canvasStrokeRenderer = CanvasStrokeRenderer.create()
        val currentPointerId = remember { mutableStateOf<Int?>(null) }
        val currentStrokeId = remember { mutableStateOf<InProgressStrokeId?>(null) }
        val defaultBrush = Brush.createWithColorIntArgb(
            family = family,
            colorIntArgb = Color.Black.toArgb(),
            size = size,
            epsilon = 0.1F
        )

        val touchListener = View.OnTouchListener { view, event ->
            // ATTENTION!!
            // In order to dynamically update the defaultBrush, touchListener should be defined in the scope of the composable.
            // With this approach, incorporating predictor mechanism will end up with a really hacky implementation as predictor
            // should be created linked with the rootView created by AndroidView composable.

//             predictor.record(event)
//             val predictedEvent = predictor.predict()

            try {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // First pointer - treat it as inking.
                        view.requestUnbufferedDispatch(event)
                        val pointerIndex = event.actionIndex
                        val pointerId = event.getPointerId(pointerIndex)
                        currentPointerId.value = pointerId
                        currentStrokeId.value =
                            inProgressStrokesView.startStroke(
                                event = event,
                                pointerId = pointerId,
                                brush = defaultBrush
                            )
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val pointerId = checkNotNull(currentPointerId.value)
                        val strokeId = checkNotNull(currentStrokeId.value)

                        for (pointerIndex in 0 until event.pointerCount) {
                            if (event.getPointerId(pointerIndex) != pointerId) continue
                            inProgressStrokesView.addToStroke(
                                event,
                                pointerId,
                                strokeId,
                                //                                                predictedEvent
                            )
                            Log.d("InkPlayground", "InkPlayground: addToStroke")
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val pointerIndex = event.actionIndex
                        val pointerId = event.getPointerId(pointerIndex)
                        check(pointerId == currentPointerId.value)
                        inProgressStrokesView.finishStroke(
                            event,
                            pointerId,
                            currentStrokeId.value!!
                        )
                        view.performClick()
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        val pointerIndex = event.actionIndex
                        val pointerId = event.getPointerId(pointerIndex)
                        check(pointerId == currentPointerId.value)

                        inProgressStrokesView.cancelStroke(
                            strokeId = currentStrokeId.value!!,
                            event = event,
                        )
                        true
                    }

                    else -> false
                }
            } finally {
//                predictedEvent?.recycle()
            }
        }

        Box(
            modifier = modifier,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val rootView = FrameLayout(context)
                    inProgressStrokesView.parent?.let {
                        (it as? ViewGroup)?.removeView(inProgressStrokesView)
                    }
                    inProgressStrokesView.apply {
                        layoutParams =
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            )
                    }
//                    val predictor = MotionEventPredictor.newInstance(rootView)

                    rootView.setOnTouchListener(touchListener)
                    rootView.addView(inProgressStrokesView)
                    rootView
                }
            )
            Canvas(modifier = Modifier) {
                val canvasTransform = Matrix()
                drawContext.canvas.nativeCanvas.concat(canvasTransform)
                val canvas = drawContext.canvas.nativeCanvas

                finishedStrokesState.value.forEach { stroke ->
                    canvasStrokeRenderer.draw(stroke = stroke, canvas = canvas, strokeToScreenTransform = canvasTransform)
                }
            }
        }
        (inProgressStrokesView.parent as? ViewGroup)?.apply {
            setOnTouchListener(touchListener)
        }
    }
}
