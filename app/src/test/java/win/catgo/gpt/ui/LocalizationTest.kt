package win.catgo.gpt.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LocalizationTest {
    private fun catalog(path: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }
    @Test fun englishAndChineseHaveCompleteMatchingCatalogs() {
        val english = catalog("src/main/res/values/i18n.xml")
        val chinese = catalog("src/main/res/values-zh/i18n.xml")
        assertTrue(english.size >= 150)
        assertEquals(english.keys, chinese.keys)
        assertTrue(english.values.none { Regex("[\\u4e00-\\u9fff]").containsMatchIn(it) })
        assertTrue(english.values.all { it.isNotBlank() })
    }
    @Test fun appDeclaresBothLocalesAndRetainsSelection() {
        val locales = File("src/main/res/xml/locales_config.xml").readText()
        assertTrue(locales.contains("android:name=\"en\""))
        assertTrue(locales.contains("android:name=\"zh\""))
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("autoStoreLocales"))
    }
}
