# Cesia 输入法

> **AI 驱动的 Android 输入法 — 语音润色 · 魔法修改 · 双拼/T9 · 全键盘**

<p align="center">
  <b>主人 (@harvie) 出品 · Hermes Agent 协助开发</b>
</p>

---

## 📖 简介

Cesia 是一款面向中文用户的 Android 输入法，融合了 AI 语音润色、魔法指令修改、T9 九宫格、全键盘双拼/拼音等多种输入方式。项目灵感来源于 Typeless 的"三步输入"体验，但使用自研闭源架构，不依赖任何 GPL 代码。

### 核心特性

| 功能 | 说明 |
|------|------|
| 🎤 **AI 语音润色** | 语音输入 → 自动识别 → AI 润色 → 一键上屏 |
| ✨ **魔法修改** | 选中文字，用语音或预设指令进行 AI 改写（扩写、缩句、转英文、换语气等） |
| 📖 **魔法书** | 管理自定义魔法指令，支持置顶、新增、修改、删除、撤销 |
| ⌨️ **全键盘** | 标准 QWERTY 布局，支持拼音/双拼输入、功能键长按快捷操作 |
| 🔢 **T9 九宫格** | 9 键拼音输入，适合单手操作 |
| 🔤 **英文/符号键盘** | 中文/英文符号切换、数字键盘 |
| 📋 **剪贴板集成** | 魔法书按钮快速调用 |
| 🗂️ **历史记录** | 润色记录本地保存，支持查看回放 |
| 🎨 **深浅主题** | 亮色 / 暗色两套主题 |

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────┐
│              CesiaInputMethod               │
│         (InputMethodService 核心)            │
├─────────────┬──────────────┬────────────────┤
│  CesiaKeyboardView           │  UI 组件层    │
│  (自定义键盘绘制)             │ 候选词栏/面板  │
├─────────────┴──────────────┴────────────────┤
│              输入引擎层                       │
│  RimeEngine (librime JNI) — 拼音/双拼/T9     │
├─────────────────────────────────────────────┤
│              AI 服务层                        │
│  TypelessEngine — 语音识别 + 文字润色 (API)   │
├─────────────────────────────────────────────┤
│              数据层                           │
│  MagicHistoryManager / PolishStatsManager    │
└─────────────────────────────────────────────┘
```

| 模块 | 技术选型 | 说明 |
|------|---------|------|
| **输入法框架** | Android InputMethodService | 系统级输入法，兼容所有 App |
| **中文输入引擎** | Rime (librime via JNI) | 支持拼音、双拼、T9、模糊音 |
| **语音润色** | TypelessEngine (OpenRouter API) | 云端 AI 大模型润色 |
| **语音识别** | Android SpeechRecognizer | 系统自带在线识别 |
| **UI** | 原生 Android View | 自定义 KeyboardView + PopupWindow |
| **语言** | Kotlin | 全项目 Kotlin 实现 |

---

## ⌨️ 输入方式

### 全键盘（QWERTY）
- **拼音/双拼输入**：通过 Rime 引擎，支持全拼、双拼、模糊音
- **功能键长按**：`a`=全选, `s`=Home, `d`=End, `h/j/k/l`=方向键, `x/c/v`=剪切/复制/粘贴, `z`=撤销, `b`=粗体 等
- **popup 字符**：长按键弹出符号选择

### T9 九宫格
- 数字 2-9 对应字母，T9 拼音输入
- 长按数字键弹出该键字母候选
- 支持通过 Shift 切换纯数字输入

### 魔法修改
1. 点 ✨ 按钮 → 进入魔法模式
2. 说出修改指令（如"翻译为英文"、"语气更幽默"）
3. 或点击魔法书选择预设指令
4. AI 改写后自动替换原文

### 语音润色
1. 点 🎤 麦克风按钮说话
2. 再次点击停止 → 系统语音识别 → AI 润色 → 自动上屏
3. 可选择"AI+"润色 或"AI×"直接上屏

---

## 🚀 安装使用

### 下载
从 [GitHub Releases](https://github.com/harviex/cesia-input-method/releases) 下载最新 APK

### 首次设置
1. 安装 APK（允许未知来源安装）
2. **设置 → 系统 → 语言与输入法 → 虚拟键盘 → 启用"Cesia"**
3. **设置 → 系统 → 语言与输入法 → 默认键盘 → 选择"Cesia"**
4. 在任何输入框点击即可使用

### 配置 API（可选）
设置 → Cesia 设置：
- **API URL**：默认为内置的 Typeless 云端服务
- **API Key**：自定义 OpenRouter API Key
- **模型**：选择 AI 模型（默认可用免费模型）
- **AI 回复风格**：自然 / 幽默 / 官方 / 简洁 / 正式 / 亲切 / 犀利 等
- **模糊音**：配置声母/韵母模糊匹配
- **主题**：亮色 / 暗色

---

## 🛠️ 开发者构建

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 34
- NDK（用于 Rime 编译）

### 克隆与构建
```bash
git clone https://github.com/harviex/cesia-input-method.git
cd cesia-input-method

# Debug 构建
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleDebug

