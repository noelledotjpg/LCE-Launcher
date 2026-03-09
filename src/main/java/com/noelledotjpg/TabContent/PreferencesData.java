package com.noelledotjpg.TabContent;

public class PreferencesData {

    private boolean fullscreen;
    private String launcherVisibility = "Hide when game starts"; // default

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public String getLauncherVisibility() {
        return launcherVisibility;
    }

    public void setLauncherVisibility(String launcherVisibility) {
        this.launcherVisibility = launcherVisibility;
    }
}