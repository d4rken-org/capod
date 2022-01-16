package eu.darken.capod.common.upgrade

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.capod.common.upgrade.core.UpgradeControlFoss
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class UpgradeModule {
    @Binds
    @Singleton
    abstract fun control(foss: UpgradeControlFoss): UpgradeRepo

}