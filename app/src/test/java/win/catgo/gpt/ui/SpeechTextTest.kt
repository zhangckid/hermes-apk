package win.catgo.gpt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test
    fun `markdown is normalized for speech`() {
        val spoken = SpeechText.fromMarkdown("## 结果\n**完成** [详情](https://example.com)\n```kotlin\nprintln(1)\n```")

        assertEquals("结果 完成 详情 代码内容已省略。", spoken)
    }

    @Test
    fun `long speech is split without losing text`() {
        val chunks = SpeechText.chunks("第一句很长。第二句也很长。第三句结束。", 10)

        assertTrue(chunks.all { it.length <= 10 })
        assertEquals("第一句很长。第二句也很长。第三句结束。", chunks.joinToString(""))
    }
}
