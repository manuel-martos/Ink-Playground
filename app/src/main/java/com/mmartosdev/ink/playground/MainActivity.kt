package com.mmartosdev.ink.playground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.StockBrushes
import com.mmartosdev.ink.playground.ui.theme.InkPlaygroundTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InkPlaygroundTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                ) { innerPadding ->
                    var family by remember { mutableStateOf(StockBrushes.markerLatest) }
                    var size by remember { mutableFloatStateOf(5f) }
                    var dashed by remember { mutableStateOf(false) }
                    val inProgressStrokesView: InProgressStrokesView = rememberInProgressStrokesView()
                    val strokeAuthoringState: StrokeAuthoringState = rememberStrokeAuthoringState(inProgressStrokesView)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                        ) {
                        InkCanvas(
                            family = family,
                            size = size,
                            color = MaterialTheme.colorScheme.onSurface,
                            strokeActionInferer = { strokeAuthoringState ->
                                if (dashed) {
                                    if ((strokeAuthoringState.moveEventCount / 5) % 2 == 0) {
                                        if (strokeAuthoringState.moveEventCount % 5 != 4) {
                                            StrokeAction.Update
                                        } else {
                                            StrokeAction.Finish
                                        }
                                    } else {
                                        if (strokeAuthoringState.moveEventCount % 5 == 4) {
                                            StrokeAction.Start
                                        } else {
                                            StrokeAction.Skip
                                        }
                                    }
                                } else {
                                    StrokeAction.Update
                                }
                            },
                            inProgressStrokesView = inProgressStrokesView,
                            strokeAuthoringState = strokeAuthoringState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(width = 1.dp, color = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(16.dp))
                                .clip(shape = RoundedCornerShape(16.dp))
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
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
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            ) {
                                FilterChip(
                                    selected = !dashed,
                                    onClick = { dashed = false },
                                    label = { Text("Continuous") }
                                )
                                FilterChip(
                                    selected = dashed,
                                    onClick = { dashed = true },
                                    label = { Text("Dashed") }
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Stroke Size", style = MaterialTheme.typography.titleMedium)
                            Slider(
                                value = size,
                                onValueChange = { size = it },
                                valueRange = 1f..50f,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        Button(
                            onClick = { strokeAuthoringState.finishedStrokes.value = emptySet() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}
