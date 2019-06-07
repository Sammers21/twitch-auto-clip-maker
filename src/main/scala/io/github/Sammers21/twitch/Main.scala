import com.github.twitch4j.TwitchClientBuilder

object Main extends App {
  println("Hello world")
  val twitchClient = TwitchClientBuilder.builder()
    .withEnableHelix(true)
    .build()
  twitchClient.getChat.joinChannel("swifty");
}