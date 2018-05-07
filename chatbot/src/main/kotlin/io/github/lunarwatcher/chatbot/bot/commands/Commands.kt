@file:Suppress("NAME_SHADOWING")

package io.github.lunarwatcher.chatbot.bot.commands

import com.google.common.base.Strings.repeat
import io.github.lunarwatcher.chatbot.BotCore
import io.github.lunarwatcher.chatbot.Configurations
import io.github.lunarwatcher.chatbot.Constants
import io.github.lunarwatcher.chatbot.bot.ReplyBuilder
import io.github.lunarwatcher.chatbot.bot.chat.BMessage
import io.github.lunarwatcher.chatbot.bot.command.CommandCenter
import io.github.lunarwatcher.chatbot.bot.command.CommandCenter.TRIGGER
import io.github.lunarwatcher.chatbot.bot.listener.StatusListener
import io.github.lunarwatcher.chatbot.bot.sites.Chat
import io.github.lunarwatcher.chatbot.cleanInput
import io.github.lunarwatcher.chatbot.utils.Http
import io.github.lunarwatcher.chatbot.utils.Utils
import io.github.lunarwatcher.chatbot.utils.Utils.random
import org.apache.commons.lang3.StringUtils
import org.apache.http.impl.client.HttpClients
import org.joda.time.*
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

const val DATE_PATTERN = "E, d MMMM HH:mm:ss.SSSS Y z ('GMT' ZZ)"

val formatter = SimpleDateFormat(DATE_PATTERN, Locale.ENGLISH)
const val FLAG_REGEX = "( -[a-zA-Z]+)( \"[a-zA-Z ]+\")?"
var ARGUMENT_PATTERN = Pattern.compile(FLAG_REGEX)!!
const val NO_DEFINED_RANK = -1
interface Command{
    val name: String;
    val aliases: List<String>
    val desc: String;
    val help: String;
    val rankRequirement: Int
    val nsfw: Boolean
    /**
     * Check if the input starts with the name or one of the command's aliases
     */
    fun matchesCommand(input: String) : Boolean;
    /**
     * Handle a given command
     */
    fun handleCommand(input: String, user: User) : BMessage?;
    fun canUserRun(user: User) : Boolean

}

/**
 * Info about a user.
 */
class User(var site: String, var userID: Long, var userName: String, var roomID: Int, var nsfwSite: Boolean = false,
           vararg val args: Pair<String, String> = arrayOf())

/**
 * Utility implementation of [Command]
 */
abstract class AbstractCommand(override val name: String, override val aliases: List<String>,
                               override val desc: String = Constants.NO_DESCRIPTION,
                               override val help: String = Constants.NO_HELP,
                               override val rankRequirement: Int = NO_DEFINED_RANK,
                               override val nsfw: Boolean = false) : Command{

    init {
        if (rankRequirement != NO_DEFINED_RANK) {
            if (rankRequirement < 0 || rankRequirement > 10)
                throw IllegalArgumentException("The rank requirement must be between 0")
        }
    }

    override fun matchesCommand(input: String): Boolean{
        val input = input.toLowerCase();
        val split = input.split(" ");
        if(split[0] == name.toLowerCase()){
            return true;
        }

        return aliases.any{split[0] == it.toLowerCase()}
    }

    /**
     * This would be considerably easier to integrate into a centralized dispatch system (where commands are refered through
     * method instances instead of declaring entire classes for them), but rewriting would take a lot of time. So this
     * method exists to enable classes where ranks appy to easily check for validity vs [rankRequirement]
     */
    override fun canUserRun(user: User) : Boolean {
        if(rankRequirement == NO_DEFINED_RANK && !Utils.isBanned(user.userID, CommandCenter.bot.getChatByName(user.site)!!.config)){
            // rank not defined (req == 1) && user !banned - can run
            return true;
        }else if(rankRequirement != NO_DEFINED_RANK){
            return Utils.getRank(user.userID, CommandCenter.bot.getChatByName(user.site)!!.config) >= rankRequirement
        }
        return false
    }
    fun splitCommand(input: String) : Map<String, String>{
        val iMap = parseArguments(input);
        val initialSplit = input.split(FLAG_REGEX.toRegex())[0];
        val name = initialSplit.split(" ")[0];

        if(name == input){
            //No arguments, just the name
            return mapOf("name" to name.trim())
        }

        val dMap = try{
            val content = initialSplit.substring(name.length + 1/*avoid the space*/)
            mutableMapOf("name" to name.trim(), "content" to content.trim())
        }catch(e: StringIndexOutOfBoundsException){
            mutableMapOf("name" to name.trim())
        }

        if(initialSplit == input){
            return dMap
        }

        //The argument map is empty, meaning the argument parser failed to find any. Just return the name and content
        if(iMap.isEmpty())
            return dMap;

        for(e in iMap)
            dMap[e.key.trim()] = e.value.trim()


        return dMap;

    }

    fun parseArguments(input: String) : Map<String, String>{
        if (input.isEmpty())
            return mapOf()
        val used = input.split(" ")[0]
        if(input.substring(used.length).trim().isEmpty()){
            //no arguments passed
            return mapOf();
        }

        val retval: MutableMap<String, String> = mutableMapOf()
        val matcher = ARGUMENT_PATTERN.matcher(input);
        while(matcher.find()){
            val groups = matcher.groupCount()
            if(groups == 2){
                val g1 = matcher.group(1)
                val g2 = matcher.group(2);

                retval[g1.substring(1, g1.length)] = g2?.substring(1, g2.length) ?: "true"
            }else if(groups == 1){
                val g1 = matcher.group(1)
                retval[g1.substring(1, g1.length)] = "true"
            }
        }

        return retval;
    }

    fun removeName(input: String) : String{
        if(input.substring(name.length).startsWith(" ")){
            //If the name is removed successfully, the string should start with a space
            return input.substring(name.length);
        }

        //otherwise it's an alias

        aliases.forEach { alias->
            if(input.substring(alias.length).startsWith(" ")){
                return input.substring(alias.length);
            }
        }
        //If it can't find in either the name or the alias, that means the command doesn't match.
        //Since this is checked with the matchesCommand check, this last line here should never
        //be called.
        return input;
    }

    fun lowRank() : BMessage
            = BMessage("You need rank $rankRequirement or higher ot use this command.", true)

    fun lowRank(reason: String) : BMessage
            = BMessage("You need rank $rankRequirement or higher ot use this command. Supplied reason: $reason", true)
}

