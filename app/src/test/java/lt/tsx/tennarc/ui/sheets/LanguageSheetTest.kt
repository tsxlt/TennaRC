package lt.tsx.tennarc.ui.sheets

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi")
class LanguageSheetTest {
    @get:Rule val compose = createComposeRule()

    private fun show(phone: List<Locale>? = null) = compose.setContent {
        TennaTheme {
            ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                if (phone == null) LanguageSheetContent(onPicked = {}) else LanguageSheetContent(onPicked = {}, phoneLanguages = phone)
            }
        }
    }

    @Test fun opensOnThePhonesLanguages() {
        show()
        compose.waitForIdle()
        compose.onNodeWithText("System").assertExists()
        captureScreenRoboImage("build/shots/phone/30_language.png")
    }

    @Test fun aWordWithoutAccentsFindsTheLanguage() {
        show()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("cestina")
        compose.waitForIdle()
        compose.onNodeWithText("Čeština").assertExists()
        captureScreenRoboImage("build/shots/phone/31_language_search.png")
    }

    @Test fun systemIsThePhonesLanguageNotTheApps() {
        show(phone = listOf(Locale.GERMANY))
        compose.waitForIdle()
        compose.onNode(hasText("System") and hasText("Deutsch (Deutschland)")).assertExists()
        compose.onNode(hasText("System") and hasText("English (United States)")).assertDoesNotExist()
    }
}
