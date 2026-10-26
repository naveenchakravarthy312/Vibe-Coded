package com.example

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.ui.ScratchpadUiState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Scratchpad", appName)
  }

  @Test
  fun `test scratchpad word count logic`() {
    val state = ScratchpadUiState(
      textFieldValue = TextFieldValue("Hello world from Linux scratchpad on Android")
    )
    assertEquals(7, state.wordCount)
    assertEquals(44, state.charCount)
    assertEquals(1, state.lineCount)
  }
}
