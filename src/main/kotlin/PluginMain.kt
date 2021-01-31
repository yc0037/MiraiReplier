package org.lyc.miraiReplier

import java.io.File
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info
import kotlin.math.floor
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.PlainText
import java.lang.reflect.*

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "org.lyc.mirai-replier",
        name = "MiraiReplier",
        version = "0.1.0",
    )
) {
    val configDir = "config/MiraiReplier"
    val helpMessage = "Mirai Replier by lyc v0.1.0\n" +
        "用法：.mrsr <keyword> <type> <reply1> <reply2> ...\n" +
        "如果参数type和reply留空，那么删除keyword对应的回复\n" +
        "通过聊天窗口设置自动回复信息时，回复文本中不能包含空格。" +
        "如果有需要，请手动编辑config/MiraiReplier/Reply.json" +
        "然后通过【.mrsr load】指令加载配置\n" +
        "更多帮助信息请参考https://github.com/yc0037/mirai-replyer\n" +
        "本插件目前正在测试阶段，如有问题请在GitHub上提Issue"
    val errorMessage =
        "指令格式错误！用法：.mrsr <keyword> <type> <reply1> <reply2> ...\n" +
        "例如：.mrsr 关键词 PLAIN 回复1 回复2\n" +
        "输入【.mrsr】查看用法说明"
    val illegalParamMessage = "参数<type>不合法！只允许三种取值：\n" +
        "PLAIN - 精确匹配关键词\n" +
        "REG - 通过正则表达式匹配关键词\n" +
        "CONTAINS - 只要包含关键词即可触发回复"
    var replier: Replier? = null
    override fun onEnable() {
        // 创建配置目录和文件
        val dir = File(configDir)
        if (!dir.exists() || !dir.isDirectory) {
            dir.mkdir()
        }
        val f = File("${configDir}/Reply.json")
        if (!f.exists()) {
            f.createNewFile()
            f.writeText("{}")
        }
        // 加载自定义回复数据
        replier = Replier(f)
        logger.info { "已读取配置文件${f.path}" }
        // 加载配置
        MiraiReplierConfig.reload()
        // 注册指令
        manageAdminCommand.register()
        listAdminCommand.register()
        // 绑定群消息事件监听
        val groupChannel = GlobalEventChannel.filter { it is GroupMessageEvent }
        groupChannel.subscribeAlways<GroupMessageEvent> { event ->
            val msg = event.message.contentToString()
            // 检查是否触发回复
            val reply = replier!!.reply(msg)
            if (reply != null) {
                group.sendMessage(PlainText(reply))
            }
        }
        // 绑定修改回复消息事件监听
        val managerList = MiraiReplierConfig.admins
        val manageChannel = GlobalEventChannel.filter {
            it is FriendMessageEvent &&
            managerList.contains(it.sender.id.toString())
        }
        manageChannel.subscribeAlways<FriendMessageEvent> { event ->
            val msg = event.message.contentToString()
            logger.info("Receive Message from ${subject.nick}: $msg")
            if (".mrsr" == msg.substring(0, 5) || "。mrsr" == msg.substring(0, 5)) {
                var args = msg.substring(5).trim().split(Regex("\\s+"))
                args = args.filter { it -> it != "" }
                if (args.isEmpty()) {
                    subject.sendMessage(helpMessage)
                }
                else if (args.size == 1) {
                    if (args[0] == "load") {
                        replier = Replier(f)
                        subject.sendMessage("[√] 已加载配置文件")
                    } else {
                        replier!!.modifyReply(args[0])
                        subject.sendMessage("[√] 已移除关键词【${args[0]}】的回复")
                    }
                }
                else if (args.size < 3) {
                    subject.sendMessage(errorMessage)
                } else {
                    var type: ReplyType?
                    if (args[1].toUpperCase() == "PLAIN") {
                        type = ReplyType.PLAIN
                    } else if (args[1].toUpperCase() == "REG") {
                        type = ReplyType.REG
                    } else if (args[1].toUpperCase() == "CONTAINS") {
                        type = ReplyType.CONTAINS
                    } else {
                        type = null
                        subject.sendMessage(illegalParamMessage)
                    }
                    val replyList = ArrayList<String>()
                    for (i in 2 until args.size) {
                        replyList.add(args[i])
                    }
                    if (type != null) {
                        replier!!.modifyReply(args[0], replyList, type)
                        subject.sendMessage("[√] 已设置关键词【${args[0]}】的回复")
                    }
                }
            }
        }
    }

    override fun onDisable() {
        replier?.writeConfig()
    }
}

object MiraiReplierConfig: AutoSavePluginConfig("admin.conf") {
    var admins: List<String> by value()
}

enum class ReplyType {
    PLAIN, REG, CONTAINS
}

class Replier(private val file: File) {

    interface ReplyItem {
        val keyword: String
        val replies: List<String>
        fun check(word: String): Boolean
        fun reply(): String {
            val ranIdx = floor(Math.random() * replies.size).toInt()
            return replies[ranIdx]
        }
        fun addReply(r: String)
        fun resetReply()
    }

    class ReplyItemJson(val keyword: String, val type: ReplyType) {
        var replies: List<String> = ArrayList<String>()
    }

