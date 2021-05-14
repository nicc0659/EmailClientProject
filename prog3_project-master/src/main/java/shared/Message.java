package shared;

import java.io.Serializable;

public class Message implements Serializable {
    private final String user, hash, text;
    private final Email[] emails;
    private boolean error;

    /**
     * message from user containing an Email array
     *
     * @param user   user that sent the message
     * @param hash   hash of the user password
     * @param text   eventual message or requested action
     * @param emails array of mail to send
     */
    public Message(String user, String hash, String text, Email[] emails) {
        this.user = user;
        this.hash = hash;
        this.emails = emails;
        this.text = text;
        error=false;
    }

    /**
     * message from server containing an Email array
     * @param text eventual error message or requested action
     * @param emails array of mail to send
     */
    public Message(String text , Email[] emails) {
        this.user = "server";
        this.hash = "";
        this.emails = emails;
        this.text = text;
        error=false;
    }

    /**
     * message from user containing an Email
     * @param user user that sent the message
     * @param hash hash of the user password
     * @param text eventual message or requested action
     * @param email one mail to send
     */
    public Message(String user, String hash, String text, Email email) {
        this.user = user;
        this.hash = hash;
        this.text = text;
        this.emails = new Email[]{email};
        error=false;
    }

    /**
     * simple action (login, update, delete)
     * @param user user that sent the message
     * @param hash hash of the user password
     * @param text requested action
     */
    public Message(String user, String hash, String text) {
        this.user = user;
        this.hash = hash;
        this.text = text;
        this.emails = new Email[]{};
        error=false;
    }

    /**
     * error message from server
     * @param text error message
     */
    public Message(String text) {
        this.user = "server";
        this.hash = "";
        this.text = text;
        this.emails = new Email[]{};
        error=false;
    }

    public String getUser() {
        return user;
    }

    public String getHash() {
        return hash;
    }

    public Email[] getEmails() {
        return emails;
    }

    public Email getEmail(){
        return emails[0];
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public String getText() {
        return text;
    }
}
