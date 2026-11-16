# Clash Zero

Clash Zero 是基于 [Mihomo（原 Clash.Meta）](https://github.com/MetaCubeX/mihomo) 内核开发的 Android 图形化代理客户端。

本项目在保留 Mihomo 核心能力的基础上，重点完善移动端的订阅、节点管理、目标分流和代理服务配置体验。

## 项目功能

- **订阅管理**：导入、更新和管理订阅；查看订阅中的代理节点；支持节点重命名、测速和节点详情。
- **节点管理**：将不同订阅中的节点组织为静态或动态节点组。
- **目标分流**：创建和维护域名、IP、GeoIP、GeoSite 等目标集合。
- **代理服务**：将目标集合关联到代理服务，并选择节点组和出口策略。
- **Mihomo 内核**：使用 Mihomo 提供代理协议、规则匹配和流量转发能力。

> 项目仍在持续开发中。README 中列出的模块是项目方向，不代表所有功能都已完成。具体完成情况以代码和实际构建版本为准。

## 系统要求

- 最低支持 Android 5.0
- 建议使用 Android 7.0 或更高版本
- 支持以下 ABI：
  - `armeabi-v7a`
  - `arm64-v8a`
  - `x86`
  - `x86_64`

## 本地构建

### 1. 获取源码及子模块

```bash
git clone --recurse-submodules https://github.com/ztesfyfi/Clash-Zero.git
cd Clash-Zero
git submodule update --init --recursive
```

### 2. 安装构建环境

请准备以下工具：

- OpenJDK 11
- Android SDK
- CMake
- Go（Golang）

### 3. 配置 Android SDK

在项目根目录创建 `local.properties`：

```properties
sdk.dir=/path/to/android-sdk
```

请将路径替换为本机 Android SDK 的实际安装目录。

### 4. （可选）自定义应用包名

在 `local.properties` 中添加：

```properties
# 自定义 applicationId
custom.application.id=com.example.clashzero

# 设置为 true 时移除默认的应用 ID 后缀
remove.suffix=true
```

如果不配置自定义包名，请以项目 Gradle 配置中的默认值为准。

### 5. （可选）配置签名

如需构建签名版本，在项目根目录创建 `signing.properties`：

```properties
keystore.path=/path/to/keystore/file
keystore.password=你的密钥库密码
key.alias=你的密钥别名
key.password=你的密钥密码
```

请勿将真实密钥、密码或签名文件提交到公开仓库。

### 6. 执行构建

构建 Alpha Release：

```bash
./gradlew app:assembleAlphaRelease
```

构建 Debug 版本：

```bash
./gradlew app:assembleDebug
```

## 自动化控制

应用提供外部 Intent 控制入口。应用包名和 Activity 名称请以当前构建变体的实际配置为准。

控制 Activity：

```
com.github.kr328.clash.ExternalControlActivity
```

支持的 Action：

| 操作 | Action |
|---|---|
| 切换运行状态 | `com.github.metacubex.clash.meta.action.TOGGLE_CLASH` |
| 启动服务 | `com.github.metacubex.clash.meta.action.START_CLASH` |
| 停止服务 | `com.github.metacubex.clash.meta.action.STOP_CLASH` |

导入订阅配置可使用以下 URL Scheme：

```
clash://install-config?url=<经过 URL 编码的订阅地址>
clashmeta://install-config?url=<经过 URL 编码的订阅地址>
```

## 项目结构与设计方向

项目围绕以下配置链路持续完善：

```
订阅管理 → 节点管理
目标分流 → 代理服务 → 节点组 → Mihomo 出站节点
```

设计原则：

- 订阅节点的原始名称与用户自定义名称分开保存。
- 节点测速、出口 IP 和归属地信息应由实际检测结果产生，不能根据节点名称猜测。
- 节点可以被多个节点组引用。
- 一个目标集合只能绑定一个代理服务；一个代理服务可以绑定多个目标集合。
- 配置生成前检查无效引用、重复绑定和循环引用。
- 配置校验失败时，不应覆盖当前正在使用的有效配置。

## 贡献与内核维护

### Mihomo 内核

本项目使用 [Mihomo](https://github.com/MetaCubeX/mihomo) 作为代理内核。内核相关问题和功能建议请优先参考 Mihomo 项目文档及其贡献指南。

### 项目开发

欢迎提交 Issue 和 Pull Request。提交代码前建议：

1. 确认修改范围和对应功能模块。
2. 尽量保持订阅数据、节点数据和运行时配置之间的职责清晰。
3. 为数据结构或数据库变更提供必要的迁移逻辑。
4. 运行相关 Gradle 构建或测试任务。
5. 在 PR 描述中注明实际完成的功能和未验证的部分。

## 免责声明

本项目仅提供代理客户端及相关配置管理功能。使用者应遵守所在国家或地区的法律法规，并对自行导入的订阅、节点和规则配置负责。
