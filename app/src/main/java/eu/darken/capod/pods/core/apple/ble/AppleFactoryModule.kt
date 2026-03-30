package eu.darken.capod.pods.core.apple.ble

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.pods.core.apple.ble.devices.ApplePodsFactory
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsGen1
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsGen2
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsGen3
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsGen4
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsGen4Anc
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsMax
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsMaxUsbc
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsPro
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsPro2
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsPro2Usbc
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsPro3
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsFitPro
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsFlex
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsSolo3
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsSolo4
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsSoloBuds
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsSoloPro
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsStudio3
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsStudioBuds
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsStudioBudsPlus
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsStudioPro
import eu.darken.capod.pods.core.apple.ble.devices.beats.BeatsX
import eu.darken.capod.pods.core.apple.ble.devices.beats.PowerBeats3
import eu.darken.capod.pods.core.apple.ble.devices.beats.PowerBeats4
import eu.darken.capod.pods.core.apple.ble.devices.beats.PowerBeatsPro
import eu.darken.capod.pods.core.apple.ble.devices.beats.PowerBeatsPro2
import eu.darken.capod.pods.core.apple.ble.devices.misc.FakeAirPodsGen1
import eu.darken.capod.pods.core.apple.ble.devices.misc.FakeAirPodsGen2
import eu.darken.capod.pods.core.apple.ble.devices.misc.FakeAirPodsGen3
import eu.darken.capod.pods.core.apple.ble.devices.misc.FakeAirPodsPro
import eu.darken.capod.pods.core.apple.ble.devices.misc.FakeAirPodsPro2

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
    @Binds @IntoSet abstract fun beatsSoloPro(factory: BeatsSoloPro.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun beatsSolo4(factory: BeatsSolo4.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun beatsSoloBuds(factory: BeatsSoloBuds.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun beatsStudioBuds(factory: BeatsStudioBuds.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun beatsStudioBudsPlus(factory: BeatsStudioBudsPlus.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun beatsStudioPro(factory: BeatsStudioPro.Factory): ApplePodsFactory

    @Binds @IntoSet abstract fun fakeAirPodsGen1(factory: FakeAirPodsGen1.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun fakeAirPodsGen2(factory: FakeAirPodsGen2.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun fakeAirPodsGen3(factory: FakeAirPodsGen3.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun fakeAirPodsPro(factory: FakeAirPodsPro.Factory): ApplePodsFactory
    @Binds @IntoSet abstract fun fakeAirPodsPro2(factory: FakeAirPodsPro2.Factory): ApplePodsFactory
}