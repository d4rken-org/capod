package eu.darken.capod.reaction.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.preferences.PreferenceStoreMapper
import eu.darken.capod.common.preferences.Settings
import eu.darken.capod.common.preferences.createFlowPreference
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReactionSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) : Settings() {

    override val preferences: SharedPreferences =
        context.getSharedPreferences("settings_reaction", Context.MODE_PRIVATE)

    val autoPause = preferences.createFlowPreference(
        "reaction.autopause.enabled",
        false
    )

    val autoPlay = preferences.createFlowPreference(
        "reaction.autoplay.enabled",
        false
    )

    val autoConnect = preferences.createFlowPreference(
        "reaction.autoconnect.enabled",
        false
    )

    val autoConnectCondition = preferences.createFlowPreference(
        "reaction.autoconnect.condition",
        AutoConnectCondition.WHEN_SEEN,
        moshi
    )

    val showPopUpOnCaseOpen = preferences.createFlowPreference(
        "reaction.popup.caseopen",
        false
    )

    val showPopUpOnConnection = preferences.createFlowPreference(
        "reaction.popup.connected",
        false
    )

    val onePodMode = preferences.createFlowPreference(
        "reaction.onepod.enabled",
        false
    )

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        autoPause,
        autoPlay,
        autoConnect,
        autoConnectCondition,
        showPopUpOnCaseOpen,
        showPopUpOnConnection,
        onePodMode,
    )
}