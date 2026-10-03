# NekoTodo — Android 客户端

[NekoTodo](https://github.com/TGBUG/NekoTodo) 的原生 Android 客户端:AI 加持的待办清单。

后端是独立的 Python 项目(在本机另一处开发),**本仓库只做客户端,不改后端**。接口契约以后端仓库的 README 为准。

## 功能范围(v0.2)

- **登录**:服务器地址可配置并记住历史。**不支持注册**——账户请用 Web 端或后端 CLI 创建
- **任务列表**:未完成/已完成分组与折叠、分类横向筛选、勾选完成、**同一截止日组内拖拽排序**(跨组会被挡住并给出提示)、长按操作菜单(编辑内容/修改日期/修改分类/上移/下移/删除)
- **AI 创建**:文本 + 拍照/图片/文件 → 后端异步拆解 → 前台轮询进度。图片会先降采样压缩(避免撞后端 20MB 上限),支持的类型与后端一致(图片、docx/pptx/pdf、txt/md/csv/json)
- **搜索**:文本、分类、状态、截止日期区间。筛选全在客户端完成(后端没有搜索接口)
- **源信息详情**:溯源"这个待办来自哪条作业、哪个文件/哪张图";可改内容并触发重新拆解、查看图片与文档、删除(级联)
- **账户**:提示词模板、时区、修改密码、撤销全部 token、注销账户
- **外观**:昼夜主题(跟随系统/浅色/深色)、**自由取色**(从任意色相推导整套配色并保证文字对比度)、背景(Hoshino / Koharu / 自定义图片 / 无)
- **星图(源映射模式)**:源信息为"星"、条目绕星成环、任务绕条目铺开,图片条目直接显示缩略图;可拖动平移、双指缩放,点星看源信息、点条目高亮其任务、点任务编辑。
  与 Web 端的一处不同:详情只在**源信息集合变化**或**有拆解刚结束**时才重取,而不是每轮轮询都对每个源信息各拉一次(N+1)

**未做**:应用内注册。

## 构建

要求:JDK 17、Android SDK(compileSdk 37 / targetSdk 36 / minSdk 26)。

```bash
# 指向本机 SDK(local.properties 不入库)
printf 'sdk.dir=/path/to/Android/Sdk\n' > local.properties

./gradlew :app:assembleDebug        # 产物:app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # 纯逻辑单测
```

构建链:Gradle 9.7.1 / AGP 9.4.1 / Kotlin 2.4.20(由 AGP 内置提供,工程里**没有** `org.jetbrains.kotlin.android` 插件)/ Jetpack Compose(compose-bom)。

## 发布

打 tag 即出包(流水线:跑单测 → 构建签名 release APK → 挂到 GitHub Releases):

```bash
git tag v0.2.0 && git push origin v0.2.0
```

需要 4 个仓库 Secrets:`KEYSTORE_BASE64`(keystore 文件的 base64)、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
本地要出签名包时导出同名环境变量(键名见 `app/build.gradle.kts`);不提供时 release 产出未签名包(`app-release-unsigned.apk`)。

版本名取自 tag,版本号取自流水线序号——**每次发布都有更大的 versionCode**,已安装的用户才能覆盖升级。

⚠️ keystore 丢失就无法再覆盖升级(只能卸载重装、本机数据全丢),务必在仓库外备份(密码管理器 + 云盘各一份)。

## 连接后端

首次启动填服务器地址,**不带协议时会自动补 `https://`**;局域网明文 HTTP 必须显式写 `http://`(此时会提示"该连接未加密")。

后端 `/me/preferences` 只有提示词模板与时区两个字段,所以**外观设置只存在本机**,换设备或重装都要重设。

## 已知限制

这些多数来自"后端不改"这一约束,是设计取舍而非 bug:

1. **后端没有推送通道**(无 WebSocket/SSE),拆解完成只能在前台轮询时得知;App 退到后台就收不到提醒
2. **`priority` 是"同一截止日组内的绝对序位"**,两端并发编辑同一组会互相覆盖,因此不做离线编辑与增量同步
3. **外观设置不跨设备同步**(见上)
4. **全局放行明文 HTTP**——家用服务器直连需要。生产部署建议用 nginx 反代终止 TLS,客户端填 `https://` 即可
5. 星图的**双指缩放**我只验证了几何与视口数学(有单测),手势本身未在设备上模拟(adb 无法注入双指),但平移与"平移后点击命中"都已实测
6. **后端返回的时间戳可能不带末尾 `Z`**(实际观测如此,文档写的是 UTC ISO 8601),客户端按 UTC 兼容解析
7. 源信息里没有可拆解内容时,后端会让整次拆解以 `RuntimeError: agent made no tool calls` 失败(观感上更像后端应"完成并新建 0 个任务")

### 用 IDEA 打开时的误报

`app/src/main/AndroidManifest.xml` 第二行的命名空间声明会被 IDEA 报"URI 未注册(设置 | 语言和框架 | 架构和 DTD)"。这是 **IDEA 的 XML 目录不认识 Android 命名空间**(Android Studio 自带 Android 插件才会注册),manifest 本身是标准且必需的,AGP 构建完全正常。

消除办法:设置 → Languages & Frameworks → Schemas and DTDs → 把 `http://schemas.android.com/apk/res/android` 加入"Ignored Schemas and DTDs"。

## 目录结构

```
app/src/main/kotlin/cn/tgbug/nekotodo/
├── domain/     纯逻辑:时区换算、截止日分组与排序、搜索过滤、调色板推导、模糊算法
│               —— 不依赖 Android,全部有单测
├── data/       网络层(Retrofit + kotlinx.serialization + 鉴权拦截器)、本地持久化(DataStore)、
│               媒体处理(拍照压缩、背景图模糊、带鉴权的图片读取)
├── ui/         各页面(登录/任务列表/搜索/账户/外观/AI 创建/源信息详情/星图)与主题
docs/agents/    issue tracker、triage 标签、领域文档的约定
docs/reference/ Web 端单 HTML 客户端的快照(本仓库之外没有可链接的权威源)
```

调色板推导(`domain/Palette.kt`)有一组**扫色性质测试**:遍历所有色相 × 饱和度 × 明度(含纯白/纯黑/纯灰),断言主文字对比度 ≥ 7:1、按钮前景 ≥ 4:1。它曾抓出两个推导错误(深色模式表面饱和度算错、暖色系天生亮度更高),改动调色板时请先跑它。
