package com.mmartosdev.ink.playground

import android.annotation.SuppressLint
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokesView

@Composable
@SuppressLint("ClickableViewAccessibility")
fun InkCanvas(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val inProgressStrokesView = InProgressStrokesView(context)
    Box(
        modifier = modifier,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            factory = { context ->
                val rootView = FrameLayout(context)
                inProgressStrokesView.apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                }
                rootView.addView(inProgressStrokesView)
                rootView
            },
        )
    }
}
