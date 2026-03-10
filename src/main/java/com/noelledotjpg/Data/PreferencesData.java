package com.noelledotjpg.Data;

public class PreferencesData {

    private boolean fullscreen;
    private String  launcherVisibility = "Hide when game starts";

    public boolean isFullscreen()                       { return fullscreen; }
    public void    setFullscreen(boolean fullscreen)    { this.fullscreen = fullscreen; }

    public String  getLauncherVisibility()              { return launcherVisibility; }
    public void    setLauncherVisibility(String value)  { this.launcherVisibility = value; }
}