# 输出 APK
ls app/build/outputs/apk/debug/app-debug.apk
```

### 签名配置
Debug 构建使用临时 keystore（`/tmp/cesia-debug.keystore`）。
Release 构建需要配置自己的 keystore。

---

## 📁 项目结构

```
cesia-input-method/
├── app/
│   ├── src/main/
│   │   ├── java/com/cesia/input/
│   │   │   ├── CesiaInputMethod.kt       # 输入法核心服务（主逻辑）
│   │   │   ├── CesiaKeyboardView.kt      # 自定义键盘绘制
│   │   │   ├── CandidateAdapter.kt       # 候选词 RecyclerView 适配器
│   │   │   ├── SettingsActivity.kt       # 设置界面
│   │   │   ├── HistoryActivity.kt        # 润色历史记录
│   │   │   ├── engine/
│   │   │   │   ├── TypelessEngine.kt     # AI 语音润色引擎
│   │   │   │   ├── PinyinEngine.kt       # 拼音引擎（兼容层）
│   │   │   │   └── PinyinDictManager.kt  # 词库管理
│   │   │   ├── polish/
│   │   │   │   └── PolishService.kt      # HTTP API 调用
│   │   │   ├── audio/
│   │   │   │   └── AudioRecorder.kt      # 音频录制
│   │   │   ├── recognizer/
│   │   │   │   └── FallbackRecognizer.kt # 语音识别（备用方案）
│   │   │   └── stats/
│   │   │       ├── MagicHistoryManager.kt # 魔法指令历史
│   │   │       └── PolishStatsManager.kt  # 润色统计
│   │   ├── res/
│   │   │   ├── xml/                      # 键盘布局定义
│   │   │   │   ├── qwerty.xml            # QWERTY 全键盘
│   │   │   │   ├── number.xml            # 数字/T9 键盘
│   │   │   │   ├── symbols.xml           # 英文符号
│   │   │   │   ├── symbols_cn.xml        # 中文符号
│   │   │   │   └── symbols_en.xml        # 英文布局
│   │   │   ├── layout/                   # UI 布局
│   │   │   │   ├── input_view.xml        # 主输入界面
│   │   │   │   ├── popup_magic_menu.xml  # 魔法书弹窗
│   │   │   │   └── ...
│   │   │   └── values/                   # 字符串/颜色/样式
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── rime-engine/                          # Rime JNI 模块
│   └── src/main/
│       ├── cpp/                          # librime C++ 源码
│       ├── java/com/cesia/input/engine/rime/
│       │   └── RimeEngine.kt             # JNI 封装
│       └── assets/rime/                  # Rime 配置文件
│           ├── pinyin.schema.yaml        # 拼音方案
│           ├── t9_pinyin.schema.yaml     # T9 拼音方案
│           └── ...
├── README.md
├── CHANGELOG.md
└── LICENSE
```

---

## 📜 版权与许可

### 软件许可

**Cesia 输入法主程序采用自定义许可协议**，不是完全开源的自由软件。

```
Copyright © 2025-2026 Harvie (harviex). All rights reserved.

本软件授权条款：
1. 个人用户可以免费使用、备份本软件。
2. 严禁逆向工程、反编译、反汇编本软件的 APK 或任何二进制文件。
3. 严禁修改、二次分发本软件的整体或任何部分。
4. 严禁将本软件或其衍生作品用于商业目的，包括但不限于销售、
   捆绑销售、预装收费、集成到商业产品等。
5. 未经作者书面许可，不得将本软件分发给第三方。
6. 本软件以"按原样"提供，不提供任何明示或暗示的担保。
```

> 📌 本软件采用**闭源**策略。您获得的是使用权，不是所有权。
> 如对许可有任何疑问，请联系作者。

---

### 第三方依赖与致谢

本软件使用了以下开源组件，在此表示感谢：

#### 核心依赖

| 项目 | 许可 | 用途 |
|------|------|------|
| **[librime](https://github.com/rime/librime)** | BSD 3-Clause | 中文输入法引擎核心，通过 JNI 调用 |
| **[Rime 拼音方案](https://github.com/rime/rime-pinyin-simp)** | LGPL-3.0 | 简体中文拼音输入方案 |
| **[Android Open Source Project (AOSP)](https://source.android.com/)** | Apache 2.0 | Android 输入法框架 (InputMethodService) |

#### 参考与致敬

以下项目在架构设计和接口参考方面为本软件提供了重要灵感（**未直接使用其代码，不构成衍生关系**）：

- **[Trime (同文输入法)](https://github.com/osfans/trime)** — GPL-3.0
  - T9 键盘布局参考、Rime 集成方案设计灵感
  - Cesia 未使用任何 Trime/GPL 代码，所有 Rime JNI 封装均为独立实现
- **[OpenBoard](https://github.com/openboard-team/openboard)** — GPL-3.0
  - 键盘 UI 布局和自定义绘制参考

> ⚠️ **特别声明**：Cesia 输入法的核心代码为独立开发，未使用、复制或衍生任何 GPL 许可的代码。
> Ceia 与 Trime / OpenBoard 无任何代码级关联。如果此声明有误，请通过 Issue 联系作者修正。

---

## 🔄 版本历史

| 版本 | 主要变更 |
|------|---------|
| v1.1.x | T9键盘、魔法书（撤销/置顶/新增/修改/删除）、全键盘功能键长按、候选词面板、模糊音支持、多项UI修复 |
| v1.0.x | 基础框架、语音润色、全键盘输入、简单符号切换 |

详见 [CHANGELOG.md](CHANGELOG.md)（如适用）

---

## 🤝 反馈与联系

- **GitHub Issues**：https://github.com/harviex/cesia-input-method/issues
- **Twitter/X**：[@HarvieTse](https://x.com/HarvieTse)
- **开发者**：主人 (@harvie) + Hermes Agent AI 协作开发

---

## 🙏 致谢

感谢所有开源社区开发者的贡献。没有 librime、AOSP InputMethodService 等开源项目，就没有 Cesia。

特别感谢：
- **Rime 输入法团队** — 提供了强大的开源输入法引擎
- **Android AOSP** — 提供了输入法扩展框架
- **OpenRouter** — 提供了 AI 模型 API 接入

---

<p align="center">
  <b>Cesia — 让输入更智能，让 AI 为你所用</b><br>
  Made with ❤️ by Harvie, CA · 2025-2026
</p>
