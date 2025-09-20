package eu.darken.capod.pods.core.apple

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.pods.core.apple.airpods.AirPodsGen1
import eu.darken.capod.pods.core.apple.airpods.AirPodsGen2
import eu.darken.capod.pods.core.apple.airpods.AirPodsGen3
import eu.darken.capod.pods.core.apple.airpods.AirPodsGen4
import eu.darken.capod.pods.core.apple.airpods.AirPodsGen4Anc
import eu.darken.capod.pods.core.apple.airpods.AirPodsMax
import eu.darken.capod.pods.core.apple.airpods.AirPodsMaxUsbc
import eu.darken.capod.pods.core.apple.airpods.AirPodsPro
import eu.darken.capod.pods.core.apple.airpods.AirPodsPro2
import eu.darken.capod.pods.core.apple.airpods.AirPodsPro2Usbc
import eu.darken.capod.pods.core.apple.airpods.AirPodsPro3
import eu.darken.capod.pods.core.apple.beats.BeatsFitPro
import eu.darken.capod.pods.core.apple.beats.BeatsFlex
import eu.darken.capod.pods.core.apple.beats.BeatsSolo3
import eu.darken.capod.pods.core.apple.beats.BeatsStudio3
import eu.darken.capod.pods.core.apple.beats.BeatsX
import eu.darken.capod.pods.core.apple.beats.PowerBeats3
import eu.darken.capod.pods.core.apple.beats.PowerBeats4
import eu.darken.capod.pods.core.apple.beats.PowerBeatsPro
import eu.darken.capod.pods.core.apple.beats.PowerBeatsPro2
import eu.darken.capod.pods.core.apple.misc.FakeAirPodsGen1
import eu.darken.capod.pods.core.apple.misc.FakeAirPodsGen2
import eu.darken.capod.pods.core.apple.misc.FakeAirPodsGen3
import eu.darken.capod.pods.core.apple.misc.FakeAirPodsPro
import eu.darken.capod.pods.core.apple.misc.FakeAirPodsPro2

@InstallIn(SingletonComponent::class)
@Module
abstract class AppleFactoryModule {

    @Binds @IntoSet abstract fun airPodsGen1(factory: AirPodsGen1.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun airPodsGen2(factory: AirPodsGen2.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun airPodsGen3(factory: AirPodsGen3.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun airPodsGen4(factory: AirPodsGen4.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun airPodsGen4Anc(factory: AirPodsGen4Anc.Factory): ApplePodsFactory

    @Binds @IntoSet abstract fun airPodsPro(factory: AirPodsPro.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun airPodsPro2(factory: AirPodsPro2.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun airPodsPro2Usbc(factory: AirPodsPro2Usbc.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun airPodsPro3(factory: AirPodsPro3.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun airPodsMax(factory: AirPodsMax.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun airPodsMax2(factory: AirPodsMaxUsbc.Factory): ApplePodsFactory

    @Binds @IntoSet abstract fun beatsFlex(factory: BeatsFlex.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun beatsSolo3(factory: BeatsSolo3.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun beatsStudio3(factory: BeatsStudio3.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun beatsX(factory: BeatsX.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun powerBeats3(factory: PowerBeats3.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun powerBeats4(factory: PowerBeats4.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun powerBeatsPro(factory: PowerBeatsPro.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun powerBeatsPro2(factory: PowerBeatsPro2.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun beatsFitPro(factory: BeatsFitPro.Factory): ApplePodsFactory

    @Binds @IntoSet abstract fun fakeAirPodsGen1(factory: FakeAirPodsGen1.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun fakeAirPodsGen2(factory: FakeAirPodsGen2.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun fakeAirPodsGen3(factory: FakeAirPodsGen3.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun fakeAirPodsPro(factory: FakeAirPodsPro.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun fakeAirPodsPro2(factory: FakeAirPodsPro2.Factory): ApplePodsFactory
}