package io.github.sammers21.tacm.cproducer.chat;

public class ChatMessage {

    private final String text;
    private final String chanName;
    private final String author;

    public ChatMessage(String text, String chanName, String author) {
        this.text = text;
        this.chanName = chanName;
        this.author = author;
    }

    public static ChatMessage parse(String line) {
        return null;
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
}
