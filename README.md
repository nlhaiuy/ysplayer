# 玻璃播放器 (GlassPlayer)

一个本地音乐播放器：**支持 WebVTT (.vtt) 歌词**，界面参考 iOS 26 的 **Liquid Glass（液态玻璃）** 设计语言。
Kotlin + Jetpack Compose + Media3，支持后台播放、通知栏 / 锁屏 / 蓝牙耳机控制。

## 运行

1. 用 **Android Studio（Ladybug 2024.2 或更新）** 打开本目录，等待 Gradle 同步。
2. 连接手机（Android 8.0+，建议 **Android 12+** 以获得完整的模糊效果）或模拟器，点 Run。
3. 首次打开点「选择文件夹」，选中存放音乐的目录。

## 歌词怎么配对

- **自动**：与音频同名的 `.vtt` 放在同一个文件夹，例如 `夜航.flac` + `夜航.vtt`。
  带语言后缀也行：`夜航.zh.vtt`。
- **手动**：在「正在播放」右上角点字幕按钮，为当前歌曲选一个 .vtt，之后会记住。
- 点歌词的任意一行可以跳转到那一句。
- 一个 cue 里写两行，第二行会以小字显示（适合原文 + 翻译）。
- 示例见 `sample/demo.vtt`。

支持的 VTT 写法：有/无小时的时间戳、`.` 或 `,` 毫秒分隔、cue 标识行、cue 设置（`align:` `position:`）、
`NOTE`/`STYLE`/`REGION` 块（忽略）、`<c>` `<v>` `<i>` 与逐字时间戳标签（剥离）、UTF-8 BOM。

## 液态玻璃是怎么做的

`ui/Glass.kt` 里的 `Modifier.glass()`：

1. 用 [Haze](https://github.com/chrisbanes/haze) 对身后内容做实时背景模糊
2. 对角线体光 + 左上角镜面高光
3. 渐变高光描边（左上最亮、中段几乎透明、右下回亮）——玻璃边缘的标志性细节
4. `bounceClick()` 提供按下时弹簧回弹的手感

另外还有：封面取色驱动的漂移光斑背景、播放/暂停时封面缩放、玻璃分段控件的弹簧滑动、
Apple Music 风格的歌词（非当前行变暗、变模糊，上下渐隐）。

> 没有实现 iOS 26 那种边缘透镜折射（需要 AGSL 着色器，仅 Android 13+），目前是模糊 + 高光的近似效果。
> Android 11 及以下没有实时模糊，会退化为半透明着色。

## 结构

```
app/src/main/java/com/example/glassplayer/
├── MainActivity.kt        入口、通知权限
├── PlaybackService.kt     Media3 后台播放服务
├── PlayerViewModel.kt     播放控制、歌词同步、曲库状态
├── Library.kt             SAF 文件夹扫描、标签/封面读取、JSON 缓存
├── VttParser.kt           WebVTT 解析
├── Models.kt
└── ui/
    ├── Glass.kt           玻璃修饰符与按钮
    ├── Components.kt      动态背景、进度条、分段控件
    ├── LyricsView.kt      歌词滚动视图
    ├── Screens.kt         曲库、迷你播放器、正在播放
    ├── Cover.kt           封面加载与取色
    └── App.kt             根布局
```
