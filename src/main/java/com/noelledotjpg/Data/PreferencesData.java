package com.noelledotjpg.Data;

public class PreferencesData {

    private boolean fullscreen;
    private String  launcherVisibility      = "Hide when game starts";
    private boolean checkForUpdates         = false;
    private String  updateFrequency         = "When launcher opens";
    private boolean checkForLauncherUpdates = true;
    private String  launcherUpdateFrequency = "Every 24 hours";
    private String  newsPageMode            = "Default";
    private String  newsPageCustomUrl       = "";
    private boolean debugMode               = false;
    private String  theme                   = "System";
    private boolean useSystemTitleBar       = true;

    public boolean isFullscreen()                       { return fullscreen; }
    public void    setFullscreen(boolean fullscreen)    { this.fullscreen = fullscreen; }

    public String  getLauncherVisibility()              { return launcherVisibility; }
    public void    setLauncherVisibility(String value)  { this.launcherVisibility = value; }

    public boolean isCheckForUpdates()                  { return checkForUpdates; }
    public void    setCheckForUpdates(boolean value)    { this.checkForUpdates = value; }

    public String  getUpdateFrequency()                 { return updateFrequency; }
    public void    setUpdateFrequency(String value)     { this.updateFrequency = value; }

    public boolean isCheckForLauncherUpdates()                   { return checkForLauncherUpdates; }
    public void    setCheckForLauncherUpdates(boolean value)     { this.checkForLauncherUpdates = value; }

    public String  getLauncherUpdateFrequency()                  { return launcherUpdateFrequency; }
    public void    setLauncherUpdateFrequency(String value)      { this.launcherUpdateFrequency = value; }

    public String  getNewsPageMode()                    { return newsPageMode; }
    public void    setNewsPageMode(String value)        { this.newsPageMode = value; }

    public String  getNewsPageCustomUrl()               { return newsPageCustomUrl; }
    public void    setNewsPageCustomUrl(String value)   { this.newsPageCustomUrl = value; }

    public boolean isDebugMode()                        { return debugMode; }
    public void    setDebugMode(boolean value)          { this.debugMode = value; }

    public String  getTheme()                           { return theme != null ? theme : "System"; }
    public void    setTheme(String value)               { this.theme = value; }

    public boolean isUseSystemTitleBar()                { return useSystemTitleBar; }
    public void    setUseSystemTitleBar(boolean value)  { this.useSystemTitleBar = value; }

    public String  getResolvedNewsUrl() {
        if ("Custom".equals(newsPageMode) && newsPageCustomUrl != null && !newsPageCustomUrl.isBlank())
            return newsPageCustomUrl;
        return "https://arbifoxx.github.io/news/";
    }
}