class HelpCommand(var center: CommandCenter, var truncated: Boolean) : AbstractCommand("help", listOf("halp", "hilfen", "help"),
        "Lists all the commands the bot has",
        "Use `" + CommandCenter.TRIGGER + "help` to list all the commands and `" + CommandCenter.TRIGGER + "help [command name]` to get more info about a specifc command\n" +
                "Call `${CommandCenter.TRIGGER}help trucated` for a small version of the help command, or `${CommandCenter.TRIGGER}help full` for the full version. " +
                "Note that not passing full or trucated leads to it defaulting to the site specific settings (for this site, it's ${if(truncated) "truncated" else "full"})"){

    override fun handleCommand(input: String, user: User): BMessage? {
        if(!matchesCommand(input)){
            return null;
        }

        val `in` = splitCommand(input)
        if(`in`.size == 1 ||
                (`in`.size == 2 && `in`.containsKey("content")
                        && (`in`["content"] == "truncated" || `in`["content"] == "full")
                        )){
            val content = `in`["content"]
            val truncated = if(content == null) this.truncated
                        else content == "truncated"

            var commands: MutableMap<String, String> = mutableMapOf()
            val learnedCommands: MutableList<String> = mutableListOf()
            var listeners: MutableMap<String, String> = mutableMapOf();

            if (!center.commands.isEmpty()) {

                for (command: Command in center.commands.values) {
                    commands[command.name] = command.desc;
                }
            }

            if (!CommandCenter.tc.commands.isEmpty()) {
                for (cmd: LearnedCommand in CommandCenter.tc.commands.values) {
                    learnedCommands.add(cmd.name)
                }
            }

            if (!center.listeners.isEmpty()) {
                for (listener in center.listeners) {
                    listeners[listener.name] = listener.description;
                }
            }

            listeners = listeners.toSortedMap()
            commands = commands.toSortedMap()

            if(!truncated) {

                val reply = ReplyBuilder(center.site.name == "discord");
                reply.fixedInput().append("###################### Help ######################")
                        .nl().fixedInput().nl();
                val names: MutableList<String> = mutableListOf()

                names.addAll(commands.keys);
                names.addAll(learnedCommands.toSet())
                names.addAll(listeners.keys);

                val maxLen = getMaxLen(names);

                if (!commands.isEmpty()) {
                    reply.fixedInput().append("==================== Commands").nl()
                    for (command in commands) {
                        reply.fixedInput().append(TRIGGER + command.key);
                        reply.append(repeat(" ", maxLen - command.key.length + 2) + "| ")
                                .append(command.value).nl();
                    }
                }

                if (!learnedCommands.isEmpty()) {
                    reply.fixedInput().append("==================== Learned Commands").nl()
                    reply.fixedInput();
                    for (i in 0 until CommandCenter.tc.commands.values.toList().size) {
                        val command = CommandCenter.tc.commands.values.toList()[i]
                        if (command.nsfw && !user.nsfwSite) {
                            continue
                        }

                        reply.append(command.name);
                        if (command.nsfw)
                            reply.append(" - NSFW");

                        if (i < CommandCenter.tc.commands.size - 1) reply.append(", ")
                    }

                }
                reply.fixedInput().newLine()
                if (!listeners.isEmpty()) {
                    reply.fixedInput().append("==================== Listeners").newLine()

                    for (listener in listeners) {
                        reply.fixedInput().append(listener.key);
                        reply.append(repeat(" ", maxLen - listener.key.length + 2) + "| ")
                                .append(listener.value).nl();
                    }
                }
                return BMessage(reply.toString(), false);
            }else{
                val builder = ReplyBuilder()
                builder.append("**Commands**: ")
                builder.append(commands.keys.joinToString(", "))
                        .append(". **User taught commands:** ")
                        .append(learnedCommands.toSortedSet().joinToString(", "))
                        .append(". **Listeners**: " + listeners.keys.joinToString(", "))
                return BMessage(builder.toString(), false)
            }
        }else{
            val cmd = (`in`["content"] ?: return null).toLowerCase();
            val desc: String
            val help: String
            val name: String
            val aliases: String
            //No clue what to call this thing
            val d: String;
            val rank: Int
            val nsfw: Boolean

            when {
                center.isBuiltIn(cmd) -> {
                    desc = center.get(cmd)?.desc ?: return null;
                    help = center.get(cmd)?.help ?: return null;
                    name = center.get(cmd)?.name ?: return null;
                    val aliasBuffer = center.get(cmd)?.aliases ?: return null
                    aliases = if(aliasBuffer.isEmpty())
                        "None"
                    else
                        aliasBuffer.joinToString(", ")
                    rank = center.get(cmd)?.rankRequirement ?: return null

                    d = "Built in command. "
                    nsfw = center.get(cmd)?.nsfw ?: return null
                }
                CommandCenter.tc.doesCommandExist(cmd) -> {
                    desc = CommandCenter.tc.get(cmd)?.desc ?: return null;
                    help = CommandCenter.tc.get(cmd)?.help ?: return null;
                    name = CommandCenter.tc.get(cmd)?.name ?: return null;
                    d = "Taught command. (Taught to the bot by user " + CommandCenter.tc.get(cmd)?.creator + " on " + CommandCenter.tc.get(cmd)?.site + "). "
                    aliases = "None. "
                    rank = 1
                    nsfw = CommandCenter.tc.get(cmd)?.nsfw ?: return null
                }

                else -> return BMessage("The command you tried finding help for (`$cmd`) does not exist. Make sure you've got the name right", true)
            }

            val reply = ReplyBuilder(center.site.name == "discord");

            reply.fixedInput().append(d).append("`$TRIGGER").append(name).append("`: $desc")
                    .nl().fixedInput().append(help)
                    .nl().fixedInput().append("Known aliases: $aliases")
                    .nl().fixedInput().append("Rank required: " +
                            "${if(rank == NO_DEFINED_RANK)
                                "1 (WARNING: Undefined in code. Actual rank may differ from listed)"
                            else rank.toString()} " +
                            "(your rank: ${Utils.getRank(user.userID, center.site.config)})")


            return BMessage(reply.toString(), true);
        }
    }

}

