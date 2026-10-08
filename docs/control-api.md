# 本机灯光控制接口 v1

应用版本 1.1 起提供；协议版本为 `1`，独立于应用版本。此接口面向其他 Android 应用，支持实现本软件没有的灯效、通知提示和应用状态灯。

## 开启与权限

在灯环工坊的「关于 → 其他应用控制灯光」开启接口。默认关闭。开启后任何本机应用均可申请控制，没有签名限制，不要求调用者 Root，也不要求申请自定义 Android 权限。只有灯环工坊需要 Magisk 授权。

接口只开放灯光能力，不传递 Root 权限，不提供任意 Shell、文件、寄存器、I²C 地址、刷机或分区写入。它是本机 Binder 接口，没有网络监听端口。

接口关闭时仍可查询能力和状态，所有控制命令返回 `DISABLED`。关闭开关会回收外部会话，默认恢复接管前状态。主界面选择灯效会回收外部会话并立即运行所选灯效；主界面 Logo 设置保持外部环灯继续运行。

## 绑定

使用显式 `ComponentName`，Action 必须为 `com.codex.ringlab.CONTROL`。

| 项目 | 值 |
| --- | --- |
| 包名 | `com.codex.ringlab` |
| 服务类 | `com.codex.ringlab.PublicControlService` |
| 协议 | Android `Messenger`，`Message` 的 `Bundle` 数据 |
| 版本 | `api_version`：int，当前 `1` |
| 回调 | 每条请求的 `replyTo` 必须设置为调用应用的 Messenger |
| 请求标识 | `Message.arg1`，回复原样返回 |
| 命令 | `Message.what`，回复原样返回 |

Android 11 及以上、调用应用 targetSdk ≥ 30 时建议声明包可见性：

```xml
<queries>
    <package android:name="com.codex.ringlab"/>
</queries>
```

```java
Intent intent = new Intent("com.codex.ringlab.CONTROL");
intent.setComponent(new ComponentName("com.codex.ringlab", "com.codex.ringlab.PublicControlService"));
context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
```

绑定不会点灯或申请 Root。成功取得控制会话时才初始化驱动；首次等待 Root 授权可能耗时，调用者应异步等待回复。不要在主线程阻塞等待。

## 回复

| Bundle 字段 | 类型 | 含义 |
| --- | --- | --- |
| `api_version` | int | 协议版本 |
| `ok` | boolean | 成功或失败 |
| `code` | String | `OK` 或错误码 |
| `message` | String | 人可读提示，不用此字段判断程序流程 |
| `data` | String | 可选 JSON 对象，成功结果或查询数据 |

控制请求成功表示已接收并通过验证。帧更新在灯光调度的下一次 tick 生效；不能把确认时间当作硬件点亮时间。后续驱动错误可通过状态查询获得。

驱动异常时关闭灯光并清除会话，不尝试自动恢复点亮；排查 Root 与硬件后再申请控制。

## 命令

| what | 名称 | 需要会话 | 作用 |
| --- | --- | --- | --- |
| 1 | HELLO | 否 | 能力与协议查询 |
| 2 | STATE | 否 | 状态与当前硬件通道查询 |
| 10 | ACQUIRE | 否 | 申请单一控制会话 |
| 11 | EFFECT | 是 | 设置内置效果及参数 |
| 12 | FRAME | 是 | 自定义 RGB／白灯或原始通道帧 |
| 13 | LOGO | 是 | 独立设置 Logo |
| 14 | KEEPALIVE | 是 | 续租，不改变灯光 |
| 15 | RELEASE | 是 | 归还控制，可恢复此前本地效果 |
| 16 | OFF | 是 | 关闭全部灯光并回收会话 |

所有需要会话的命令均携带 `session_id`（String）。真实调用 UID 由 Android `Message.sendingUid` 提供；伪造 Bundle 中的 UID 没有作用。

### HELLO

返回 `api_version`、`app_version`、`enabled`、`mapping_id`、`rgb_pixels`、`white_pixels`、`raw_channels`、`white_offset_degrees`、`maximum_fps`、租约上下限、`formats`、`effects`、`palettes` 和 `features`。

本版测试映射为 `n5d-clockwise-2026-10`，RGB 与白灯各 24 个位置、原始通道 96 个、输出上限 25 fps。其他型号应先核对能力与映射，不能仅按外观假设兼容。

### STATE

返回 `ready`、`running`、`ring_enabled`、`mode`、`status`、`external_control`、`owner_uid`、`owner`、当前 `settings`、`brightness`、`white_brightness`、`logo_mode`、`logo_level`、`logo_actual`、`frames`、`chip_configuration` 和 `channels`。

