<div align="center">

# One UI X

## 释放三星 One UI 的无限可能 | Unleash the full potential of Samsung One UI

[![Stars](https://img.shields.io/github/stars/SoClear/OneUIX?style=for-the-badge&logo=github)](https://github.com/SoClear/OneUIX) [![Release](https://img.shields.io/github/v/release/SoClear/OneUIX?style=for-the-badge)](https://github.com/SoClear/OneUIX/releases/latest)

![LSPosed](https://img.shields.io/badge/Powered_by-LSPosed-blue?style=flat-square&logo=android)
[![License](https://img.shields.io/badge/License-AGPL%20v3-blue.svg?style=flat-square)](https://github.com/SoClear/OneUIX/blob/main/LICENSE.txt)
[![Min SDK](https://img.shields.io/badge/Min_SDK-33-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions)
[![Language](https://img.shields.io/github/languages/top/SoClear/OneUIX?style=flat-square&color=blueviolet&logo=kotlin)](https://github.com/SoClear/OneUIX)
[![GitHub Downloads](https://img.shields.io/github/downloads/SoClear/OneUIX/total?style=flat-square&color=blue&label=GitHub%20Downloads)](https://github.com/SoClear/OneUIX/releases)
[![LSPosed Downloads](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.soclear.oneuix/total?style=flat-square&color=orange&label=LSPosed%20Downloads)](https://github.com/Xposed-Modules-Repo/io.github.soclear.oneuix/releases)

<img src="./icon.svg" width="150" alt="One UI X Logo">

[简体中文](#简体中文) | [English](#english)

</div>

---

## 简体中文

**One UI X** 是一个专为三星设备设计的强大 LSPosed 模块。它旨在提供高度可定制的系统体验，解除烦人的限制，并为状态栏、快捷设置以及系统自带应用注入强大的增强功能。

### ✨ 功能

#### Android 系统

- 禁用每 72 小时验证锁屏密码
- 修改“保持打开”应用的最大数量
- 可以禁用系统的通知类别
- 支持应用跳转拦截

#### 系统 UI

##### 状态栏

- 修改状态栏左边距
- 修改状态栏右边距
- 隐藏状态栏电池的百分号
- 支持显示实时网速
- 状态栏显示上传下载网速
- 设置状态栏日期时间格式
- 状态栏时间每秒更新
- 隐藏安全文件夹状态栏图标
- 双击状态栏锁屏
- 修改状态栏最大通知图标数量

##### 快捷设置

- 快捷设置时间设为等宽字体
- 隐藏快捷设置的设备控制
- 开启快捷设置 5G 磁贴
- 隐藏快捷设置面板的媒体播放器 Bar
- 隐藏快捷设置面板的附近设备和设备控制 Bar
- 隐藏快捷设置面板的安全底部提示 Bar
- 隐藏快捷设置面板的 Smart View 和模式 Bar
- 总是展开快捷设置磁贴块
- 快捷设置面板总是显示时间日期
- 快捷设置面板显示亮度级别
- 快捷设置面板显示音量级别
- 快捷设置面板显示农历
- 修改快捷设置面板时钟字体大小

##### 息屏提醒

- 隐藏息屏提醒的状态栏
- 息屏提醒显示中国农历

##### 其他

- 禁用截图声音

#### 设置

- 开发者选项显示强制最大刷新率选项
- 显示更多电池信息
- 应用程序信息页面显示包信息
- Wi-Fi 列表页显示当前连接速度
- 支持任意字体
- 支持自动开/关机

#### 电话

- 支持录制通话
- 通话界面显示“录音”按钮而不是“添加通话”按钮
- 显示来电归属地
- 中国大陆样式的通话记录
- 支持跨设备接打电话和收发短信

#### 相机

- 显示相机的所有菜单
- 禁用相机过热检查

#### 其他

- 去除应用商店的广告
- 应用分身支持所有用户应用
- 天气源设为中国天气
- 最近任务页面显示内存信息
- 视频播放器添加 3.0 和 4.0 倍速
- 显示相册的所有设置
- 支持三星笔记的所有功能
- 日历中显示中国节假日
- 支持拦截短信
- 主题和图标试用永不过期
- 自定义浏览器搜索引擎（国行专属）
- 禁用图片编辑器 AI 水印
- 绕过 Samsung Health Monitor 国家检查

### 📦 安装要求

- 运行 Samsung One UI 5.0+ 的三星设备。
- Android 13+ (API 33+) 。
- 已获取 Root 权限 (Magisk / KernelSU / APatch)。
- 已安装并激活 **LSPosed** 框架。

### 🤝 参与贡献

欢迎提交 Pull Request 或开 Issue 讨论新功能与 Bug 修复！
由于 One UI 的更新可能会导致 Hook 失效，如果你发现了失效的 Hook 点，非常欢迎提交 PR 修复。

> 因个人原因，我将会在4月开始活跃开发新功能和修复bug

---

## English

**One UI X** is a powerful LSPosed module explicitly designed for Samsung devices. It aims to provide a highly customizable system experience, remove annoying restrictions, and inject powerful enhancements into the Status Bar, Quick Settings, and native apps.

### ✨ Features

#### Android System

- Disable PIN verification every 72 hours
- Modify max number of 'Keep open' apps
- Allow disabling system notification categories
- Support app jump blocking

#### System UI

##### Status Bar

- Modify status bar left padding
- Modify status bar right padding
- Hide battery percentage sign in status bar
- Support displaying real-time network speed
- Show separate upload/download speeds in status bar
- Set status bar date and time format
- Update status bar clock every second
- Hide Secure Folder status bar icon
- Double tap status bar to sleep
- Modify maximum number of notification icons in status bar

##### Quick Settings

- Set Quick Settings clock to monospaced font
- Hide Device Control in Quick Settings
- Enable 5G Quick Settings tile
- Hide Media Player bar in QS panel
- Hide Nearby Devices and Device Control bar in QS panel
- Hide Security footer bar in QS panel
- Hide Smart View and Modes bar in QS panel
- Always expand Quick Settings tile chunk
- Always show time and date in QS panel
- Show brightness level in QS panel
- Show volume level in QS panel
- Show Lunar calendar in QS panel
- Modify Quick Settings panel clock text size

##### Always On Display

- Hide AOD status bar
- Show Chinese Lunar calendar on AOD/Lock screen

##### Other

- Disable screenshot sound

#### Settings

- Show 'Force peak refresh rate' in Developer options
- Show more battery info
- Show package info on App Info page
- Show current link speed in Wi-Fi list
- Support any font
- Support Auto power on/off

#### Phone

- Support call recording
- Show 'Record' button instead of 'Add call' button on call screen
- Show caller location in recent calls
- Mainland China style call log
- Support call & text on other devices

#### Camera

- Show all camera menus
- Disable camera temperature check

#### Other

- Remove ads in Galaxy Store
- Make all user apps available for app cloning
- Set weather provider to China Weather
- Show memory usage in Recents page
- Add 3.0x and 4.0x playback speeds to video player
- Show all Gallery settings
- Support all Samsung Notes features
- Show Chinese holidays in Calendar
- Support message blocking
- Never expire theme and icon trials
- Customize browser search engine (Mainland China ROM exclusive)
- Disable Photo Editor AI watermark
- Bypass Samsung Health Monitor country check

### 📦 Installation Requirements

- Samsung devices running Samsung One UI 5.0+.
- Android 13+ (API 33+).
- Root access acquired (Magisk / KernelSU / APatch).
- **LSPosed** framework installed and activated.

### 🤝 Contributing

Feel free to submit Pull Requests or open Issues to discuss new features and bug fixes!
Since One UI updates may cause hooks to fail, if you find any invalid hook points, you are highly encouraged to submit a PR to fix them.

> Due to personal reasons, I will actively start developing new features and fixing bugs starting in April.

---

感谢 | Thanks

- [Firefds Kit](https://github.com/Firefds/FirefdsKit)
- [OneDesign](https://github.com/qlenlen/android_kernel_samsung_sm8550)
