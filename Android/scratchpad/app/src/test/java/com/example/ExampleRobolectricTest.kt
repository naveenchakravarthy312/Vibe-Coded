package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.ui.ScratchpadUiState
import com.example.ui.ScratchpadViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    assertEquals(0, ScratchpadUiState(textFieldValue = TextFieldValue("   \n\t  ")).wordCount)
  }

  @Test
  fun `undo groups typing by word boundaries instead of per character`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = ScratchpadViewModel(app)
    var now = 1000L
    vm.clockMs = { now }
    vm.newScratchpad()

    // Type a 60+ character sentence one character at a time (would exhaust a 50-char stack previously)
    val sentence = "The quick brown fox jumps over the lazy dog and keeps running fast"
    var acc = ""
    for (ch in sentence) {
      acc += ch
      now += 40L
      vm.onTextChanged(TextFieldValue(acc, selection = TextRange(acc.length)))
    }

    assertTrue(vm.uiState.value.canUndo)
    // First undo should revert the last word ("fast"), not just the last letter ('t')
    vm.undo()
    assertEquals(
      "The quick brown fox jumps over the lazy dog and keeps running ",
      vm.uiState.value.textFieldValue.text
    )
    assertTrue(vm.uiState.value.canRedo)

    // Redo restores the last word
    vm.redo()
    assertEquals(sentence, vm.uiState.value.textFieldValue.text)
    assertFalse(vm.uiState.value.canRedo)

    // Undoing all word groups should reach the original empty string cleanly
    var steps = 0
    while (vm.uiState.value.canUndo && steps < 50) {
      vm.undo()
      steps++
    }
    assertEquals("", vm.uiState.value.textFieldValue.text)
    assertTrue("Expected ~12 word-level undo steps, got $steps", steps in 10..15)
  }

  @Test
  fun `find and replace advances past overlapping replacement and escapes dollar signs`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = ScratchpadViewModel(app)
    vm.newScratchpad()
    vm.onTextChanged(TextFieldValue("cat and cat", selection = TextRange(0)), forceUndoSnapshot = true)

    vm.toggleFindReplace(true)
    vm.setFindQuery("cat")
    vm.setReplaceQuery("category")

    vm.findNext()
    assertEquals(1, vm.uiState.value.currentMatchIndex)
    assertEquals(TextRange(0, 3), vm.uiState.value.textFieldValue.selection)

    // Replace match 1 -> must advance to match 2 at (13, 16), not loop on index 0
    vm.replaceCurrent()
    assertEquals("category and cat", vm.uiState.value.textFieldValue.text)
    assertEquals(2, vm.uiState.value.currentMatchIndex)
    assertEquals(TextRange(13, 16), vm.uiState.value.textFieldValue.selection)

    // Replace match 2 -> both replaced cleanly
    vm.replaceCurrent()
    assertEquals("category and category", vm.uiState.value.textFieldValue.text)

    // Replace all with literal '$100' should not crash on regex group syntax
    vm.setFindQuery("category")
    vm.setReplaceQuery("\$100")
    vm.replaceAll()
    assertEquals("\$100 and \$100", vm.uiState.value.textFieldValue.text)
  }
}
