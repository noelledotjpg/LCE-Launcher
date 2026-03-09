package com.noelledotjpg.TabContent;

import java.util.ArrayList;

public class ProfilesData {
    private String lastUsed;
    private ArrayList<String> usernames = new ArrayList<>();

    public String getLastUsed() { return lastUsed; }
    public void setLastUsed(String lastUsed) { this.lastUsed = lastUsed; }

    public ArrayList<String> getUsernames() { return usernames; }
    public void setUsernames(ArrayList<String> usernames) { this.usernames = usernames; }
}