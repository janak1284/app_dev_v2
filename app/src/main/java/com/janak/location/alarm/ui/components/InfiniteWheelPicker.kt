package com.janak.location.alarm.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfiniteWheelPicker(
    items: List<String>,
    initialIndex: Int = 0,
    visibleItemsCount: Int = 3, // Use an odd number for a clear center
    itemHeight: Dp = 48.dp,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
    onItemSelected: (index: Int, item: String) -> Unit
) {
    if (items.isEmpty()) return

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % items.size) + initialIndex
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
            val centerItem = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + (item.size / 2) - viewportCenter)
            }
            centerItem?.let {
                val actualIndex = it.index % items.size
                onItemSelected(actualIndex, items[actualIndex])
            }
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItemsCount)
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemsCount / 2))
        ) {
            items(
                count = Int.MAX_VALUE,
                key = { it }
            ) { index ->
                val actualIndex = index % items.size
                val item = items[actualIndex]

                val isCenter by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == index }
                        if (visibleItem != null) {
                            val viewportCenter = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
                            val itemCenter = visibleItem.offset + (visibleItem.size / 2)
                            abs(itemCenter - viewportCenter) < (itemHeightPx / 2)
                        } else {
                            false
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val layoutInfo = listState.layoutInfo
                            val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == index }
                            if (visibleItem != null) {
                                val viewportCenter = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f
                                val itemCenter = visibleItem.offset + (visibleItem.size / 2f)
                                val distanceToCenter = abs(itemCenter - viewportCenter)
                                val maxDistance = itemHeightPx * 1.5f // Start fading outside this range

                                val ratio = 1f - (distanceToCenter / maxDistance).coerceIn(0f, 1f)
                                
                                alpha = 0.3f + (ratio * 0.7f)     // partial opacity to fully opaque
                                scaleX = 0.7f + (ratio * 0.3f)    // scale down (e.g., 0.7f) to scale 1.0f
                                scaleY = 0.7f + (ratio * 0.3f)
                            } else {
                                alpha = 0.3f
                                scaleX = 0.7f
                                scaleY = 0.7f
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        textAlign = TextAlign.Center,
                        color = if (isCenter) Color.White else Color.DarkGray,
                        style = textStyle.copy(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            )
                        ),
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
