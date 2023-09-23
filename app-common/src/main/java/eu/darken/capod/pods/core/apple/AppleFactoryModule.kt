package eu.darken.capod.pods.core.apple

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.pods.core.apple.airpods.*
import eu.darken.capod.pods.core.apple.beats.*
import eu.darken.capod.pods.core.apple.misc.*

@InstallIn(SingletonComponent::class)
@Module
abstract class AppleFactoryModule {

    @Binds @IntoSet abstract fun airPodsGen1(factory: AirPodsGen1.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun airPodsGen2(factory: AirPodsGen2.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun airPodsGen3(factory: AirPodsGen3.Factory): ApplePodsFactory<out ApplePods>

    @Binds @IntoSet abstract fun airPodsPro(factory: AirPodsPro.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun airPodsPro2(factory: AirPodsPro2.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun airPodsPro2Usbc(factory: AirPodsPro2Usbc.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun airPodsMax(factory: AirPodsMax.Factory): ApplePodsFactory<out ApplePods>

    @Binds @IntoSet abstract fun beatsFlex(factory: BeatsFlex.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun beatsSolo3(factory: BeatsSolo3.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun beatsStudio3(factory: BeatsStudio3.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun beatsX(factory: BeatsX.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun powerBeats3(factory: PowerBeats3.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun powerBeatsPro(factory: PowerBeatsPro.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun beatsFitPro(factory: BeatsFitPro.Factory): ApplePodsFactory<out ApplePods>

    @Binds @IntoSet abstract fun fakeAirPodsGen1(factory: FakeAirPodsGen1.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun fakeAirPodsGen2(factory: FakeAirPodsGen2.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun fakeAirPodsGen3(factory: FakeAirPodsGen3.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun fakeAirPodsPro(factory: FakeAirPodsPro.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun fakeAirPodsPro2(factory: FakeAirPodsPro2.Factory): ApplePodsFactory<out ApplePods>
}