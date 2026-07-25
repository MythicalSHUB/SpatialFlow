package com.codetrio.spatialflow.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Dimens(
    val screenMargin: Dp = 16.dp,
    val cardCornerRadius: Dp = 16.dp,
    val smallPadding: Dp = 8.dp,
    val standardPadding: Dp = 16.dp,
    val largePadding: Dp = 24.dp
)

val CompactDimens = Dimens(
    screenMargin = 16.dp,
    cardCornerRadius = 16.dp,
    smallPadding = 8.dp,
    standardPadding = 16.dp,
    largePadding = 24.dp
)

val MediumDimens = Dimens(
    screenMargin = 24.dp,
    cardCornerRadius = 24.dp,
    smallPadding = 12.dp,
    standardPadding = 20.dp,
    largePadding = 32.dp
)

val ExpandedDimens = Dimens(
    screenMargin = 32.dp,
    cardCornerRadius = 24.dp,
    smallPadding = 16.dp,
    standardPadding = 24.dp,
    largePadding = 48.dp
)

val LocalDimens = staticCompositionLocalOf { CompactDimens }
