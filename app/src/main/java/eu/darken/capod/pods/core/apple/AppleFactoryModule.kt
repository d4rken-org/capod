package eu.darken.capod.pods.core.apple

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.pods.core.apple.airpods.*
import eu.darken.capod.pods.core.apple.beats.*

@InstallIn(SingletonComponent::class)
@Module
abstract class AppleFactoryModule {

    @Binds @IntoSet abstract fun airPodsGen1(factory: AirPodsGen1.Factory): ApplePods.Factory
    @Binds @IntoSet abstract fun airPodsGen2(factory: AirPodsGen2.Factory): ApplePods.Factory
    @Binds @IntoSet abstract fun airPodsGen3(factory: AirPodsGen3.Factory): ApplePods.Factory

    @Binds @IntoSet abstract fun airPodsPro(factory: AirPodsPro.Factory): ApplePods.Factory
    @Binds @IntoSet abstract fun airPodsMax(factory: AirPodsMax.Factory): ApplePods.Factory

    @Binds @IntoSet abstract fun beatsFlex(factory: BeatsFlex.Factory): ApplePods.Factory
    @Binds @IntoSet abstract fun beatsSolo3(factory: BeatsSolo3.Factory): ApplePods.Factory
    @Binds @IntoSet abstract fun beatsStudio3(factory: BeatsStudio3.Factory): ApplePods.Factory
    @Binds @IntoSet abstract fun beatsX(factory: BeatsX.Factory): ApplePods.Factory
    @Binds @IntoSet abstract fun powerBeats3(factory: PowerBeats3.Factory): ApplePods.Factory
    @Binds @IntoSet abstract fun powerBeatsPro(factory: PowerBeatsPro.Factory): ApplePods.Factory
}