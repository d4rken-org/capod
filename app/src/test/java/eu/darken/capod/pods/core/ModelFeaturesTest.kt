package eu.darken.capod.pods.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ModelFeaturesTest : BaseTest() {

    @Test
    fun `AirPods Pro 3 has all features`() {
        val f = PodDevice.Model.AIRPODS_PRO3.features
        f.hasDualPods shouldBe true
        f.hasCase shouldBe true
        f.hasEarDetection shouldBe true
        f.hasAncControl shouldBe true
    }

    @Test
    fun `AirPods Gen 1 has dual pods and case but no ear detection or ANC`() {
        val f = PodDevice.Model.AIRPODS_GEN1.features
        f.hasDualPods shouldBe true
        f.hasCase shouldBe true
        f.hasEarDetection shouldBe false
        f.hasAncControl shouldBe false
    }

    @Test
    fun `AirPods Max is single device with ANC but no dual pods or case`() {
        val f = PodDevice.Model.AIRPODS_MAX.features
        f.hasDualPods shouldBe false
        f.hasCase shouldBe false
        f.hasEarDetection shouldBe false
        f.hasAncControl shouldBe true
    }

    @Test
    fun `Beats Solo 3 has no features`() {
        val f = PodDevice.Model.BEATS_SOLO_3.features
        f.hasDualPods shouldBe false
        f.hasCase shouldBe false
        f.hasEarDetection shouldBe false
        f.hasAncControl shouldBe false
    }

    @Test
    fun `PowerBeats Pro has dual pods, case, ear detection but no ANC`() {
        val f = PodDevice.Model.POWERBEATS_PRO.features
        f.hasDualPods shouldBe true
        f.hasCase shouldBe true
        f.hasEarDetection shouldBe true
        f.hasAncControl shouldBe false
    }

    @Test
    fun `UNKNOWN model has no features`() {
        val f = PodDevice.Model.UNKNOWN.features
        f.hasDualPods shouldBe false
        f.hasCase shouldBe false
        f.hasEarDetection shouldBe false
        f.hasAncControl shouldBe false
    }

    @Test
    fun `all ANC-capable models also have ear detection or are headphones`() {
        PodDevice.Model.entries
            .filter { it.features.hasAncControl && it.features.hasDualPods }
            .forEach { model ->
                model.features.hasEarDetection shouldBe true
            }
    }

    @Test
    fun `all models with case also have dual pods`() {
        PodDevice.Model.entries
            .filter { it.features.hasCase }
            .forEach { model ->
                model.features.hasDualPods shouldBe true
            }
    }
}
