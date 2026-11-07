package model;

import java.util.ArrayList;
import java.util.List;

public class Account {

    // Fields
    private String username;
    private String password;
    private String createdAt;
    private String lastLogin;
    private List<Email> inbox;
    private List<Email> sent;

    // Constructor
    public Account(String username, String password, String createdAt, String lastLogin) {
        this.username = username;
        this.password = password;
        this.createdAt = createdAt;
        this.lastLogin = lastLogin;
        this.inbox = new ArrayList<>();
        this.sent = new ArrayList<>();
    }

    // Getters & Setters
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getCreatedAt() { return createdAt; }
    public String getLastLogin() { return lastLogin; }
    public void setLastLogin(String lastLogin) { this.lastLogin = lastLogin; }

    public List<Email> getInbox() { return inbox; }
    public List<Email> getSent() { return sent; }
}