fun getMaxLen(list: MutableList<String>) : Int{
    val longest = list
            .map { it.length }
            .max()
            ?: 0;

    return longest;
}

class ShrugCommand(val shrug: String): AbstractCommand("shrug", listOf("dunno", "what"), "Shrugs", "Use `" + TRIGGER + "shrug` to use the command"){
    override fun handleCommand(input: String, user: User): BMessage? {
        if(!matchesCommand(input)){
            return null;
        }
        return BMessage(shrug, false);
    }
}

class AboutCommand : AbstractCommand("about", listOf("whoareyou"), "Let me tell you a little about myself...", "Use `" + TRIGGER + "about` to show the info"){

    override fun handleCommand(input: String, user: User): BMessage? {
        if(!matchesCommand(input)){
            return null;
        }

        val reply = ReplyBuilder();

        reply.append("Hiya! I'm Alisha, a chatbot designed by [${Configurations.CREATOR}](${Configurations.CREATOR_GITHUB}). ")
                .append("I'm open-source and the code is available on [Github](${Configurations.GITHUB}). Running version ${Configurations.REVISION}")

        return BMessage(reply.toString(), true)
    }
}

class BasicPrintCommand(val print: String, name: String, aliases: List<String>, desc: String) : AbstractCommand(name, aliases, desc){
    override fun handleCommand(input: String, user: User): BMessage? {
        if(!matchesCommand(input))
            return null;
        return BMessage(print, false)
    }
}