`channels` 是当前映射后 96 个 DIM 值，不是顺时针 RGB 像素数组；亮度需要同时参考两个 FADE 字段。`mode=external` 表示外部帧模式。查询无需 Root，也不会启动灯光。

### ACQUIRE

`lease_ms`：int，默认 10000，范围 2000～60000 毫秒。`restore_on_end`：boolean，默认 true。正常归还、进程死亡、租约到期或接口关闭时恢复此前状态；设为 false 则关灯。返回 `session_id`、实际 `lease_ms`、`restore_on_end` 与 `owner`。已有会话时返回 `BUSY`，包括同一 UID 再次申请；先 RELEASE 后再申请。

申请会话时保存接管前的本地设置，并保持当前灯光帧，随后由调用者发送 EFFECT 或 FRAME。会话同时绑定实际 UID、随机 ID 和申请时的回调 Binder。不要把 `replyTo` 设置为其他进程的代理 Binder。

### EFFECT

`settings`：String，最多 4096 字符的 JSON 对象。未提供的字段保留当前值。只接受以下字段；未知字段、未知效果、类型不匹配或越界返回 `INVALID_ARGUMENT`。

| 字段 | 类型 | 范围或值 |
| --- | --- | --- |
| `mode` | String | HELLO 返回的效果 ID |
| `brightness`, `white` | int | 0～255，彩灯／白灯亮度 |
| `speed` | int | 1～100 |
| `palette` | int | 0～13，顺序见 HELLO palettes |
| `colors` | String | 1～8 个六位 HEX，逗号分隔 |
| `color_count` | int | 1～8 |
| `bank` | int | 0 彩灯、1 白灯、2 混合 |
| `reverse` | boolean | 反向运动 |
| `tail` | int | 1～24 |
| `softness` | int | 0～100 |
| `width` | int | 8～80 |
| `waves` | int | 1～6 |
| `density` | int | 5～90 |
| `floor` | int | 0～80 |
| `source` | int | 0 麦克风、1 本机播放 |
| `gain`, `release` | int | 0～100 |
| `gate` | int | 30～85 |

`palette=13` 表示自定义；同时提供 `colors` 和 `color_count` 可避免默认色数变化。进入音乐效果或 Logo 跟随音乐前，需要在灯环工坊主界面授予其音频权限。接口不会弹出授权窗口，也不使用调用应用的音频权限。

```json
{"mode":"comet","palette":13,"colors":"#00D9CF,#FFAA00","color_count":2,"brightness":96,"white":35,"bank":2,"tail":12,"softness":80}
```

### FRAME

发送完整帧，不是增量帧。DIM 数据使用 `byte[]`；Java 有符号 byte 在服务中按 `& 255` 解读。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `format` | String | `rgb_white`（默认）或 `raw96` |
| `rgb` | byte[] | rgb_white：恰好 72 字节，RGBRGB 顺序 |
| `white` | byte[] | rgb_white：恰好 24 字节 |
| `channels` | byte[] | raw96：恰好 96 字节 |
| `brightness` | int | 0～255，默认 255，RGB FADE |
| `white_brightness` | int | 0～255，默认 255，白灯 FADE |
| `logo` | int | 可选 0～255；提供则切为固定 Logo；省略保持 Logo 设置 |
| `sequence` | long | 可选非负递增序号；省略自动递增；重复／回退返回 STALE_FRAME |

`rgb_white` 按设备正面观察：RGB 位置 0 为顶部 12 点方向，顺时针递增；白灯位置 0 在 RGB 位置 0 后顺时针约 7.5°，随后同样顺时针递增。白灯与 RGB 不是共址，因此扩散后的视觉混光与同封装 RGBW 不完全相同。

服务缓存最新完整帧，每约 40 ms 输出一次，不排队播放历史帧。调用者自行实现动画并按 ≤25 fps 发送；服务保持最后一帧直到新帧或租约到期。不要通过超速发帧模拟更高刷新率。高亮度运行需考虑实际供电与发热。

raw96 的 DIM 顺序、BGR 接线与映射在 [硬件说明](hardware.md)；能力查询中的 mapping_id 用于辨认映射版本。

### LOGO

`logo_mode`：int，0 关闭、1 固定、2 跟随环灯、3 跟随音乐。`logo_level`：int，0～255。省略字段保持原值。此命令不重启环灯相位或同一音源采集。

### KEEPALIVE / RELEASE / OFF

