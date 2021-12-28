package eu.darken.cap.common.dagger

import android.app.Application
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AndroidModule {

    @Provides
    @Singleton
    fun context(app: Application): Context = app.applicationContext

    @Provides
    @Singleton
    fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Provides
    @Singleton
    fun bluetoothManager(context: Context): BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    @Provides
    @Singleton
    fun workerManager(context: Context): WorkManager =
        WorkManager.getInstance(context)
}