    class ReplyItemJsonAdapter {
        @FromJson fun replyItemFromJson(json: ReplyItemJson): ReplyItem {
            val res: ReplyItem
            if (json.type == ReplyType.PLAIN) {
                res = PlainTextReplyItem(json.keyword)
            } else if (json.type == ReplyType.REG) {
                res = RegReplyItem(json.keyword)
            } else if (json.type == ReplyType.CONTAINS) {
                res = ContainsReplyItem(json.keyword)
            } else {
                throw Exception("Unexpected reply type: ${json.keyword}")
            }
            for (item in json.replies) {
                res.addReply(item)
            }
            return res
        }
        @ToJson fun replyItemToJson(ri: ReplyItem): ReplyItemJson {
            val type: ReplyType
            if (ri is PlainTextReplyItem) {
                type = ReplyType.PLAIN
            } else if (ri is RegReplyItem) {
                type = ReplyType.REG
            } else if (ri is ContainsReplyItem) {
                type = ReplyType.CONTAINS
            } else {
                throw Exception("Unknown reply type!")
            }
            val ret = ReplyItemJson(ri.keyword, type)
            ret.replies = ri.replies.toList()
            return ret
        }
    }

    class PlainTextReplyItem(override val keyword: String): ReplyItem {
        override val replies = ArrayList<String>()
        override fun check(word: String): Boolean {
            return word == keyword
        }
        override fun addReply(r: String) {
            replies.add(r)
        }
        override fun resetReply() {
            replies.clear()
        }
    }

    class RegReplyItem(override val keyword: String): ReplyItem {
        override val replies = ArrayList<String>()
        override fun check(word: String): Boolean {
            return Regex(keyword).matches(word)
        }
        override fun addReply(r: String) {
            replies.add(r)
        }
        override fun resetReply() {
            replies.clear()
        }
    }

    class ContainsReplyItem(override val keyword: String): ReplyItem {
        override val replies = ArrayList<String>()
        override fun check(word: String): Boolean {
            return word.contains(keyword)
        }
        override fun addReply(r: String) {
            replies.add(r)
        }
        override fun resetReply() {
            replies.clear()
        }
    }

    fun modifyReply(keyword: String) {
        replyMap.remove(keyword)
        writeConfig()
    }

    fun modifyReply(keyword: String, texts: ArrayList<String>, type: ReplyType) {
        val newReply: ReplyItem
        if (type == ReplyType.PLAIN) {
            newReply = PlainTextReplyItem(keyword)
        } else if (type == ReplyType.REG) {
            newReply = RegReplyItem(keyword)
        } else if (type == ReplyType.CONTAINS) {
            newReply = ContainsReplyItem(keyword)
        } else {
            throw Exception("Illegal Reply Type!")
        }
        for (text in texts) {
            newReply.addReply(text)
        }
        replyMap[keyword] = newReply
        writeConfig()
    }

    fun writeConfig() {
        val type: Type = Types.newParameterizedType(Map::class.java, String::class.java, ReplyItem::class.java)
        val jsonAdapter: JsonAdapter<Map<String, ReplyItem>> = moshi.adapter(type)
        val json = jsonAdapter.toJson(replyMap)
        file.writeText(json)
        logger.info("已写入配置文件${file.path}")
    }

    fun reply(msg: String): String? {
        for (ri in replyMap.values) {
            if (ri.check(msg)) {
                return ri.reply()
            }
        }
        return null
    }

    private val logger = PluginMain.logger
    private var replyMap = HashMap<String, ReplyItem>()
    private val moshi = Moshi.Builder()
        .add(ReplyItemJsonAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    init {
        val config = file.readText()
        // 解析 config
        val type: Type = Types.newParameterizedType(Map::class.java, String::class.java, ReplyItem::class.java)
        val jsonAdapter: JsonAdapter<Map<String, ReplyItem>> = moshi.adapter(type)
        replyMap = HashMap<String, ReplyItem>(jsonAdapter.fromJson(config)?.toMap())
    }
}

object manageAdminCommand: SimpleCommand(
    PluginMain,
    "setAdmin",
    "setAdmin",
    description = "添加或删除管理员",
) {
    val logger = PluginMain.logger
    @Handler
    suspend fun CommandSender.handle(action: String, userid: String) {
        if (action == "add") {
            val adminList = ArrayList<String>(MiraiReplierConfig.admins)
            if (!adminList.contains(userid)) {
                adminList.add(userid)
                MiraiReplierConfig.admins = adminList
                logger.info("[√] 已添加${userid}为管理员")
            }
        } else if (action == "remove") {
            val adminList = ArrayList<String>()
            for (uid in MiraiReplierConfig.admins) {
                if (uid != userid) {
                    adminList.add(uid)
                }
            }
            MiraiReplierConfig.admins = adminList
            logger.info("[√] 已移除管理员${userid}")
        } else {
            logger.error("<action>参数不合法：值只能为\"add\"(增加管理员)或\"remove\"(移除管理员)")
        }
    }
}

object listAdminCommand: SimpleCommand(
    PluginMain,
    "listAdmin",
    "listAdmin",
    description = "查看当前管理员",
) {
    val logger = PluginMain.logger
    @Handler
    suspend fun CommandSender.handle() {
        val adminList = MiraiReplierConfig.admins
        println("$adminList")
    }
}