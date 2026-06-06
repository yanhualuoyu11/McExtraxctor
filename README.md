# McExtractor — Minecraft 资源浏览器

浏览、预览和批量导出 Minecraft 游戏资源的桌面工具，基于 Java Swing。

## 功能

- **层级导航** — 按命名空间 / 目录树浏览所有资源
- **表格视图** — 显示每个资源的路径、类型、大小、SHA1 哈希值
- **图片预览** — 支持 PNG / JPEG / GIF / BMP，可缩放、适应窗口、1:1 显示
- **音频预览** — 支持 OGG (Vorbis) / WAV 播放，带播放 / 暂停 / 停止和进度条
- **文本预览** — 支持 JSON / .lang / .mcmeta / GLSL 着色器 / .properties 等文本文件
- **实时搜索** — 可按文件名搜索，支持按扩展名筛选
- **右键菜单** — 预览、导出选中、导出可见、复制路径、复制哈希值、全选
- **批量导出** — 支持导出选中 / 可见 / 全部 / 按文件类型，保留原始目录结构
- **自动检测** — 启动时自动查找 Windows (`%APPDATA%\.minecraft`) / Linux / macOS 标准路径，加载最新索引

## 运行要求

- **Java 17** 或更高版本
- **Maven 3.8+**（仅构建时需要）

## 快速开始

### 构建

```bash
mvn clean package
```

构建产物为 `target/McExtractor-1.0.0-jar-with-dependencies.jar`（含依赖的 fat JAR）。

### 运行

```bash
java -jar target/McExtractor-1.0.0-jar-with-dependencies.jar
```

或直接用 Maven 运行：

```bash
mvn exec:java -Dexec.mainClass="com.mcextractor.Main"
```

### 首次使用

1. 启动程序后会自动检测 `.minecraft` 目录并加载最新索引
2. 若未自动检测到，使用 **文件 → 打开索引** 选择 `indexes/` 下的 `.json` 文件
3. 使用 **文件 → 设置对象目录** 指向 `.minecraft/assets/objects/`
4. 左侧树形导航点击节点，右侧表格显示对应资源，下方预览区显示选中文件

## 资源目录结构

程序需要标准 Minecraft 资源布局：

```
.minecraft/
  assets/
    indexes/
      1.20.json          ← 版本索引文件
      1.21.json
      ...
    objects/
      ab/                ← SHA1 哈希的前 2 位
        abcd1234...      ← 实际文件，以完整哈希命名
      ...
```

`.minecraft` 目录的查找顺序：

1. 当前工作目录下的 `.minecraft`
2. **Windows**: `%APPDATA%\.minecraft`
3. **Linux**: `~/.minecraft`
4. **macOS**: `~/Library/Application Support/minecraft`

## 快捷键

| 按键 | 操作 |
|------|------|
| `Ctrl+O` | 打开索引文件 |
| `Ctrl+Q` | 退出程序 |
| `Enter` | 预览选中资源 |
| `Ctrl+C` | 复制资源路径 |
| 双击表格行 | 预览 |

## 依赖

| 依赖 | 用途 |
|------|------|
| [Gson](https://github.com/google/gson) | JSON 解析 |
| [VorbisSPI](https://github.com/pdudits/soundlibs) | OGG Vorbis 格式 SPI |
| [JOrbis](https://github.com/pdudits/soundlibs) | OGG Vorbis 解码器 |
| [Tritonus Share](https://github.com/pdudits/soundlibs) | 音频框架支撑 |

## 项目结构

```
McExtractor/
├── pom.xml                          ← Maven 构建配置
├── README.md
├── .gitignore
└── src/main/java/com/mcextractor/
    ├── Main.java                    ← 入口
    ├── model/
    │   ├── AssetEntry.java          ← 单个资源数据模型
    │   └── AssetIndex.java          ← 索引解析 + 层级树
    ├── service/
    │   ├── AssetService.java        ← 索引加载 / 对象文件解析
    │   └── ExportService.java       ← 导出逻辑
    └── ui/
        ├── MainFrame.java           ← 主窗口（菜单、工具栏、事件分发）
        ├── AssetTreePanel.java      ← 左侧层级导航树
        ├── AssetTablePanel.java     ← 右侧资源表格
        ├── PreviewPanel.java        ← 预览切换容器
        ├── ImagePreviewPanel.java   ← 图片预览（缩放 / 适应）
        └── AudioPreviewPanel.java   ← 音频播放器（OGG / WAV）
```

## 许可证

MIT
