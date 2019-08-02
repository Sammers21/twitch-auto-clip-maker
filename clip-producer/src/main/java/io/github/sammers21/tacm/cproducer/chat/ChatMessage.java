package io.github.sammers21.tacm.cproducer.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatMessage {

    public static Pattern MSG_PATTERN = Pattern.compile(":(.+)!.+@.+ PRIVMSG #(.+) :(.+)");

    private final String text;
    private final String chanName;
    private final String author;

    public ChatMessage(String text, String chanName, String author) {
        this.text = text;
        this.chanName = chanName;
        this.author = author;
    }

    public static ChatMessage parse(String line) {
        Matcher matcher = MSG_PATTERN.matcher(line);
        boolean found = matcher.find();
        if(found){
            return new ChatMessage(matcher.group(3), matcher.group(2), matcher.group(1));
        } else {
            return null;
        }
    }

    public String getText() {
        return text;
    }

    public String getChanName() {
        return chanName;
    }

    public String getAuthor() {
        return author;
    }

    public static void main(String[] args) {
        String s = ":realisee19!realisee19@realisee19.tmi.twitch.tv PRIVMSG #icebergdoto :@fooonshin походу у тебя нет,что ты просто высераешь рофлы тупые";
        Matcher matcher = MSG_PATTERN.matcher(s);
        boolean b = matcher.find();
        System.out.println(matcher.group(1));
        System.out.println(matcher.group(2));
        System.out.println(matcher.group(3));
    }
}
