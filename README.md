# Mirai Replier
基于[Mirai](https://github.com/mamoe/mirai)开发的 QQ 关键词回复插件。

## 更新

### 2021-01-31

更新了 v0.1.0 版本。

## 使用

目前，Mirai Replier 只响应 QQ 群中的关键词。私聊中不会触发关键词。

### 安装插件

将 `jar` 文件放入 `plugins` 目录下，重启 Mirai Console 即可。

### 控制台指令

#### `/listAdmin`

查看当前管理员的 ID（QQ 号）。

用法：`/listAdmin`

#### `/setAdmin`

增加或移除管理员。

用法：`/setAdmin <action> <userid>`，其中参数 `<action>` 只能为 `add` 或 `remove`

### 聊天窗口指令

Mirai Replier 响应的聊天窗口指令需要满足如下所有条件：

- 发送者是 bot 的好友
- 发送者在私聊窗口中发送指令
- 发送者在管理员列表（可以通过 `/listAdmin` 查看）中

#### 查看插件信息

用法：`.mrsr`

#### 添加关键词回复

用法：`.mrsr <keyword> <type> <reply1> <reply2> ...`

参数之间均用空格隔开。**关键字和回复文本中不能包含空格**。

##### `<type>`

Mirai Replier 支持三种匹配关键词的模式。通过 `<type>` 参数来指定当前关键词通过何种模式进行匹配和触发。`<type>` 参数可能的取值有（大小写不敏感）：

- `PLAIN`：精确匹配关键字。即当消息内容与关键字完全相同时才会触发回复。

- `REG`：正则匹配关键字。此时关键字是一个正则表达式。当消息内容能够匹配该正则表达式时触发回复。

- `CONTAINS`：模糊匹配关键字。即当消息内容包含关键字时触发回复。

#### 移除关键字回复

用法：`.mrsr <keyword>`

#### 重新加载外部配置文件

用法：`.mrsr load`

### 配置文件

除了使用聊天窗口指令之外，Mirai Replier 还允许直接编辑外部配置文件来定义关键词回复。

Mirai Replier 的关键词回复配置文件为 `$root/config/MiraiReplier/Reply.json`，其中`$root`是 Mirai 运行的根目录。

> 建议使用 VS Code 等文本编辑器来编辑配置文件。

在配置文件中，每个关键词通过下面这样的键值对来表示：

``` json
"关键词": {
    "keyword": "关键词",
    "type": "PLAIN",
    "replies": [
        "回复1",
        "回复2",
        "回复3"
    ]
}
```

其中，键和 `keyword` 的值**必须相等**。`type` 属性规定关键词的匹配方式（大小写敏感，必须全为大写），可能的取值包括 `PLAIN`, `REG` 和 `CONTAINS`。`replies` 属性列出关键词触发后回复的文本。关键词触发后，Mirai Replier 将在 `replies` 数组中随机选择一项进行回复。

如果有多个关键词匹配，Mirai Replier 会选择配置文件中位置最靠前的一个。

目前回复只支持纯文本（`PlainText`）。将来计划支持更多格式。

如果在 Mirai Console 运行过程中更新外部配置文件，那么需要通过 `.mrsr load` 指令重新加载外部配置文件，否则修改不会生效。
