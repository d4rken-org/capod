package eu.darken.capod.pods.core.apple

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ModelFeaturesTest : BaseTest() {

    @Test
    fun `feature flags match supported model matrix`() {
        featureExpectations.forEach { expectation ->
            withClue(expectation.name) {
                modelsWith(expectation.predicate) shouldBe expectation.models
            }
        }
    }

    @Test
    fun `models without ear detection match intentional matrix`() {
        modelsWithout { it.hasEarDetection } shouldBe setOf(
            PodModel.BEATS_FLEX,
            PodModel.BEATS_SOLO_3,
            PodModel.BEATS_SOLO_PRO,
            PodModel.BEATS_SOLO_4,
            PodModel.BEATS_SOLO_BUDS,
            PodModel.BEATS_STUDIO_3,
            PodModel.BEATS_STUDIO_BUDS,
            PodModel.BEATS_STUDIO_BUDS_PLUS,
            PodModel.BEATS_STUDIO_PRO,
            PodModel.BEATS_X,
            PodModel.POWERBEATS_3,
            PodModel.POWERBEATS_4,
            PodModel.UNKNOWN,
        )
    }

    @Test
    fun `UNKNOWN model has no features`() {
        PodModel.UNKNOWN.features shouldBe PodModel.Features()
    }

    @Test
    fun `ear detection toggle implies ear detection`() {
        PodModel.entries
            .filter { it.features.hasEarDetectionToggle }
            .forEach { model ->
                withClue(model.name) {
                    model.features.hasEarDetection shouldBe true
                }
            }
    }

    @Test
    fun `sleep detection implies ear detection`() {
        PodModel.entries
            .filter { it.features.hasSleepDetection }
            .forEach { model ->
                withClue(model.name) {
                    model.features.hasEarDetection shouldBe true
                }
            }
    }

    @Test
    fun `case implies dual pods`() {
        PodModel.entries
            .filter { it.features.hasCase }
            .forEach { model ->
                withClue(model.name) {
                    model.features.hasDualPods shouldBe true
                }
            }
    }

    @Test
    fun `adaptive ANC implies ANC control`() {
        PodModel.entries
            .filter { it.features.hasAdaptiveAnc }
            .forEach { model ->
                withClue(model.name) {
                    model.features.hasAncControl shouldBe true
                }
            }
    }

    @Test
    fun `adaptive audio noise implies adaptive ANC`() {
        PodModel.entries
            .filter { it.features.hasAdaptiveAudioNoise }
            .forEach { model ->
                withClue(model.name) {
                    model.features.hasAdaptiveAnc shouldBe true
                }
            }
    }

    @Test
    fun `listening mode cycle implies ANC control`() {
        PodModel.entries
            .filter { it.features.hasListeningModeCycle }
            .forEach { model ->
                withClue(model.name) {
                    model.features.hasAncControl shouldBe true
                }
            }
    }

    @Test
    fun `allow off option implies listening mode cycle`() {
        PodModel.entries
            .filter { it.features.hasAllowOffOption }
            .forEach { model ->
                withClue(model.name) {
                    model.features.hasListeningModeCycle shouldBe true
                }
            }
    }

    private fun modelsWith(predicate: (PodModel.Features) -> Boolean): Set<PodModel> = PodModel.entries
        .filter { predicate(it.features) }
        .toSet()

    private fun modelsWithout(predicate: (PodModel.Features) -> Boolean): Set<PodModel> = PodModel.entries
        .filterNot { predicate(it.features) }
        .toSet()

    private data class FeatureExpectation(
        val name: String,
        val predicate: (PodModel.Features) -> Boolean,
        val models: Set<PodModel>,
    )

    private fun feature(
        name: String,
        predicate: (PodModel.Features) -> Boolean,
        models: Set<PodModel>,
    ) = FeatureExpectation(name, predicate, models)

    private val dualPodModels = setOf(
        PodModel.AIRPODS_GEN1,
        PodModel.AIRPODS_GEN2,
        PodModel.AIRPODS_GEN3,
        PodModel.AIRPODS_GEN4,
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.BEATS_SOLO_BUDS,
        PodModel.BEATS_STUDIO_BUDS,
        PodModel.BEATS_STUDIO_BUDS_PLUS,
        PodModel.POWERBEATS_PRO,
        PodModel.POWERBEATS_PRO2,
        PodModel.BEATS_FIT_PRO,
        PodModel.FAKE_AIRPODS_GEN1,
        PodModel.FAKE_AIRPODS_GEN2,
        PodModel.FAKE_AIRPODS_GEN3,
        PodModel.FAKE_AIRPODS_PRO,
        PodModel.FAKE_AIRPODS_PRO2,
    )

    private val earDetectionModels = setOf(
        PodModel.AIRPODS_GEN1,
        PodModel.AIRPODS_GEN2,
        PodModel.AIRPODS_GEN3,
        PodModel.AIRPODS_GEN4,
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.AIRPODS_MAX,
        PodModel.AIRPODS_MAX_USBC,
        PodModel.AIRPODS_MAX2,
        PodModel.POWERBEATS_PRO,
        PodModel.POWERBEATS_PRO2,
        PodModel.BEATS_FIT_PRO,
        PodModel.FAKE_AIRPODS_GEN1,
        PodModel.FAKE_AIRPODS_GEN2,
        PodModel.FAKE_AIRPODS_GEN3,
        PodModel.FAKE_AIRPODS_PRO,
        PodModel.FAKE_AIRPODS_PRO2,
    )

    private val ancControlModels = setOf(
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.AIRPODS_MAX,
        PodModel.AIRPODS_MAX_USBC,
        PodModel.AIRPODS_MAX2,
        PodModel.BEATS_SOLO_PRO,
        PodModel.BEATS_STUDIO_3,
        PodModel.BEATS_STUDIO_BUDS,
        PodModel.BEATS_STUDIO_BUDS_PLUS,
        PodModel.BEATS_STUDIO_PRO,
        PodModel.POWERBEATS_PRO2,
        PodModel.BEATS_FIT_PRO,
        PodModel.FAKE_AIRPODS_PRO,
        PodModel.FAKE_AIRPODS_PRO2,
    )

    private val adaptiveAncModels = setOf(
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.AIRPODS_MAX2,
    )

    private val conversationAwarenessModels = setOf(
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.AIRPODS_MAX2,
    )

    private val ncOneAirpodModels = setOf(
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
    )

    private val pressSpeedModels = setOf(
        PodModel.AIRPODS_GEN3,
        PodModel.AIRPODS_GEN4,
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.AIRPODS_MAX,
        PodModel.AIRPODS_MAX_USBC,
        PodModel.AIRPODS_MAX2,
    )

    private val volumeSwipeModels = setOf(
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
    )

    private val personalizedVolumeModels = setOf(
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.AIRPODS_MAX2,
    )

    private val toneVolumeModels = setOf(
        PodModel.AIRPODS_GEN3,
        PodModel.AIRPODS_GEN4,
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.AIRPODS_MAX,
        PodModel.AIRPODS_MAX_USBC,
        PodModel.AIRPODS_MAX2,
    )

    private val endCallMuteMicModels = setOf(
        PodModel.AIRPODS_GEN3,
        PodModel.AIRPODS_GEN4,
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
    )

    private val adaptiveAudioNoiseModels = setOf(
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.AIRPODS_MAX2,
    )

    private val microphoneModeModels = setOf(
        PodModel.AIRPODS_GEN1,
        PodModel.AIRPODS_GEN2,
        PodModel.AIRPODS_GEN3,
        PodModel.AIRPODS_GEN4,
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.POWERBEATS_PRO2,
        PodModel.BEATS_FIT_PRO,
    )

    private val earDetectionToggleModels = setOf(
        PodModel.AIRPODS_GEN1,
        PodModel.AIRPODS_GEN2,
        PodModel.AIRPODS_GEN3,
        PodModel.AIRPODS_GEN4,
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.AIRPODS_MAX,
        PodModel.AIRPODS_MAX_USBC,
        PodModel.AIRPODS_MAX2,
        PodModel.POWERBEATS_PRO,
        PodModel.POWERBEATS_PRO2,
        PodModel.BEATS_FIT_PRO,
    )

    private val listeningModeCycleModels = setOf(
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.AIRPODS_MAX,
        PodModel.AIRPODS_MAX_USBC,
        PodModel.AIRPODS_MAX2,
    )

    private val stemConfigModels = setOf(
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
    )

    private val sleepDetectionModels = setOf(
        PodModel.AIRPODS_GEN4,
        PodModel.AIRPODS_GEN4_ANC,
        PodModel.AIRPODS_PRO2,
        PodModel.AIRPODS_PRO2_USBC,
        PodModel.AIRPODS_PRO3,
        PodModel.POWERBEATS_PRO2,
    )

    private val featureExpectations = listOf(
        feature("hasDualPods", { it.hasDualPods }, dualPodModels),
        feature("hasCase", { it.hasCase }, dualPodModels),
        feature("hasEarDetection", { it.hasEarDetection }, earDetectionModels),
        feature("hasAncControl", { it.hasAncControl }, ancControlModels),
        feature("hasAdaptiveAnc", { it.hasAdaptiveAnc }, adaptiveAncModels),
        feature("hasConversationAwareness", { it.hasConversationAwareness }, conversationAwarenessModels),
        feature("hasNcOneAirpod", { it.hasNcOneAirpod }, ncOneAirpodModels),
        feature("hasPressSpeed", { it.hasPressSpeed }, pressSpeedModels),
        feature("hasPressHoldDuration", { it.hasPressHoldDuration }, pressSpeedModels),
        feature("hasVolumeSwipe", { it.hasVolumeSwipe }, volumeSwipeModels),
        feature("hasVolumeSwipeLength", { it.hasVolumeSwipeLength }, volumeSwipeModels),
        feature("hasPersonalizedVolume", { it.hasPersonalizedVolume }, personalizedVolumeModels),
        feature("hasToneVolume", { it.hasToneVolume }, toneVolumeModels),
        feature("hasEndCallMuteMic", { it.hasEndCallMuteMic }, endCallMuteMicModels),
        feature("hasAdaptiveAudioNoise", { it.hasAdaptiveAudioNoise }, adaptiveAudioNoiseModels),
        feature("hasMicrophoneMode", { it.hasMicrophoneMode }, microphoneModeModels),
        feature("hasEarDetectionToggle", { it.hasEarDetectionToggle }, earDetectionToggleModels),
        feature("hasListeningModeCycle", { it.hasListeningModeCycle }, listeningModeCycleModels),
        feature("hasAllowOffOption", { it.hasAllowOffOption }, listeningModeCycleModels),
        feature("hasStemConfig", { it.hasStemConfig }, stemConfigModels),
        feature("hasSleepDetection", { it.hasSleepDetection }, sleepDetectionModels),
        feature("hasDynamicEndOfCharge", { it.hasDynamicEndOfCharge }, setOf(PodModel.AIRPODS_PRO3)),
    )
}
