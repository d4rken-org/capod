package eu.darken.capod.main.ui.stemactions

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionSettings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class StemActionConfigViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val stemActionSettings: StemActionSettings,
) : ViewModel4(dispatcherProvider) {

    val state = combine(
        stemActionSettings.leftSingle.flow,
        stemActionSettings.leftDouble.flow,
        stemActionSettings.leftTriple.flow,
        stemActionSettings.leftLong.flow,
        stemActionSettings.rightSingle.flow,
        stemActionSettings.rightDouble.flow,
        stemActionSettings.rightTriple.flow,
        stemActionSettings.rightLong.flow,
    ) { values ->
        State(
            leftSingle = values[0],
            leftDouble = values[1],
            leftTriple = values[2],
            leftLong = values[3],
            rightSingle = values[4],
            rightDouble = values[5],
            rightTriple = values[6],
            rightLong = values[7],
        )
    }.asLiveState()

    data class State(
        val leftSingle: StemAction = StemAction.NONE,
        val leftDouble: StemAction = StemAction.NONE,
        val leftTriple: StemAction = StemAction.NONE,
        val leftLong: StemAction = StemAction.NONE,
        val rightSingle: StemAction = StemAction.NONE,
        val rightDouble: StemAction = StemAction.NONE,
        val rightTriple: StemAction = StemAction.NONE,
        val rightLong: StemAction = StemAction.NONE,
    )

    fun setLeftSingle(action: StemAction) = launch {
        stemActionSettings.leftSingle.update { action }
        applyCrossSideEffect(action, stemActionSettings.rightSingle)
    }

    fun setLeftDouble(action: StemAction) = launch {
        stemActionSettings.leftDouble.update { action }
        applyCrossSideEffect(action, stemActionSettings.rightDouble)
    }

    fun setLeftTriple(action: StemAction) = launch {
        stemActionSettings.leftTriple.update { action }
        applyCrossSideEffect(action, stemActionSettings.rightTriple)
    }

    fun setLeftLong(action: StemAction) = launch {
        stemActionSettings.leftLong.update { action }
        applyCrossSideEffect(action, stemActionSettings.rightLong)
    }

    fun setRightSingle(action: StemAction) = launch {
        stemActionSettings.rightSingle.update { action }
        applyCrossSideEffect(action, stemActionSettings.leftSingle)
    }

    fun setRightDouble(action: StemAction) = launch {
        stemActionSettings.rightDouble.update { action }
        applyCrossSideEffect(action, stemActionSettings.leftDouble)
    }

    fun setRightTriple(action: StemAction) = launch {
        stemActionSettings.rightTriple.update { action }
        applyCrossSideEffect(action, stemActionSettings.leftTriple)
    }

    fun setRightLong(action: StemAction) = launch {
        stemActionSettings.rightLong.update { action }
        applyCrossSideEffect(action, stemActionSettings.leftLong)
    }

    private suspend fun applyCrossSideEffect(
        selected: StemAction,
        otherSide: eu.darken.capod.common.datastore.DataStoreValue<StemAction>,
    ) {
        when (selected) {
            StemAction.NONE -> otherSide.update { StemAction.NONE }
            StemAction.NO_ACTION -> {} // No side-effect
            else -> {
                if (otherSide.flow.first() == StemAction.NONE) {
                    otherSide.update { StemAction.NO_ACTION }
                }
            }
        }
    }

    fun resetAll() = launch { stemActionSettings.resetAll() }
}
