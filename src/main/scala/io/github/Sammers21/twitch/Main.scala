import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.chat.TwitchChat
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux

object Main extends App {


  private val token: String = args(0)
  private val log = LoggerFactory.getLogger("Main")

  // chat credential
  val credential = new OAuth2Credential("twitch", token)

  // twitch client
  val twitchClient = TwitchClientBuilder.builder()
    .withEnableChat(true)
    .withChatAccount(credential)
    .build()

  val chat: TwitchChat = twitchClient.getChat
  chat.joinChannel("starladder5")
  private val value: Flux[ChannelMessageEvent] = chat.getEventManager.onEvent(classOf[ChannelMessageEvent])
  value.subscribe(ok => {
    log.info(ok.getMessage)
  })
}