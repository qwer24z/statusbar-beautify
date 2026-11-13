# 状态栏美化（LSPosed 模块）

把一张图铺进系统状态栏，替代系统那块半透明底。类原生 / AOSP 的 SystemUI 上效果最好，
和 B 站那些「类原生美化」视频里的状态栏贴图是一个路子，只是不用改 ROM、不用装 Magisk 模块。

## 效果

- 状态栏背景换成你选的图，居中裁切，铺满整条
- 圆角可调（0–40 dp，走 Outline 裁切，不改系统资源）
- 暗化可调（0–80%，给状态栏图标留对比度）
- 模糊可调（0–25 dp，方框模糊跑三遍近似高斯）
- 设置页里实时预览，所见即所得

## 原理

- 模块 APK 声明 `xposedmodule`，入口在 `assets/xposed_init`
- 两层找状态栏，命中哪个用哪个，两个都中也只是同一张图铺两遍：
  1. **窗口层（通用）**：挂 `WindowManagerImpl#addView`，认 `WindowManager.LayoutParams.type == TYPE_STATUS_BAR(2000)`
     的窗口，把图铺在它的 `DecorView` 上。厂商改类名改不掉窗口类型，ColorOS 这类就靠这层。
  2. **类名层（精确）**：Android 11+ 的 `CollapsedStatusBarFragment#onCreateView`，
     10 及以前的 `StatusBar` / `PhoneStatusBar#makeStatusBarView`
  - 类名对不上的候选安静跳过，不会把系统界面搞挂
- 拿到 View 后 `setBackground(BitmapDrawable)` + `clipToOutline` 做圆角
- 配置用 `XSharedPreferences` 走：模块的 SharedPreferences 存图（Base64 JPEG）和参数，
  SystemUI 进程启动时读一次

## 用之前得知道

- **LSPosed 里必须勾作用域**：模块列表 → 状态栏美化 → 作用域 → 勾「系统界面」（`com.android.systemui`）。
- 改完参数要**重启系统界面**（或重启手机）才生效，图不是实时刷新的。
- AOSP / 类原生 直接走类名层，效果最稳。
- **ColorOS / OriginOS / MIUI 这类**：类名多半对不上，走窗口层兜底，能不能成看厂商有没有在
  DecorView 上再盖一层不透明的遮罩（厂商自己的状态栏底色）。没生效就先看 LSPosed 日志里
  `SBBeautify` 打的「命中 / 跳过」，确认到底走到哪一层了。
- 系统界面包名不是 `com.android.systemui` 的，只要包名或进程名里带 `systemui` 也照样挂。
- 那个状态栏 View 的背景有可能被系统某些转场逻辑重置（比如沉浸式的 bar 模式切换），
  如果发现图偶尔没了，是这原因，等后续 hook `BarTransitions`。
- 图标包那类「美化」是资源替换，Xposed 干不了，得用 overlay。

## 编译

### GitHub Actions（推荐）

推到 GitHub，`.github/workflows/build.yml` 会自动：

1. 拉 Xposed API 82（阿里云 jcenter 镜像，失败再试 Maven Central）
2. 生成一次性签名
3. `gradle assembleRelease`
4. 把 APK 传到 Actions 的 Artifacts 里

### 本地

```bash
mkdir -p app/libs
curl -L -o app/libs/xposed-api-82.jar \
  https://maven.aliyun.com/repository/jcenter/de/robv/android/xposed/api/82/api-82.jar
gradle assembleRelease      # 没配签名就出 app-release-unsigned.apk
```

需要 JDK 17 + Android SDK（`local.properties` 指 `sdk.dir`）。
注意 Google 发的 build-tools 只有 x86_64 版，**ARM Linux 上 aapt2 跑不起来**，
要么用 x86_64 机器 / 普通 PC，要么直接交给 Actions。

## 目录

```
app/src/main/java/com/kuuzan/statusbar/
  HookMain.java        # 入口，找 SystemUI 的类挂钩子
  Beautifier.java      # 拿根 View、设背景、裁圆角（跑在 SystemUI 进程）
  RenderUtil.java      # 裁切 + 暗化 + 模糊，预览和实际共用
  Blur.java            # 方框模糊
  SettingsActivity.java# 选图 / 预览 / 调参
  Prefs.java           # 两端的存储约定（包名必须跟 applicationId 一致）
```