class Alive : AbstractCommand("alive", listOf(), "Used to check if the bot is working"){
    override fun handleCommand(input: String, user: User): BMessage? {
        if(!matchesCommand(input))
            return null;

        return BMessage("I'm pretty sure I am.", true);
    }
}

class TimeCommand : AbstractCommand("time", listOf(), "What time is it?", help="Displays the current time at the bots location.\n" +
        "`-get` as an argument without content displays all the available timezones.\n" +
        "Supplying a timezone (see `${CommandCenter.TRIGGER}time -get` for the available ones) shows the current time in that timezone"){

    override fun handleCommand(input: String, user: User): BMessage? {
        val raw = splitCommand(input)
        val content = raw["content"]
        if(content == null && raw.size == 1)
            return BMessage(formatter.format(System.currentTimeMillis()), true);

        if(content != null && (content.trim().toLowerCase().contains("139") || content.trim().toLowerCase().contains("java"))){
            return BMessage("Morning", true)
        }else if(raw["-get"] != null)
            return BMessage("Available timezones: " + DateTimeZone.getAvailableIDs(), false)
        else if(content != null && (content.trim().toLowerCase().contains("internet")))
            return BMessage("Morning UIT (Universal Internet Time)", true);

        return try{
            val applicable = DateTimeZone.forID(content)
            val formatter = DateTimeFormat.forPattern(DATE_PATTERN)
                    .withLocale(Locale.ENGLISH)
                    .withZone(applicable)
            BMessage(Instant().toString(formatter), true)
        }catch(e: IllegalArgumentException){
            BMessage(e.message, true)
        }
    }
}

class NetStat(val site: Chat) : AbstractCommand("netStat", listOf("netstat"), "Tells you the status of the neural network"){
    override fun handleCommand(input: String, user: User): BMessage? {
        if(Utils.getRank(user.userID, site.config) < 3)
            return BMessage("You need rank 3 or higher to use this command.", true)

        try {
            val httpClient = HttpClients.createDefault()
            val http = Http(httpClient)
            val response = http.post("http://${Configurations.NEURAL_NET_IP}:" + Constants.FLASK_PORT + "/predict", "message", "hello")
            http.close()
            httpClient.close()
            if (response.body.toLowerCase().contains("sorry, i boot")){
                return BMessage("The network is booting", true);
            }
            return BMessage("The neural network has started. Hi!", true)

        }catch(e: Exception){
            return BMessage("The server is offline.", true)
        }
    }
}

class DogeCommand : AbstractCommand("doge", listOf(), desc="Such doge. Much command."){
    val doges = mutableListOf("such", "very", "much", "so", "many")
    override fun handleCommand(input: String, user: User): BMessage? {
        if(!matchesCommand(input))
            return null
        val raw = input.split(" ", limit=2)
        val converted = if (raw.size < 2) defaultMsg else raw[1]

        val msg = ReplyBuilder()
        val what = converted.split(",").map{ it.trim() }
        if (what.isEmpty()){
            return BMessage("Much user. Few arguments. Such attempt", true)
        }

        if (random.nextBoolean())
            msg.fixedInput().append(StringUtils.repeat(" ", random.nextInt(10))).append("wow").nl()

        val maxIndex = Math.min(what.size, 10)//Limit at 10. Because i
        for (i in 0 until maxIndex){
            msg.fixedInput().append(StringUtils.repeat(" ", random.nextInt(15))).append(doges.randomItem()).append(" " + what[i]).nl()
        }
        return BMessage(msg.toString(), false)
    }

    companion object {
        const val defaultMsg = "user, fail, pro"
    }
}

class WakeCommand : AbstractCommand("wake", listOf(), desc="HEY! Wake up!"){
    val random = Random()

    override fun handleCommand(input: String, user: User): BMessage? {
        if(!matchesCommand(input))
            return null
        val who = splitCommand(input)["content"] ?: return BMessage("You have to tell me who to wake up!", true)
        return BMessage(Constants.wakeMessages[random.nextInt(Constants.wakeMessages.size)].format(who), true)
    }
}

