# webbox-app

通用安卓网页容器：一个 App 管理任意网页，可为每个网址生成桌面快捷方式（类似小程序添加到桌面），点击图标直达网页，无需为每做的一个网页单独打包 App。

## 功能

- **手动添加网址**：主界面输入名称 + 网址，列表管理（打开 / 添加到桌面 / 删除），数据存本地。
- **添加到桌面**：为每个网址抓取站点 favicon 作为图标，生成桌面快捷方式；点击直接全屏打开该网页（0 点击直达）。
- **全屏网页容器**：WebView 铺满全屏，不向网页注入任何 JS，也不处理安全区；靠系统返回键导航。
- **壳自更新**：主界面「检查更新」，走统一分发目录 appdist，下载后自动安装。

## 目录

```
webbox-app/
├── android/                       # 安卓工程（Gradle / Kotlin）
│   └── app/src/main/java/com/xb18/webbox/
│       ├── MainActivity.kt        # 网址管理主界面
│       ├── WebViewActivity.kt     # 全屏网页容器
│       ├── ShortcutHelper.kt      # favicon 抓取 + 桌面快捷方式
│       ├── SiteStore.kt           # 本地网址存储
│       └── Updater.kt             # 壳更新
└── .github/workflows/android.yml
```

## 发版

打 `android-v<版本>` tag 推送，CI 构建 Debug APK、创建 Release，并通过 WebDAV 发布到通用分发根 `lanzou/appdist`：

```
appdist/
├── webbox-app-<版本>.apk
└── webbox-app-update.txt          # 更新索引
```

默认更新源：`https://pan.202921.xyz/sd/59uaf2bR/webbox-app-update.txt`

需要在仓库配置：

- Secrets：`WEBDAV_URL` / `WEBDAV_USERNAME` / `WEBDAV_PASSWORD`
- Variables：`WEBDAV_DIR=lanzou/appdist`（默认）、`PUBLIC_BASE=https://pan.202921.xyz/sd/59uaf2bR`（默认）
