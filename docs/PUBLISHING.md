# 源码与版本发布说明

## 仓库内容

通过 Git 发布已跟踪的源码、文档、Gradle wrapper 和测试。不要把整个开发工作目录直接压缩上传：未跟踪目录可能包含设备日志、测试截图、本机 SDK 配置和私有构建文件。

公开文档使用通用设备描述，协议测试中的网络地址使用文档示例地址。提交作者可使用项目身份或个人 GitHub 隐私邮箱；Git 作者与提交者信息都会随历史公开。

## 源码归档

在仓库根目录执行以下命令，只导出当前提交中的文件，不包含 `.git`、未跟踪文件或被忽略的诊断资料：

```sh
git archive --format=zip --prefix=LanBoost-source/ -o LanBoost-source.zip HEAD
```

文件包含工程源码和必要说明，解压后根目录即可作为 Android 工程打开。归档前应确认当前提交包含预期文档，并检查是否有本机路径、设备序列号、账户信息或凭据。

## APK 发布

按照[构建说明](DEVELOPMENT.md#build-and-test)运行检查。固定签名 Release 构建和 GitHub Actions 已配置，详见[签名说明](SIGNING.md)。本项目沿用早期测试版证书以保留升级兼容性，密钥和密码保存在仓库之外及目标仓库 Secrets 中；更换签名会影响覆盖安装。

Git 不跟踪 APK 或构建日志。发布流程：

1. 递增 `app/build.gradle` 中的 `versionCode`，并把 `versionName` 更新为本次版本（如 `0.5.0`）；`versionCode` 不能降低。
2. 提交并推送到 `main`，等待 `Android APK` 工作流通过。
3. 在通过检查的提交上创建并推送标签 `v<versionName>`（如 `v0.5.0`）：

   ```sh
   git tag v0.5.0
   git push origin v0.5.0
   ```

4. `Release` 工作流自动构建固定签名 APK、验签并创建 GitHub Release，附 `LanBoost-<versionName>.apk`、对应提交的源码归档和 `SHA256SUMS.txt`。标签与 `versionName` 不一致时工作流失败。

校验值由工作流按最终附件生成；手工补发附件或重新打包源码归档后必须重新生成。

当前源码目录保存源码、Gradle 配置、测试、自动构建脚本和必要说明。开发时使用的 `work/`、`outputs/`、日志、截图和偏好备份由 `.gitignore` 排除。Git 发布历史从整理后的工程源码开始，历史交付目录和重复说明不纳入发布历史。APK、源码归档和校验值由 Release 工作流生成并上传为附件。

## 许可与来源

LanBoost 的原创实现采用 [GPL-3.0-only](../LICENSE)，版权和适用范围见 [NOTICE.md](../NOTICE.md)。分发 APK 时须同时按 GPLv3 提供对应源码，保留许可、版权和必要的修改声明；本项目的 Release 工作流会随 APK 提供对应提交的源码归档。私有仓库链接本身不能替代向接收者提供可获取的源码。

Gradle 会把根目录的 `LICENSE` 和 `NOTICE.md` 自动打入 APK；原有第三方许可声明继续保留。发布说明会链接许可证和对应版本源码。

第三方组件及 Gradle wrapper 保留各自许可证。XboxDownload 的规则、链接和节点来源授权仍需独立核清；GPL 声明不代表本项目可以重新许可上游资料。
