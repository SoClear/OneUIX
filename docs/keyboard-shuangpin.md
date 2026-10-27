# 三星键盘双拼布局实验（Issue #21）

## 范围

- 基于国际版三星键盘 5.9.40.48（594048300）静态分析。
- 新增“放开双拼布局（实验性）”开关，默认关闭。
- 仅针对 `shuangpin_qwerty` 绕过布局可用性过滤，不修改其他布局、CSC/地区、双拼方案菜单或输入引擎。
- DexKit 通过 `boolean (Language, String)` 签名和 `shuangpin_qwerty` 字符串定位过滤方法；该版本对应 `Vg.a.b`。结果必须唯一，缓存按宿主 versionCode 失效；不匹配则记录日志并跳过。
- 仅适配布局过滤，不代表已经验证候选词、上屏或搜狗多套双拼方案。

## 手动验证

1. 安装本分支生成的 OneUIX APK。
2. 在 Xposed 管理器中将 `com.samsung.android.honeyboard`（三星键盘）加入 OneUIX 作用域。
3. 在 OneUIX 的“三星键盘”分类中打开“放开双拼布局（实验性）”。
4. 保存设置后重启三星键盘进程（或重启设备）。
5. 检查模块日志中的 `Keyboard: Shuangpin layout filter hooked:`。若出现 `no unique Shuangpin layout filter found`，该版本未匹配，不应继续扩大 Hook 范围来掩盖问题。
6. 在三星键盘的“语言和类型”中，打开已启用的简体中文语言，检查双拼布局是否出现并可选择。此实验不会给语言包补写它未声明的布局。
7. 记录选择是否生效、输入是否生成正确候选词和上屏、重启后是否保留；同时确认普通拼音等其他布局行为未变。
8. 关闭开关并重启键盘，确认恢复原布局过滤。

双拼方案设置页仍可能隐藏，实际输入也可能依赖其他地区开关；这些是下一步排查项，本实验有意不处理。不要为验证本 Hook 而直接替换当前手机的系统键盘 APK。

## 本地验证

`:app:assembleDebug` / `:app:assembleRelease` 检查编译和打包；`:app:testDebugUnitTest` 覆盖默认关闭、旧配置读取以及开启后的序列化兼容性。单元测试不验证 Xposed 运行时行为，真机输入测试需单独执行。
