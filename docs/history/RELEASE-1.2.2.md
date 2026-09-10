# catgo-gpt 1.2.2 手机终端输入修复

版本：1.2.2，versionCode 14。安装包：catgo-gpt-v1.2.2-debug.apk。
Debug 签名与上一版一致，可覆盖安装；不会清除服务器配置。

## 修改

- SSH / Hermes 交互终端使用原生 Android InputConnection，不再依赖隐藏网页 textarea 的软键盘事件。
- 创建 WebView 时就使用当前界面 Context，脱离界面后仍释放 Activity 引用。
- 原生编辑器可在触屏模式取得焦点；点击终端或键盘按钮后，等待窗口焦点再唤起输入法。
- 如果仍显示之前登录输入框的键盘，键盘按钮先将输入焦点切到终端。
- 中文 composing 候选留在本地，确认后发送一次；处理 commitText、finishComposingText、
  退格/Delete、IME 发送/回车和常用硬件控制键。
- 使用不个性化学习、非全屏提取等输入法标记；关闭输入连接不自动提交候选或密码。
- 断线、重建输入连接、窗口失焦/移除后撤销旧 IME 回调。原生提交携带终端 epoch，
  JavaScript 和发送层仍校验当前连接，防止过期输入送入另一个会话。
- 修正本地模拟 WebSocket 测试的关闭握手，避免清理阶段偶发超时。

## 验证

- 70 项 JVM 用例：69 通过、0 失败、1 项远程测试未启用。
- 其中 20 项为 Robolectric Android 框架回归：10 项分别在 SDK 28 / Android 9 和
  SDK 35 / Android 15 执行，覆盖原生输入、中文候选、回车、删除、旧连接撤销和编辑器声明。
- 15 项 Chromium 终端回归通过；新增原生 IME 模式下绕过隐藏 textarea、按键投递和 epoch 拒绝。
- Debug / Release 构建通过；lint 0 errors、35 warnings、1 hint。
- APK 签名已验证；Release 中 nativeIme / input 等 JavaScript 桥接方法名保留。

没有连接实体 Android 手机。Robolectric 验证 Android API 行为，Chromium 验证终端桥接，
均不能替代手机上实际输入法和 WebView 的端到端验收。

## 手机验收

覆盖安装后连接 SSH，点终端区域或“键盘”，依次检查：
1. 输入英文命令后回车，远端正常收到。
2. 中文拼音选词后只出现一次中文，没有拼音残留。
3. 退格、方向键、回车、Ctrl-C；键盘弹出/收起后仍能输入。
4. 断开/重联、锁屏恢复和旋转后不发送旧候选或旧命令。

参考：[Android BaseInputConnection](https://developer.android.com/reference/android/view/inputmethod/BaseInputConnection)。
