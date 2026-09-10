# catgo-gpt 1.2.3 手机输入与交互终端恢复

版本：1.2.3-debug，versionCode 15。安装包：catgo-gpt-v1.2.3-debug.apk。
已核验与 1.2.2-debug 使用同一签名，可直接覆盖安装；不需要卸载或清空服务器配置。

## 修改

- SSH 与 Hermes 交互页新增可见的原生“命令或回答”输入框，键盘按钮定位到该输入框。
  中文选词在本地完成，点击“发送并回车”或输入法发送键才提交整行。
  文字和回车作为一个 UTF-8 数据项送入当前连接的有序队列，不依赖网页文字输入桥接。
- 原生输入不保存草稿历史或实例状态；断开或切换终端后清空，拒绝旧终端的延迟提交。
  发送失败不自动重发。单行输入拒绝换行、控制字符和超过 16 KB 的内容。
- 可选择隐藏密码；Hermes 密码请求默认隐藏，并设置密码辅助功能标记。
  原有 ANSI/zsh 全屏渲染及远端方向键、Tab、Ctrl-C 等保留。
- 原 WebView InputConnection 明确使用 UI Looper，避免输入回调线程与终端 UI 线程不一致。
- 渲染队列改为按字节限流（256 KiB），合并连续碎片为最多 32 KiB 的批次，保留
  reset、历史回放完成通知和实时输出的顺序。原先 64 个碎片的限制会被密集的小更新耗尽。
- 忽略两种标准 ResizeObserver 尺寸反馈通知，其他脚本错误仍报告；终端就绪不再等待字体，
  字体加载缓慢或失败时先使用备用字体。缺少 ResizeObserver 时使用窗口 resize。
- 关闭 Hermes 交互页后停止离屏渲染，重新打开从内存历史恢复；聊天连接与回答轮询保留。
  终端失败留在原页，显示错误类别和 WebView 版本，可“重试加载终端”。重试不发送回答或授权。
- 等待区过滤 voice off / session 数字页脚碎片。SSH 页展示 app 版本号。

## 验证

- 103 项 JVM/Android 框架用例：102 通过、0 失败、1 项远程服务测试未启用。
- 包含 SDK 28 / Android 9 和 SDK 35 / Android 15 的 38 项 Robolectric 框架用例：
  22 项 InputConnection、12 项原生输入 UI、4 项交互失败/重试 UI。
- 新增本地 SSH 服务往返：原生行输入的中文、emoji 和回车字节完整送达并返回。
- 7 项渲染队列用例覆盖一万条碎片、回放/实时输出顺序、限流、背压和字节所有权。
- 18 项 Chromium 终端用例通过，包括真实本地 zsh PTY、中文、控制键、授权面板、
  ResizeObserver 通知、脚本失败和字体加载停滞。
- APK 签名与 1.2.2-debug 一致。

没有连接实体 Android 手机，也未访问用户的远程 SSH 或 Hermes 服务。
这些测试不能代替用户手机上 Gboard + 系统 WebView 的端到端验收，无法据此断言该设备的所有原因都已排除。

## 手机验收

1. 覆盖安装，SSH 页应显示 catgo-gpt 1.2.3-debug。
2. 连接 SSH，点击底部“命令或回答”，用 Gboard 输入 `echo 中文`，点击“发送并回车”。
3. 测试输入法发送键、收起再展开键盘；不点击发送时不应执行输入框中的内容。
4. Hermes 提问时先核对终端里的完整问题；文字回答用底部输入框，选项用方向键和 Enter。
   密码请求默认隐藏，任何回答或授权都需要手动操作。
5. 交互终端失败时，点击原页“重试加载终端”；若仍失败，反馈错误类别和页面上的 WebView 版本。

APK SHA-256：

`62aea258a7cc61869f379b80e53522d96858e674456f2b762d5ce9777a236ba2`

线程依据：[Android InputConnection.getHandler](https://developer.android.com/reference/android/view/inputmethod/InputConnection#getHandler())。
