package eu.darken.capod.pods.core.apple

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.pods.core.apple.airpods.*
import eu.darken.capod.pods.core.apple.beats.*
import eu.darken.capod.pods.core.apple.misc.Twsi99999

@InstallIn(SingletonComponent::class)
@Module
abstract class AppleFactoryModule {

    @Binds @IntoSet abstract fun airPodsGen1(factory: AirPodsGen1.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun airPodsGen2(factory: AirPodsGen2.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun airPodsGen3(factory: AirPodsGen3.Factory): ApplePodsFactory<out ApplePods>

    @Binds @IntoSet abstract fun airPodsPro(factory: AirPodsPro.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun airPodsMax(factory: AirPodsMax.Factory): ApplePodsFactory<out ApplePods>

    @Binds @IntoSet abstract fun beatsFlex(factory: BeatsFlex.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun beatsSolo3(factory: BeatsSolo3.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun beatsStudio3(factory: BeatsStudio3.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun beatsX(factory: BeatsX.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun powerBeats3(factory: PowerBeats3.Factory): ApplePodsFactory<out ApplePods>
    @Binds @IntoSet abstract fun powerBeatsPro(factory: PowerBeatsPro.Factory): ApplePodsFactory<out ApplePods>

    @Binds @IntoSet abstract fun fakesTwsi999999(factory: Twsi99999.Factory): ApplePodsFactory<out ApplePods>
}