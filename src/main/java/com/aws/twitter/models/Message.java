package com.aws.twitter.models;

public class Message {

    private String createdAt;
    private String id;
    private String lang;
    private String text;
    private String username;
    private String hashtag;

    public Message(String createdAt, String id, String lang, String text, String username, String hashtag) {
        this.createdAt = createdAt;
        this.id = id;
        this.lang = lang;
        this.text = text;
        this.username = username;
        this.hashtag = hashtag;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHashtag() {
        return hashtag;
    }

    public void setHashtag(String hashtag) {
        this.hashtag = hashtag;
    }

    @Override
    public String toString() {
        return "Message{" +
                "createdAt='" + createdAt + '\'' +
                ", id='" + id + '\'' +
                ", lang='" + lang + '\'' +
                ", text='" + text + '\'' +
                ", username='" + username + '\'' +
                ", hashtag='" + hashtag + '\'' +
                '}';
    }
}