class WhoIs(val site: Chat) : AbstractCommand("whois", listOf("identify")){
    override fun handleCommand(input: String, user: User): BMessage? {
        if(!matchesCommand(input))
            return null;
        val who = splitCommand(input)["content"]?.trim() ?: return BMessage("You have to tell me who to identify", true)
        if(who.isEmpty()) return BMessage("You have to tell me who to identify", true)

        val uid = if(who.matches("(\\d+)".toRegex())){
            who.toLong()
        }else {
            val res = getRankOrMessage(who, site)
            if (res is BMessage) {
                return res
            } else {
                res as Long
            }
        }
        val username = site.config.ranks[uid]?.username ?: return BMessage("User not indexed", true)
        return BMessage("""${
        if(site.name == "stackoverflow" || site.name == "metastackexchange")
            "[$username](${site.site.url.replace("chat.", "")}/users/$uid)"
        else if(site.name=="stackexchange"){
            "[$username](${site.site.url}/users/$uid)"
        } else username
        } (UID $uid)""".trimIndent().replace("\n", ""), true)
    }
}

class StatusCommand(val statusListener: StatusListener, val site: Chat) : AbstractCommand("status", listOf("leaderboard"), desc="Shows the leading chatters"){

    override fun handleCommand(input: String, user: User): BMessage? {
        if(clear){
            if(!cleared.contains(site.name)){
                statusListener.users.clear()
                cleared.add(site.name)
            }

            if(cleared.size == 4){
                clear = false
                cleared.clear()
            }
        }

        if (statusListener.users.isEmpty() || statusListener.users[user.roomID] == null)
            return BMessage("No users registered yet. Try again later", true)
        if(statusListener.users[user.roomID]!!.isEmpty())
            return BMessage("No users registered yet. Try again later", true)

        if(!statusListener.users.keys.contains(user.roomID))
            statusListener.users[user.roomID] = mutableMapOf()



        if(input.contains("--clear")){
            if(Utils.getRank(user.userID, site.config) < 9)
                return BMessage("You need rank 9 or higher to clear the status", true);
            if(input.contains("--confirm")){
                statusListener.users.clear()
                clear = true
                cleared.add(site.name)
                return BMessage("Flag sent!", true)
            }
            return BMessage("Confirm with --confirm", true)
        }

        val buffer = statusListener.users[user.roomID]!!.map{
                it.key.toString() to it.value
            }.associateBy({it.first}, {it.second})
                    .toMutableMap()
        val localCopy = mutableMapOf<String, Long>()

        for((k, v) in buffer){
            val buff = getUsername(k, site)

            if(buff == null)
                localCopy[k] = v
            else
                localCopy["$buff ($k)"] = v

        }
        val longFirst = localCopy.map { it.value to it.key }.associateBy ({it.first}, {it.second}).toSortedMap(compareBy{-it})

        val reply = ReplyBuilder()
        val now = Instant()
        val duration = Period(BotCore.STARTED_AT, now)

        val days = duration.days
        val hours = duration.hours
        val minutes = duration.minutes
        val seconds = duration.seconds
        reply.fixedInput().append("Started ${formatter.format(BotCore.STARTED_AT.toDate().time)} (running for $days days, $hours hours, $minutes minutes, and $seconds seconds.)").nl()
        reply.discord = site.site.name == "discord"
        reply.fixedInput().append("Message status").nl()
        val maxLen = localCopy.getMaxLen()
        val x = "Username:"
        reply.fixedInput().append("$x${repeat(" ", maxLen - x.length + 2)}- Message count").nl()
        for((count, who) in longFirst){
            reply.fixedInput().append("$who${repeat(" ", maxLen - who.length + 2)}- $count").nl()
        }

        return BMessage(reply.toString(), false)
    }

    companion object {
        var clear = false

        val cleared = mutableListOf<String>()
    }
}

class GitHubCommand : AbstractCommand("github", listOf("borked"), desc="Sends the link to GitHub in chat (also available through the about command). " +
        "Raise any concerns there."){
    override fun handleCommand(input: String, user: User): BMessage? = BMessage("Here you go: ${Configurations.GITHUB}", true)
}

fun <K, V> Map<K, V>.getMaxLen() : Int{
    var current = 0
    for (k in this){
        if(k.toString().length > current)
            current = k.toString().length
    }
    return current
}

class RepeatCommand : AbstractCommand("echo", listOf("repeat", "say")){
    override fun handleCommand(input: String, user: User): BMessage?{
        val content = splitCommand(input)["content"] ?: return BMessage("What?", true)
        if(content.trim().isEmpty()) return BMessage("What?", true)
        return BMessage(content, false)
    }
}

fun <T> List<T>.randomItem() : T{
    return get(random.nextInt(this.size))
}