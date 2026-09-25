package com.nova.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovaNavigationPolicyTest {
  private val tabs = setOf("chat", "agents", "activity", "settings")

  @Test
  fun imeOwnsBackBeforeTabNavigation() {
    assertFalse(shouldHandleTabBack("agents", tabs, imeVisible = true))
    assertTrue(shouldHandleTabBack("agents", tabs, imeVisible = false))
  }

  @Test
  fun modalChromeHidesKeyboardAndDrawer() {
    assertFalse(shouldShowBottomBar(imeVisible = true, drawerOpen = false))
    assertFalse(shouldShowBottomBar(imeVisible = false, drawerOpen = true))
    assertTrue(shouldShowBottomBar(imeVisible = false, drawerOpen = false))
  }
}
