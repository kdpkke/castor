package io.github.stupside.castor.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun stoppedEngineDisablesCastingAndShowsStatus() {
    val model = MainScreenViewModel()
    model.connect(null)
    compose.setContent {
      io.github.stupside.castor.theme.CastorTheme {
        MainScreen(model, null, "https://example.org/watch", {})
      }
    }
    compose.onNodeWithText("Casting engine unavailable").assertExists()
    compose.onNodeWithText("https://example.org/watch").assertExists()
    compose.onNodeWithText("Cast video").assertIsNotEnabled()
  }

  @Test
  fun copiesTheEntireErrorToTheClipboard() {
    val error = "SSL routines::certificate verify failed\n" + "Long diagnostic details. ".repeat(80)
    val model = MainScreenViewModel()
    model.fail(IllegalStateException(error))
    compose.setContent {
      io.github.stupside.castor.theme.CastorTheme {
        MainScreen(model, null, "", {})
      }
    }
    compose.onNodeWithText("Copy error").performScrollTo().performClick()
    compose.waitForIdle()
    compose.runOnIdle {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
      assertEquals(error, clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
    }
  }
}
