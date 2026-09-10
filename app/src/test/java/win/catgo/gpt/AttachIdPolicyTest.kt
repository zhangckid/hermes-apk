package win.catgo.gpt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AttachIdPolicyTest {
    @Test
    fun `fresh chat gets a new attach id`() {
        val selected = AttachIdPolicy.select("old", fresh = true) { "new" }

        assertEquals("new", selected)
        assertNotEquals("old", selected)
    }

    @Test
    fun `resumed chat keeps its attach id`() {
        var generated = false
        val selected = AttachIdPolicy.select("existing", fresh = false) {
            generated = true
            "new"
        }

        assertEquals("existing", selected)
        assertEquals(false, generated)
    }
}
