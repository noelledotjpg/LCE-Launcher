package com.noelledotjpg.Data;

public class PreferencesData {

    private boolean fullscreen;
    private String  launcherVisibility  = "Hide when game starts";
    private boolean checkForUpdates     = false;
    private String  updateFrequency     = "When launcher opens";
    private String  newsPageMode        = "Default";
    private String  newsPageCustomUrl   = "";

    public boolean isFullscreen()                       { return fullscreen; }
    public void    setFullscreen(boolean fullscreen)    { this.fullscreen = fullscreen; }

    public String  getLauncherVisibility()              { return launcherVisibility; }
    public void    setLauncherVisibility(String value)  { this.launcherVisibility = value; }

    public boolean isCheckForUpdates()                  { return checkForUpdates; }
    public void    setCheckForUpdates(boolean value)    { this.checkForUpdates = value; }

    public String  getUpdateFrequency()                 { return updateFrequency; }
    public void    setUpdateFrequency(String value)     { this.updateFrequency = value; }

    public String  getNewsPageMode()                    { return newsPageMode; }
    public void    setNewsPageMode(String value)        { this.newsPageMode = value; }

    public String  getNewsPageCustomUrl()               { return newsPageCustomUrl; }
    public void    setNewsPageCustomUrl(String value)   { this.newsPageCustomUrl = value; }

    public String  getResolvedNewsUrl() {
        if ("Custom".equals(newsPageMode) && newsPageCustomUrl != null && !newsPageCustomUrl.isBlank())
            return newsPageCustomUrl;
        return "https://arbifoxx.github.io/news/";
    }
}