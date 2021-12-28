package eu.darken.cap.monitor.core

import dagger.BindsInstance
import dagger.hilt.DefineComponent
import dagger.hilt.components.SingletonComponent

@MonitorScope
@DefineComponent(parent = SingletonComponent::class)
interface MonitorComponent {

    @DefineComponent.Builder
    interface Builder {

        fun coroutineScope(@BindsInstance coroutineScope: MonitorCoroutineScope): Builder

        fun build(): MonitorComponent
    }
}