有效 EFFECT、FRAME、LOGO、KEEPALIVE 都按原租约长度续租，租约不累加。只维持内置效果时建议每 2～3 秒心跳一次（租约应大于心跳间隔），不要只发送 STATE；查询不会续租。

RELEASE 的 `resume_local`：boolean，默认跟随 ACQUIRE 的 restore_on_end（默认 true）。默认恢复接管前的本地环灯与 Logo；false 关闭全部灯光。外部设置不覆盖本地保存的普通／音乐效果。会话期间用户在主界面调整 Logo 时，恢复使用用户最新的 Logo 设置。

OFF 始终关闭环灯和 Logo，并回收会话。会话回收后旧 ID 返回 `NOT_OWNER`。客户端进程死亡、租约超时或用户关闭接口会回收会话，按 restore_on_end 恢复或关灯；主界面全关／退出始终关灯；主界面选效果会回收会话并切换到本地效果。恢复也保留原来的开关状态和动画相位基准，原来关闭就保持关闭。

解绑本身不代表进程死亡。调用者应在结束时显式 RELEASE，等待回复再解绑；异常结束由 Binder 死亡或租约处理。不依赖 `onDestroy` 一定执行。

## 错误与限制

| code | 处理建议 |
| --- | --- |
| `DISABLED` | 提示用户进入关于页开启 |
| `BUSY` | 控制已占用或等待队列已满，稍后重试 |
| `NOT_OWNER` | 旧会话、错误 UID 或 ID，重新申请 |
| `INVALID_ARGUMENT` | 修正字段、类型、范围与字节数组长度 |
| `STALE_FRAME` | 使用递增 long 序号 |
| `AUDIO_PERMISSION_REQUIRED` | 在灯环工坊内授权音频后重试 |
| `RATE_LIMIT` | 降低请求频率 |
| `DRIVER_ERROR` | 查询状态，检查 Root、型号和驱动；不绕过校验 |
| `UNSUPPORTED_VERSION` | 先 HELLO，使用支持的版本 |
| `UNKNOWN_COMMAND` | 按能力与协议使用命令 |
| `INVALID_CALLER`, `UNAVAILABLE` | 重新绑定，检查回调进程与服务状态 |

控制入口全局每秒最多 60 条请求、最多 32 条等待请求。此限制用于避免无限排队，不能保证恶意本机应用下的服务可用性。能力与查询请节制轮询；推荐状态查询 ≤2 Hz。

## Java 调用示例

可直接复制 [RingClient.java](../examples/client/RingClient.java)，使用主线程异步回调。示例 helper 不自动点灯、不自动申请控制；业务应用负责心跳和结束时归还控制。

```java
RingClient client = new RingClient(context);
client.connect(hello -> {
    if (!hello.getBoolean("ok")) return;
    Bundle acquire = new Bundle();
    acquire.putInt("lease_ms", 10000);
    client.send(10, acquire, acquired -> {
        if (!acquired.getBoolean("ok")) return;
        try {
            String session = new JSONObject(acquired.getString("data")).getString("session_id");
            Bundle frame = new Bundle();
            frame.putString("session_id", session);
            frame.putString("format", "rgb_white");
            byte[] rgb = new byte[72];
            byte[] white = new byte[24];
            rgb[0] = (byte) 255;
            white[0] = (byte) 80;
            frame.putByteArray("rgb", rgb);
            frame.putByteArray("white", white);
            frame.putInt("brightness", 96);
            frame.putInt("white_brightness", 40);
            frame.putLong("sequence", 1L);
            client.send(12, frame, result -> handleResult(result));
        } catch (JSONException e) {
            handleError(e);
        }
    });
});
```

上例 `handleResult`、`handleError` 是业务应用自己的处理函数。把 session 保存在业务对象中，持续发帧或 KEEPALIVE。

```java
Bundle release = new Bundle();
release.putString("session_id", session);
release.putBoolean("resume_local", true);
client.send(15, release, result -> client.close());
```

## 版本维护

协议 v1 的命令编号和现有字段语义保持稳定。新增能力通过 HELLO 的 features、formats 与映射 ID 宣告；调用者先检查能力，再启用可选功能。未来不兼容更改使用新的 api_version。不要用应用版本字符串判断协议兼容性。

## 参考

通信模型参照 [Android 绑定服务](https://developer.android.com/develop/background-work/services/bound-services) 与 [Message.sendingUid](https://developer.android.com/reference/android/os/Message#sendingUid)。包可见性见 [Android queries](https://developer.android.com/training/package-visibility/declaring)。
