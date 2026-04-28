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
- 如果站点关键 cookie 是 `HttpOnly`，请改用"方法 1"手动导入，或用"方法 3"读取浏览器数据库。

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

1. 安装JDK25、Git
2. 使用 `git submodule update --init --recursive` 初始化项目
3. 使用 `gradlew build` 构建

## 配置文件说明

配置文件采用json格式，需要遵守json编写的格式规范  
- maxPlayList              最大歌曲数  
- maxPlayerList            一个玩家最大可点数量，0代表不限制
- minVote                  最小通过投票数
- voteTime                 投票时间
- lyricDelay               歌曲延迟，单位毫秒
- defaultAddMusic          默认添加歌曲方式，1为搜歌
- ktvLyricDelay            KTV模式歌词延迟，单位毫秒
- adminList                管理员列表
- muteServer               不参与点歌的服务器列表
- mutePlayer               不参与点歌的玩家列表
- banMusic                 禁止点歌ID列表，ID为音乐ID
- banPlayer                禁止玩家点歌列表
- playListSwitch           是否玩家点歌后是否直接从空闲歌单切换至玩家歌曲
- playListRandom           是否空闲歌单随机播放
- sendLyric                是否发送歌词到客户端
- needPermission           是否指令需要权限
- topAPI                  是否启用顶层模式，用于和BC交换数据
- mutePlayMessage          是否不发送播放信息
- muteAddMessage           是否不发送点歌信息
- showInBar                是否将信息限制在bar处
- ktvMode                  是否启用KTV歌词
- musicBR                  歌曲音质
- version                  配置文件版本号
- defaultHud               默认Hud配置
    - list                 播放列表Hud配置
        - x                x轴间距
        - y                y轴间距
        - dir              对齐方式
        - color            字体颜色
        - shadow           是否显示字体阴影
        - enable           是否启用
    - lyric                歌词Hud配置
        - x
        - y
        - dir  
        - color 
        - shadow 
        - enable 
    - info                 歌曲信息Hud配置
        - x
        - y
        - dir
        - color
        - shadow
        - enable
    - pic                  图片显示配置
        - x
        - y
        - dir
        - color            图片尺寸
        - shadow           是否开启图片旋转
        - enable
    - picRotateSpeed       图片旋转速度
- economy                  经济扩展配置
    - mysqlUrl             目前无用
    - backend              目前无用
    - vault                是否使用vault插件
- funConfig                娱乐选项
    - rain                 是否启用随机下雨
    - rainRate             随机下雨概率
- limit                    限制设置
    - messageLimit         是否启用广播消息长度限制
    - messageLimitSize     广播消息限制长度
    - listLimit            是否启用歌曲列表长度限制
    - listLimitSize        歌曲列表限制长度
    - infoLimit            是否启用信息长度限制
    - infoLimitSize        信息长限制长度
    - musicTimeLimit       是否启用歌曲长度限制
    - maxMusicTime         限制最长歌曲长度，单位秒
    - limitText            限制长度替换文本
- cost                     花费相关配置
    - searchCost           搜歌花费
    - addMusicCost         点歌花费
    - useCost              启用花费
- sendDelay                Hud信息更新延迟
- defaultApi               默认音乐API

## 指令说明

普通玩家指令  
- /music [音乐ID/网易云分享链接] 点歌
- /music [音乐API] [音乐ID] 点歌
- /music stop 停止播放歌曲
- /music list 查看歌曲队列
- /music cancel [序号] 取消你的点歌
- /music vote 投票切歌
- /music vote cancel 取消发起的切歌
- /music push [序号] 投票将歌曲插入到队列头
- /music push cancel 取消发起的插歌
- /music mute 不再参与点歌，再输入一次恢复
- /music mute list 不接收空闲列表点歌，再输入一次恢复
- /music search [歌名] 搜索歌曲
- /music search [音乐API] [歌名] 搜索歌曲
- /music select [序列] 选择歌曲
- /music nextpage 切换下一页歌曲搜索结果
- /music lastpage 切换上一页歌曲搜索结果
- /music hud enable 启用/关闭全部界面
- /music hud reset 重置全部界面
- /music hud [位置] enable 启用关闭单一界面
- /music hud [位置] pos [x] [y] 设置某个界面的位置
- /music hud [位置] dir [对齐方式] 设置某个界面的对齐方式
- /music hud [位置] color [颜色HEX] 设置某个界面的颜色
- /music hud [位置] reset 重置单一界面
- /music hud pic size [尺寸] 设置图片尺寸
- /music hud pic rotate [开关] 设置图片旋转模式
- /music hud pic speed [数值] 设置图片旋转速度

以下方式才是管理员  
- 在配置文件给自己管理员
- bukkit/spigot/paper/folia 服务器给自己op
- forge/fabric/neoforge 服务器给自己等级权限2
- bc/velocity 只能在配置文件写上自己的游戏名

管理员指令 
- /music reload 重读配置文件
- /music next 强制切歌
- /music ban [ID] 禁止点这首歌
- /music ban [音乐API] [ID] 禁止点这首歌
- /music unban [ID] 解禁点这首歌
- /music unban [音乐API] [ID] 解禁点这首歌
- /music banplayer [ID] 禁止某位玩家点歌
- /music unbanplayer [ID] 解禁某位玩家点歌
- /music delete [序号] 删除队列中的歌曲
- /music addlist [歌单ID] 添加歌单到空闲列表
- /music clearlist 清空空闲歌单
- /music clearban 清空禁止点歌列表
- /music clearbanplayer 清空禁止点歌玩家列表
- /music test [ID] 测试歌曲内容解析
- /music test [音乐API] [ID] 测试歌曲内容解析

若开启权限后  
- 点歌需要权限`allmusic.addmusic`
- 搜歌需要权限`allmusic.search`
- 插歌需要权限`allmusic.push`
- 投票切歌需要权限`allmusic.vote`
