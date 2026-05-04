// For IDE design preview, open ScreenshotPreviews.kt instead.
package eu.darken.capod.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PlayStoreLocales
@Composable
fun DashboardLight() = DashboardContent()

@PreviewTest
@PlayStoreLocalesDark
@Composable
fun DashboardDark() = DashboardContent(showAap = true)

@PreviewTest
@PlayStoreLocales
@Composable
fun CasePopUp() = CasePopUpContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun DeviceProfiles() = DeviceProfilesContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun AddProfile() = AddProfileContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun DeviceSettingsReactions() = DeviceSettingsReactionsContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun WidgetConfiguration() = WidgetConfigurationContent()
