package lt.tsx.tennarc.ui

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w385dp-h560dp-xxhdpi")
class SheetBackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = TvState(host = "192.168.1.70", connection = Connection.Connected, power = TvPower.ON)
    private val prefs = Prefs(autoKeyboard = false, tvName = "Living room")

    private fun show(requested: Sheet?) {
        compose.activity.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, prefs, NoRemoteActions, remember { SnackbarHostState() }, requestedSheet = requested)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pressBack() {
        compose.runOnUiThread { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun text(id: Int) = compose.activity.getString(id)

    private val languageRow: SemanticsNodeInteraction get() = compose.onNode(hasText(text(R.string.settings_language)) and hasClickAction())

    private val settings: SemanticsNodeInteraction get() = compose.onNode(hasText(text(R.string.settings_find)))

    private val languageSheet: SemanticsMatcher get() = isRoot() and hasAnyDescendant(isSelectable() and hasText(text(R.string.settings_theme_system)))

    private val languages: SemanticsNodeInteraction get() = compose.onNode(languageSheet)

    private val languageSearch: SemanticsNodeInteraction get() = compose.onNode(hasSetTextAction() and hasAnyAncestor(languageSheet))

    private fun openLanguages() {
        languageRow.assertIsNotDisplayed()
        languageRow.performScrollTo().performClick()
        compose.waitForIdle()
        languages.assertExists()
        settings.assertDoesNotExist()
    }

    @Test fun backFromLanguageGoesBackToSettings() {
        show(Sheet.SETTINGS)
        openLanguages()
        pressBack()
        languages.assertDoesNotExist()
        settings.assertExists()
        languageRow.assertIsDisplayed()
        pressBack()
        settings.assertDoesNotExist()
    }

    @Test fun backFromAddATvGoesBackToSettings() {
        show(Sheet.SETTINGS)
        compose.onNode(hasText(text(R.string.settings_find)) and hasClickAction()).performClick()
        compose.waitForIdle()
        settings.assertDoesNotExist()
        pressBack()
        settings.assertExists()
    }

    @Test fun aLanguageOpenedOnItsOwnClosesOnBack() {
        show(Sheet.LANGUAGE)
        languages.assertExists()
        pressBack()
        languages.assertDoesNotExist()
        settings.assertDoesNotExist()
    }

    private fun pickGerman() {
        languageSearch.performTextInput("deutsch")
        compose.waitForIdle()
        compose.onAllNodes(hasText("Deutsch") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test fun aLanguagePickedLandsBackOnSettings() {
        show(Sheet.SETTINGS)
        openLanguages()
        pickGerman()
        languages.assertDoesNotExist()
        settings.assertExists()
        languageRow.assertIsDisplayed()
    }

    @Test fun theSheetAndTheWayBackOutlastTheActivityStartingAgain() {
        show(Sheet.SETTINGS)
        openLanguages()
        compose.activityRule.scenario.recreate()
        show(null)
        languages.assertExists()
        pressBack()
        settings.assertExists()
        languageRow.assertIsDisplayed()
    }

    @Test fun aNewLanguageStartsAgainOnSettingsAtItsRow() {
        show(Sheet.SETTINGS)
        openLanguages()
        pickGerman()
        RuntimeEnvironment.setQualifiers("+de")
        compose.activityRule.scenario.recreate()
        show(null)
        settings.assertExists()
        compose.onNode(hasText("Sprache") and hasClickAction()).assertIsDisplayed()
    }
}
