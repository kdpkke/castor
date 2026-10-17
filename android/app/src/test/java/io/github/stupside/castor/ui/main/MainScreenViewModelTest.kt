package io.github.stupside.castor.ui.main

import castor.v1.DeviceOuterClass.Target
import org.junit.Assert.*
import org.junit.Test

class MainScreenViewModelTest {
  private val target = Target.newBuilder().setDeviceId("living-room").build()

  @Test
  fun pageAndDirectStreamHaveDifferentContractSources() {
    val page = MainScreenViewModel.request("https://example.org/watch", true, target, "720")
    val direct =
      MainScreenViewModel.request("https://example.org/video.m3u8", false, target, "2160")
    assertEquals(listOf("https://example.org/watch"), page.source.pages.urlsList)
    assertFalse(page.source.hasStream())
    assertEquals("https://example.org/video.m3u8", direct.source.stream.url)
    assertFalse(direct.source.hasPages())
    assertEquals(2160, direct.preferences.maxHeight)
    assertEquals(target, direct.target)
  }

  @Test
  fun invalidInputsAreRejectedWithoutBeingRewritten() {
    for (url in
      listOf(
        "file:///sdcard/movie.mp4",
        "javascript:alert(1)",
        "https://u:p@example.org/video",
        "example.org",
      )) {
      assertThrows(IllegalArgumentException::class.java) {
        MainScreenViewModel.request(url, false, target, "1080")
      }
    }
    for (height in listOf("", "0", "1", "1080.5", "overflow")) {
      assertThrows(IllegalArgumentException::class.java) {
        MainScreenViewModel.request("https://example.org", false, target, height)
      }
    }
    assertThrows(IllegalArgumentException::class.java) {
      MainScreenViewModel.request("https://example.org", true, Target.getDefaultInstance(), "1080")
    }
  }

  @Test
  fun subtitleModesStayDistinctAtTheContract() {
    fun selection(value: String) =
      MainScreenViewModel.request("https://example.org/watch", true, target, "720", value)
        .preferences
        .subtitles
    assertTrue(selection("off").hasDisabled())
    assertTrue(selection("auto").hasAutoDetect())
    assertEquals("it", selection("it").language)
    assertThrows(IllegalArgumentException::class.java) { selection("italian") }
  }
}
