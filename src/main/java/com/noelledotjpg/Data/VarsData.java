package com.noelledotjpg.Data;

public class VarsData {

    public static final String VERSION = "v1.0.0";

    private boolean setupDone;
    private String  lceFolder;
    private String  minecraftExe;
    private String  installedCommitHash;
    private String skippedCommitHash;

    public boolean isSetupDone()                    { return setupDone; }
    public void    setSetupDone(boolean v)          { this.setupDone = v; }

    public String  getLceFolder()                   { return lceFolder; }
    public void    setLceFolder(String v)           { this.lceFolder = v; }

    public String  getMinecraftExe()                { return minecraftExe; }
    public void    setMinecraftExe(String v)        { this.minecraftExe = v; }

    public String  getInstalledCommitHash()         { return installedCommitHash; }
    public void    setInstalledCommitHash(String v) { this.installedCommitHash = v; }

    public String getSkippedCommitHash() { return skippedCommitHash; }
    public void setSkippedCommitHash(String skippedCommitHash) { this.skippedCommitHash = skippedCommitHash; }

}