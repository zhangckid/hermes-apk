"""Generate VT snapshots using the same rendering library as Hermes CLI.

Run in a venv with prompt_toolkit==3.0.52. This is a local fixture, not Hermes
server access. It emits JSON on stdout and never executes approval commands.
"""
import asyncio
import io
import json
from prompt_toolkit.application import Application
from prompt_toolkit.input import create_pipe_input
from prompt_toolkit.layout import Layout, HSplit, Window
from prompt_toolkit.layout.controls import FormattedTextControl, BufferControl
from prompt_toolkit.buffer import Buffer
from prompt_toolkit.output.vt100 import Vt100_Output
from prompt_toolkit.data_structures import Size
from prompt_toolkit.utils import get_cwidth


def box(title, body):
    width = 66
    rows = ["╭" + "─" * width + "╮"]
    for line in [title, ""] + body:
        rows.append("│ " + line + " " * max(0, width - get_cwidth(line) - 1) + "│")
    return "\n".join(rows + ["╰" + "─" * width + "╯"])


async def capture(title, body, hint):
    stream = io.StringIO()
    with create_pipe_input() as pipe:
        output = Vt100_Output(stream, lambda: Size(rows=32, columns=90), term="xterm-256color", enable_cpr=False)
        content = [box(title, body)]
        control = FormattedTextControl(lambda: [("fg:#ffd700", content[0])])
        editor = BufferControl(buffer=Buffer())
        app = Application(layout=Layout(HSplit([Window(control), Window(FormattedTextControl(hint), height=1),
            Window(editor, height=1)]), focused_element=editor), input=pipe, output=output, full_screen=False)
        snapshots = []

        async def sample():
            await asyncio.sleep(0.15)
            snapshots.append(stream.getvalue())
            # Incremental repaint must overwrite the old CJK text, not append a duplicate panel.
            content[0] = box(title, [line.replace("部署到哪里", "使用哪个环境") for line in body])
            app.invalidate()
            await asyncio.sleep(0.15)
            snapshots.append(stream.getvalue())
            app.exit()

        await app.run_async(pre_run=lambda: app.create_background_task(sample()))
        return snapshots


async def main():
    cases = {}
    cases["question"] = await capture("Hermes needs your input", ["请问部署到哪里？", "", "❯ 1. 测试环境", "  2. 生产环境", "  3. Other (type your answer)"],
        "↑/↓ to select, Enter to confirm (300s)")
    cases["approval"] = await capture("⚠️  Dangerous Command", ["sudo systemctl status hermes", "", "❯ 1. Allow once", "  2. Deny"],
        "↑/↓ to select, Enter to confirm (300s)")
    cases["secret"] = await capture("🔐 Sudo Password Required", ["Enter password below (hidden), or press Enter to skip"],
        "password hidden · Enter to skip (45s)")
    print(json.dumps(cases, ensure_ascii=True))


if __name__ == "__main__":
    asyncio.run(main())
