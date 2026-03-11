<div align="center">

![AllMusic_Server](https://socialify.git.ci/Coloryr/AllMusic_Server/image?description=1&font=Inter&forks=1&logo=https%3A%2F%2Fgithub.com%2FColoryr%2FAllMusic_Server%2Fblob%2Fmain%2Fforge_1_12_2%2Fsrc%2Fmain%2Fresources%2Ficon.png%3Fraw%3Dtrue&name=1&owner=1&pattern=Signal&stargazers=1&theme=Auto)

![](https://img.shields.io/bstats/players/6720?label=players&style=for-the-badge)
![](https://img.shields.io/bstats/servers/6720?label=servers&style=for-the-badge)
![](https://img.shields.io/badge/Version-3.8.0-blue?style=for-the-badge)
![](https://img.shields.io/github/actions/workflow/status/Coloryr/AllMusic_Server/gradle.yml?style=for-the-badge)
![](https://img.shields.io/github/license/Coloryr/AllMusic_Server?style=for-the-badge)

</div>

**不提供技术支持**

**仅适用于中国大陆网络环境**

![GIF.gif](img/GIF.gif)

需要配合客户端模组使用：
[AllMusic_Client](https://github.com/Coloryr/AllMusic_Client)

本项目是原项目的 fork。
当前这个仓库主要维护服务端侧，客户端 / 模组侧仍然使用原项目提供的 `AllMusic_Client`，这里没有单独维护新的客户端模组。

## 当前代码支持的平台

以下内容按仓库中当前仍在维护的模块和构建脚本整理。

### 服务端插件

- Bukkit / Spigot / CraftBukkit
- Paper
- Folia
- Velocity

> 旧版 README 提到的 BungeeCord 目前在仓库中没有对应的可用实现，`server_top` 现为 Velocity 端实现，因此这里不再写 BungeeCord。

### 服务端模组

- Forge 1.7.10
- Forge 1.12.2
- Forge 1.16.5
- Forge 1.20.1
- NeoForge 1.21
- NeoForge 1.21.6
- NeoForge 1.21.11
- Fabric 1.16.5
- Fabric 1.20.1
- Fabric 1.21
- Fabric 1.21.6
- Fabric 1.21.11
- Fabric 26.1 snapshot

### 当前内置音源 API

- 网易云音乐 `netease`
- QQ 音乐 `qq`
- 酷狗音乐 `kugou`
- 酷我音乐 `kuwo`

## 安装方法

### 1. 安装服务端

- Bukkit / Spigot / Paper / Folia / Velocity：把对应构建产物放进 `plugins/`
- Forge / NeoForge / Fabric：把对应构建产物放进 `mods/`

当前构建产物命名大致如下：

- `[bukkit_spigot]AllMusic_Server-xxx.jar`
- `[paper]AllMusic_Server-xxx.jar`
- `[folia]AllMusic_Server-xxx.jar`
- `[velocity]AllMusic_Server-xxx.jar`
- `[forge-版本]AllMusic_Server-xxx.jar`
- `[neoforge-版本]AllMusic_Server-xxx.jar`
- `[fabric-版本]AllMusic_Server-xxx.jar`

### 2. 安装客户端

客户端仍使用原项目的 `AllMusic_Client`。

将对应版本的 `AllMusic_Client` 放入客户端 `mods/` 后重启游戏。

## Cookie 导入

VIP 曲目依赖 `cookie.json`。当前代码里已经支持直接通过命令导入，不需要再手动覆盖旧格式文件。

支持导入的 API：

- `netease`
- `qq`
- `kugou`
- `kuwo`

这些命令都会把结果写入插件/模组数据目录下的 `cookie.json`。

### 方法 1：直接导入 Cookie Header

适用平台：全部平台

命令：

```text
/music cookie <api> <cookie header>
```

示例：

```text
/music cookie netease MUSIC_U=xxxx; __csrf=xxxx
/music cookie qq uin=xxxx; qm_keyst=xxxx
```

也支持这些别名命令：

```text
/music neteasecookie <cookie header>
/music qqcookie <cookie header>
/music kugoucookie <cookie header>
/music kuwocookie <cookie header>
```

这里的 `<cookie header>` 必须是标准请求头格式，例如：

```text
name1=value1; name2=value2; name3=value3
```

### 方法 2：本地浏览器辅助导入

适用平台：全部平台

命令：

```text
/music importcookie <api>
```

示例：

```text
/music importcookie netease
```

执行后服务端会返回一个本地地址，例如 `http://127.0.0.1:xxxxx/import?...`。

使用流程：

1. 在运行游戏的本机浏览器里打开这个本地地址。
2. 再按页面提示打开目标音乐站点并登录。
3. 使用页面提供的 bookmarklet、控制台脚本，或手动粘贴 cookie。
4. 成功后会自动写入 `cookie.json`。

注意：

- 这个方法只能拿到浏览器 JavaScript 可见的 cookie。
- `HttpOnly` cookie 无法通过这个页面直接读取。
- 如果站点关键 cookie 是 `HttpOnly`，请改用“方法 1”手动导入，或用“方法 3”读取浏览器数据库。

### 方法 3：从 Windows 浏览器 Cookie 数据库导入

适用平台：仅 Windows

命令：

```text
/music importcookiedb <api>
```

示例：

```text
/music importcookiedb netease
```

当前实现说明：

- 仅支持 Windows
- 仅读取 Chromium 系浏览器配置
- 当前代码会尝试读取 Microsoft Edge 和 Google Chrome
- 结果会合并写入 `cookie.json`

已知限制：

- 新版 Chromium 如果使用 application-bound encryption（`v20`），该方式可能失败
- 浏览器数据库被锁定时会尝试复制临时库，但仍可能失败

## 管理员权限说明

Cookie 导入命令属于管理员命令。

- Bukkit / Spigot / Paper / Folia：`OP` 或 `config.json` 的 `adminList`
- Forge / NeoForge / Fabric：权限等级 `2` 或 `config.json` 的 `adminList`
- Velocity：控制台或 `config.json` 的 `adminList`

## 常用命令

普通玩家：

- `/music <音乐ID或分享链接>`
- `/music <api> <音乐ID>`
- `/music search <关键字>`
- `/music search <api> <关键字>`
- `/music list`
- `/music stop`
- `/music vote`
- `/music cancel <序号>`
- `/music hud ...`

管理员：

- `/music reload`
- `/music next`
- `/music test <id>`
- `/music test <api> <id>`
- `/music addlist <歌单ID>`
- `/music clearlist`
- `/music ban <id>`
- `/music unban <id>`
- `/music cookie <api> <cookie header>`
- `/music importcookie <api>`
- `/music importcookiedb <api>`

## 构建

当前仓库自带的 `build.cmd` 按现状是 Windows 构建脚本。

要求：

- Windows
- 已安装可供 Gradle 使用的 JDK
- 不同模块使用的 Java 版本不同，当前仓库内可见的目标版本主要是 Java 8 / 17 / 21

执行：

```bat
build.cmd
```

脚本会先执行 `link.cmd`，再让你选择要构建的平台模块。
