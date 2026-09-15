"""Opt-in live browser reference; requires Playwright and a local Chrome binary."""
import os
import getpass
import re
import time
import uuid
from playwright.sync_api import sync_playwright

def main():
    base = os.environ['CATGO_WEB_BASE'].rstrip('/')
    username = os.environ['CATGO_LIVE_USER']
    password = getpass.getpass('Password: ')
    marker = 'CATGO_WEB_' + uuid.uuid4().hex[:8]
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=os.environ.get('CATGO_CHROME', '/usr/bin/google-chrome'), headless=True,
            args=['--no-proxy-server'])
        context = browser.new_context()
        response = context.request.post(base + '/auth/password-login', data={
            'provider': 'basic', 'username': username, 'password': password, 'next': '/chat'})
        del password
        assert response.ok, 'login failed'
        page = context.new_page()
        page.add_init_script('''window.testSockets = []; const OriginalWS = window.WebSocket;
          window.WebSocket = class extends OriginalWS {
            constructor(...args) { super(...args); if (String(args[0]).includes('/api/pty')) window.testSockets.push(this); }
          };''')
        routes = []
        page.route_web_socket(re.compile(r'.*/api/pty\?.*'), lambda ws: (routes.append(ws), ws.connect_to_server()))
        page.goto(base + '/chat')
        page.wait_for_selector('.xterm-helper-textarea', timeout=45000)
        candidates = page.get_by_role('button', name=re.compile(r'new (chat|session|conversation)', re.I))
        print('WEB new conversation buttons:', candidates.count(), flush=True)
        if candidates.count() != 1:
            print('WEB candidate labels:', page.locator('button').evaluate_all("els => els.map(e => [e.getAttribute('title'), e.getAttribute('aria-label')]).filter(a => a.some(v => v && /new|fresh/i.test(v)))"), flush=True)
            raise RuntimeError('Could not unambiguously select a new conversation; no message sent')
        candidates.click()
        page.wait_for_function('window.testSockets.some(s => s.readyState === 1)', timeout=45000)
        page.wait_for_timeout(3000)
        session = None
        def send(text):
            page.locator('.xterm-helper-textarea').focus()
            page.keyboard.type(text)
            page.wait_for_timeout(250)
            page.keyboard.press('Enter')
        def wait_reply(expected):
            nonlocal session
            deadline = time.monotonic() + 110
            while time.monotonic() < deadline:
                page.wait_for_timeout(1000)
                if session is None:
                    data = context.request.get(base + '/api/sessions?limit=50&order=recent').json()
                    session = next((s['id'] for s in data.get('sessions', []) if marker in (s.get('preview') or '')), None)
                if session:
                    messages = context.request.get(base + '/api/sessions/' + session + '/messages?limit=500&order=latest').json().get('messages', [])
                    if any(m.get('role') == 'assistant' and expected in str(m.get('content', '')) for m in messages):
                        return messages
            raise RuntimeError('No saved reference reply: ' + expected)
        send(marker + ' Reply only FIRST_OK. Do not use tools.')
        wait_reply('FIRST_OK')
        print('WEB initial saved reply PASS', flush=True)
        before = page.evaluate("(() => {const s=window.testSockets.filter(s=>s.readyState===1).at(-1); const channel=new URL(s.url).searchParams.get('channel'); return {count:window.testSockets.length,channel};})()")
        routes[-1].close(code=1001, reason='test network interruption')
        page.wait_for_function('(count) => window.testSockets.length > count && window.testSockets.at(-1).readyState === 1', arg=before['count'], timeout=45000)
        after = page.evaluate("new URL(window.testSockets.at(-1).url).searchParams.get('channel')")
        assert before['channel'] == after, 'reference channel changed'
        page.wait_for_timeout(2000)
        send(marker + ' Reply only SECOND_OK. Do not use tools.')
        messages = wait_reply('SECOND_OK')
        assert sum(m.get('role') == 'user' and marker in str(m.get('content', '')) for m in messages) == 2
        print('WEB reconnect same channel and second saved reply PASS; session=' + session, flush=True)
        browser.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Do not print request bodies, tokens, browser diagnostics or credentials.
        print("WEB reference failed: " + type(error).__name__, flush=True)
        raise SystemExit(1)
