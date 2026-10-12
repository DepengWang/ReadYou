package me.ash.reader.ui.page.adaptive

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneMotion
import androidx.compose.material3.adaptive.layout.PaneScaffoldMotionDataProvider
import androidx.compose.material3.adaptive.layout.PaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculateDefaultEnterTransition
import androidx.compose.material3.adaptive.layout.calculateDefaultExitTransition
import me.ash.reader.ui.motion.MotionConstants
import me.ash.reader.ui.motion.materialSharedAxisXIn
import me.ash.reader.ui.motion.materialSharedAxisXOut

private const val INITIAL_OFFSET_FACTOR = 0.10f

@ExperimentalMaterial3AdaptiveApi
internal fun <Role : PaneScaffoldRole> PaneScaffoldMotionDataProvider<Role>
    .calculateEnterTransition(
        role: Role,
        durationMillis: Int = MotionConstants.DefaultMotionDuration,
        offsetFactor: Float = INITIAL_OFFSET_FACTOR,
    ) =
    when (this[role].motion) {
        PaneMotion.EnterFromRight ->
            materialSharedAxisXIn(
                initialOffsetX = { (it * offsetFactor).toInt() },
                durationMillis = durationMillis,
            )
        PaneMotion.EnterFromLeft ->
            materialSharedAxisXIn(
                initialOffsetX = { -(it * offsetFactor).toInt() },
                durationMillis = durationMillis,
            )
        else -> calculateDefaultEnterTransition(role)
    }

@ExperimentalMaterial3AdaptiveApi
internal fun <Role : PaneScaffoldRole> PaneScaffoldMotionDataProvider<Role>.calculateExitTransition(
    role: Role,
    durationMillis: Int = MotionConstants.DefaultMotionDuration,
    offsetFactor: Float = INITIAL_OFFSET_FACTOR,
) =
    when (this[role].motion) {
        PaneMotion.ExitToLeft ->
            materialSharedAxisXOut(
                targetOffsetX = { -(it * offsetFactor).toInt() },
                durationMillis = durationMillis,
            )
        PaneMotion.ExitToRight ->
            materialSharedAxisXOut(
                targetOffsetX = { (it * offsetFactor).toInt() },
                durationMillis = durationMillis,
            )
        else -> calculateDefaultExitTransition(role)
    }
