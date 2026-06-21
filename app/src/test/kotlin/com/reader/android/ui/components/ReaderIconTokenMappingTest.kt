package com.reader.android.ui.components

import com.reader.android.ui.appScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderIconTokenMappingTest {

    private val assetIconTokens: Set<String> by lazy {
        val source = String(Files.readAllBytes(Paths.get("..").resolve("docs/ui-design/frontend-input/asset-library/icons.js")))
        Regex("""^\s*(?:"([^"]+)"|([A-Za-z][A-Za-z0-9]*)):\s*'""", RegexOption.MULTILINE)
            .findAll(source)
            .map { match -> match.groupValues[1].ifBlank { match.groupValues[2] } }
            .toSet()
    }

    @Test
    fun `main tabs use semantic icon tokens`() {
        assertEquals(
            listOf(
                ReaderIconToken.Bookshelf,
                ReaderIconToken.Discover,
                ReaderIconToken.Rss,
                ReaderIconToken.Settings
            ),
            appScreens.map { it.iconToken }
        )
    }

    @Test
    fun `all declared icon tokens resolve to image vectors`() {
        ReaderIconToken.entries.forEach { token ->
            assertNotNull("${token.name} must resolve to an ImageVector", token.asImageVector())
        }
    }

    @Test
    fun `local asset library exposes current icon token inventory`() {
        assertEquals("local asset icon token count", 79, assetIconTokens.size)

        listOf(
            "chevron-left",
            "code",
            "current-location",
            "folder-off",
            "help",
            "night-mode",
            "permission",
            "stop"
        ).forEach { token ->
            assertTrue("asset library must expose supplemented token $token", token in assetIconTokens)
        }
    }

    @Test
    fun `compose semantic icon tokens trace to local asset tokens`() {
        val localTokenByComposeToken = mapOf(
            ReaderIconToken.Bookshelf to "bookshelf",
            ReaderIconToken.Discover to "discover",
            ReaderIconToken.Rss to "rss",
            ReaderIconToken.Settings to "settings",
            ReaderIconToken.Search to "search",
            ReaderIconToken.More to "more",
            ReaderIconToken.Back to "back",
            ReaderIconToken.Chevron to "chevron",
            ReaderIconToken.ChevronDown to "chevron",
            ReaderIconToken.ViewList to "list",
            ReaderIconToken.Grid to "grid",
            ReaderIconToken.Add to "add",
            ReaderIconToken.Delete to "trash",
            ReaderIconToken.FileOpen to "file",
            ReaderIconToken.Folder to "folder",
            ReaderIconToken.FolderOff to "folder-off",
            ReaderIconToken.Badge to "badge",
            ReaderIconToken.Warning to "warning",
            ReaderIconToken.Offline to "offline",
            ReaderIconToken.Permission to "permission",
            ReaderIconToken.Directory to "directory",
            ReaderIconToken.Refresh to "refresh",
            ReaderIconToken.SourceSwitch to "source",
            ReaderIconToken.AutoBrightness to "sun",
            ReaderIconToken.ChevronLeft to "chevron-left",
            ReaderIconToken.AutoScroll to "auto-page",
            ReaderIconToken.ContentReplace to "replace",
            ReaderIconToken.NightMode to "night-mode",
            ReaderIconToken.Tts to "tts",
            ReaderIconToken.Appearance to "appearance",
            ReaderIconToken.ReadingSettings to "settings",
            ReaderIconToken.Bookmark to "bookmark",
            ReaderIconToken.CurrentLocation to "current-location",
            ReaderIconToken.Close to "close",
            ReaderIconToken.Sort to "sort",
            ReaderIconToken.People to "people",
            ReaderIconToken.Clock to "clock",
            ReaderIconToken.List to "list",
            ReaderIconToken.Trash to "trash",
            ReaderIconToken.Bell to "bell",
            ReaderIconToken.Battery to "battery",
            ReaderIconToken.EyeOff to "eyeOff",
            ReaderIconToken.Info to "info",
            ReaderIconToken.Shield to "shield",
            ReaderIconToken.Bug to "bug",
            ReaderIconToken.Storage to "storage",
            ReaderIconToken.Download to "download",
            ReaderIconToken.Image to "image",
            ReaderIconToken.Check to "check",
            ReaderIconToken.Link to "link",
            ReaderIconToken.Message to "message",
            ReaderIconToken.Code to "code",
            ReaderIconToken.Help to "help",
            ReaderIconToken.Upload to "upload",
            ReaderIconToken.Edit to "edit"
        )

        assertEquals("every Compose icon token must have a local asset mapping", ReaderIconToken.entries.toSet(), localTokenByComposeToken.keys)
        localTokenByComposeToken.forEach { (composeToken, localToken) ->
            assertTrue("${composeToken.name} must trace to local asset token $localToken", localToken in assetIconTokens)
        }
    }

    @Test
    fun `settings secondary semantic icons are available`() {
        listOf(
            ReaderIconToken.Folder,
            ReaderIconToken.Badge,
            ReaderIconToken.Sort,
            ReaderIconToken.People,
            ReaderIconToken.Clock,
            ReaderIconToken.List,
            ReaderIconToken.Trash,
            ReaderIconToken.Bell,
            ReaderIconToken.Battery,
            ReaderIconToken.EyeOff,
            ReaderIconToken.Info,
            ReaderIconToken.Shield,
            ReaderIconToken.Bug,
            ReaderIconToken.Storage,
            ReaderIconToken.Download,
            ReaderIconToken.Image,
            ReaderIconToken.Check,
            ReaderIconToken.Link,
            ReaderIconToken.Message,
            ReaderIconToken.Code,
            ReaderIconToken.Help,
            ReaderIconToken.Upload,
            ReaderIconToken.Edit
        ).forEach { token ->
            assertNotNull("${token.name} must support settings secondary pages", token.asImageVector())
        }
    }
}
