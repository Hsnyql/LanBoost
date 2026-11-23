# 固定签名与 GitHub Actions

LanBoost 的 Release 构建沿用早期测试版的签名证书，以保持同包名覆盖升级兼容。证书私钥另存于受密码保护的 PKCS#12 文件；原本的 Android debug keystore 不受修改。Release 构建关闭 `debuggable`，但仍保留原证书身份，并非重新生成的证书。

公开的证书 SHA-256 保存在 `signing-certificate.sha256`，它不是私钥。构建会核对私钥条目、密码及证书指纹；未配置或配置错误时拒绝构建 Release，不会自动换成临时签名或输出未签名版本。后续发布须保持相同签名和包名，版本号按需递增，不能降低 `versionCode`。

## 本地构建

默认从仓库外的 `~/.config/lanboost/signing/signing.properties` 读取配置。也可以设置 `LANBOOST_SIGNING_PROPERTIES` 指向其他位置。格式见 `signing.properties.example`：

- `storeFile`：PKCS#12 签名文件的绝对路径。
- `storePassword`：签名文件密码。
- `keyAlias`：私钥条目别名。
- `keyPassword`：条目密码。

签名目录仅所有者可访问，密钥及配置文件仅所有者可读写。请独立备份这些文件；源码归档不包含它们，GitHub Secrets 也不能供维护者重新下载原值。不要把真实配置粘贴进工单、聊天或提交记录。

```sh
./gradlew \
  :core:test :app:lintRelease :app:assembleRelease \
  --no-daemon --no-configuration-cache
python3 scripts/verify-apk-signature.py \
  app/build/outputs/apk/release/app-release.apk
```

设置 JDK 17 的 `JAVA_HOME` 和 Android SDK 的 `ANDROID_HOME`；验签脚本使用 Build Tools 35.0.0。输出为 `app/build/outputs/apk/release/app-release.apk`。常规 Debug 构建不依赖这些凭据，仍使用本机构建环境的调试密钥。

Fork 或其他维护者若使用自己的签名，应更新公开指纹，并使用自己的私密配置；新证书不能直接覆盖旧证书签署的同包名安装。

## GitHub Secrets

在目标仓库的 Settings → Secrets and variables → Actions 添加：

| Secret | 内容 |
| --- | --- |
| `LANBOOST_KEYSTORE_BASE64` | PKCS#12 文件的单行 Base64 编码 |
| `LANBOOST_KEYSTORE_PASSWORD` | 文件密码 |
| `LANBOOST_KEY_ALIAS` | 条目别名 |
| `LANBOOST_KEY_PASSWORD` | 条目密码 |

工作流将文件写入 runner 临时目录，密码通过环境变量传给 Gradle，结束时清理临时文件。签名文件不进入 Git、构建缓存或下载产物。环境变量优先于本地 properties 文件，CI 另通过 `LANBOOST_KEYSTORE_FILE` 指定临时文件位置。

## 自动构建行为

检查工作流位于 `.github/workflows/android.yml`：

- 推送到 `main`：运行核心测试、Android lint 和 Debug 构建；通过后生成固定签名的 Release APK 并再次验签。
- PR：仅运行检查及 Debug 构建，不读取固定签名 Secrets。
- Actions → Android APK → Run workflow：在默认分支手动运行，默认生成固定签名 APK，也可关闭 `signed_apk` 只做检查。
- 固定签名构建只接受默认分支的推送或手动运行。若更改默认分支名，请同步修改工作流的 push 分支筛选。

发布工作流位于 `.github/workflows/release.yml`：推送 `v<versionName>` 标签（如 `v0.5.0`）时运行核心测试和 Release lint，构建固定签名 APK 并验签，然后自动创建同名 GitHub Release。标签必须与 `app/build.gradle` 中的 `versionName` 完全一致，否则构建失败。Release 附件为 `LanBoost-<versionName>.apk`、`LanBoost-<versionName>-source.zip` 和 `SHA256SUMS.txt`；同一批文件也会作为 Artifact 保留 30 天。

下载 `main` 推送对应运行的 `LanBoost-signed-<运行号>` Artifact，内含 `LanBoost-release.apk` 和 `SHA256SUMS.txt`，保留 30 天。`LanBoost-ci-debug-<运行号>` 使用 runner 临时调试签名，只用于检查，不作为已有安装的更新包，保留 14 天。

Actions 下载权限遵循仓库可见性。`android.yml` 只有读取仓库权限；`release.yml` 仅在 `v*` 标签推送时以 `contents: write` 创建 GitHub Release，不发布到应用商店，也不改变仓库可见性。Actions 的实际运行额度与保留策略由 GitHub 账号及仓库设置决定。
