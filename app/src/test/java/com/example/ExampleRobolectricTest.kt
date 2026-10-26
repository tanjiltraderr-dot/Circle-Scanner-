package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.ToolCategory
import com.example.domain.model.ToolRegistry
import com.example.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Circle Scanner", appName)
    }

    @Test
    fun `verify tool registry contains categories and tools`() {
        val allTools = ToolRegistry.allTools
        assertTrue("Expected at least 25 tools, found ${allTools.size}", allTools.size >= 25)

        val categories = listOf(
            ToolCategory.SCANNER,
            ToolCategory.PDF,
            ToolCategory.QR_BARCODE,
            ToolCategory.IMAGE,
            ToolCategory.FILES,
            ToolCategory.FINANCE,
            ToolCategory.CALCULATOR,
            ToolCategory.PRODUCTIVITY,
            ToolCategory.SECURITY,
            ToolCategory.OTHER
        )

        for (cat in categories) {
            val count = ToolRegistry.getCategoryCount(cat)
            assertTrue("Expected category ${cat.title} to have tools", count > 0)
        }
    }

    @Test
    fun `verify screen routes exist`() {
        assertNotNull(Screen.Home.route)
        assertNotNull(Screen.Tools.route)
        assertNotNull(Screen.Scanner.route)
        assertNotNull(Screen.Pro.route)
        assertNotNull(Screen.Settings.route)
        assertEquals("category/{categoryId}", Screen.CategoryDetails.route)
    }
}
