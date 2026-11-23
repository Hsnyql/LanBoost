# LanBoost

面向 Root Android 设备的 CDN 测速与局域网 DNS 应用。当前版本 **0.5.0**，处于测试阶段。

LanBoost 可以获取候选节点、测试连接与下载速度，并通过手机提供 DNS，让同一局域网中的主机或电脑使用选定节点。支持 Xbox、微软商店应用、Nintendo Switch、PlayStation、EA、战网国际服及其他 Akamai 资源的独立规则。Xbox 国内 HTTP 换源默认开启，选择 CN1 节点后生效。

项目参考 [XboxDownload](https://github.com/skydevil88/XboxDownload) 的域名与测速流程，是独立的 Android 实现，不是官方移植，也不代表原项目或主机厂商。功能范围不包含下载游戏到手机、商店浏览、文件回传或游戏联机代理。

## 使用条件

- Android 8.0 及以上；目前真机验证覆盖 Android 16，其他版本仍需测试。
- 提供标准 DNS/HTTP 端口需要 Root，且 `su` 和 `iptables` 可用。KernelSU 已实测，Magisk、APatch 的设备组合尚未全面验证。
- 手机与主机或电脑连接同一非隔离局域网；路由器无需刷机。
- 获取节点和测速不要求 Root。

## 快速开始

1. 从 [Releases](https://github.com/Hsnyql/LanBoost/releases/latest) 下载 APK，允许通知，并在 Root 管理器中授权 **LanBoost**。
2. 选择平台、获取或导入节点、测速并选用 IP。Xbox 国内换源需要选择 CN1；部分旧游戏下载还需要 CN2。
3. 启动 DNS 服务，将主机或电脑的主 DNS 设置为 App 显示的手机 Wi-Fi 地址；副 DNS 留空，或填同一地址。
4. 暂停再继续下载，在活动页确认请求命中规则。
5. 停止服务或手机离开 Wi-Fi 前，将客户端 DNS 恢复自动。

节点获取跟随系统当前网络，依次使用 GitHub Raw 和 GitHub API。完整库获取失败时保留上次成功的缓存；没有完整缓存时才尝试少量 DNS 候选。页面标明来源、更新时间和失败原因。刷新保留已有测速结果与选用节点；节点列表支持分页，下载测速可继续下一批。支持运营商、省份 / 地区、城市和关键词组合筛选；确认测速时可选每批最多 12 个（可设置）或测完筛选结果，并显示测试数据上限。筛选条件按平台保存，“采用最快”也限于当前筛选。

部分系统会冻结后台应用。按系统提供的选项允许后台运行，并在最近任务中锁定 App。现有短时锁屏测试通过，不代表所有机型、长时间待机或未充电状态都稳定。

## 文档

- [使用说明与故障排查](docs/USAGE.md)
- [构建、协议与依赖说明（英文）](docs/DEVELOPMENT.md)
- [数据与隐私说明](docs/PRIVACY.md)
- [源码与版本发布说明](docs/PUBLISHING.md)
- [固定签名与自动构建](docs/SIGNING.md)

## 仓库结构

```text
app/          Android 应用、资源和设备测试
core/         DNS、测速、节点筛选与核心测试
gradle/       Gradle wrapper
scripts/      APK 签名校验
.github/      自动构建与 Release 发布
docs/         使用、开发、隐私与发布说明
```

仓库只保存工程源码、构建配置、测试和必要说明。APK、源码压缩包及校验值放在 GitHub Releases；开发过程中的交付目录、设备日志与截图不纳入 Git。

## 构建

安装 JDK 17 和 Android SDK 35，设置 `JAVA_HOME`、`ANDROID_HOME`。使用 Android Studio 时直接打开仓库根目录。

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。仓库包含 Gradle wrapper，不包含本机 SDK 路径、构建缓存或签名私钥。

调试包使用构建机器的 debug 密钥。固定签名 Release 构建已配置，沿用早期测试版证书以支持覆盖升级，私钥与密码保存在仓库之外。推送到 `main` 后，GitHub Actions 自动测试并生成签名 APK；推送 `v<versionName>` 标签（如 `v0.5.0`）后，Release 工作流自动创建带 APK、源码归档和校验值的 GitHub Release；PR 构建不接触签名密钥。配置和下载方法见[签名说明](docs/SIGNING.md)。APK 和源码压缩包不随 Git 提交。

## 已知限制

- DNS 选址不能保证提速，效果取决于运营商线路、节点缓存、无线网络和客户端。
- 国内换源仅适用于支持的 Xbox HTTP 下载。相关 HTTPS 下载异常时，关闭换源并使用 COM 组；不提供 HTTPS 中转。
- NS 默认测速地址在已测网络中返回错误；可更换有效链接，或清空链接后仅测延迟。
- 部分分组默认仅测连接延迟；Steam 规则覆盖静态资源，不代表游戏仓库下载支持。
- 后台冻结、长时间锁屏和设备兼容性仍需持续验证。

## 来源与许可状态

[依赖许可声明](app/src/main/assets/THIRD_PARTY_NOTICES.txt) 随源码和 APK 保留。上游域名、参考链接及节点来源见[构建说明](docs/DEVELOPMENT.md#dependencies-and-sources)。

LanBoost 的原创实现采用 **[GNU GPL v3.0（GPL-3.0-only）](LICENSE)**。你可以使用、修改和商业分发；分发受 GPL 约束的版本时，须按协议向接收者提供对应源码，并保留版权、许可及修改说明。软件不提供担保。

项目版权及来源范围见 [NOTICE.md](NOTICE.md)。第三方依赖和 Gradle wrapper 保留各自许可证。域名规则、默认测速链接和节点来源参考 XboxDownload；尚未核清的上游资料授权不由本项目的 GPL 声明代为授予。
