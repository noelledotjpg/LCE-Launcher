package com.noelledotjpg.BootstrapContent;

public class VarsData {
    private boolean setupDone = false;
    private String lceFolder = "";
    private String minecraftExe = "";

    public boolean isSetupDone() { return setupDone; }
    public void setSetupDone(boolean setupDone) { this.setupDone = setupDone; }

    public String getLceFolder() { return lceFolder; }
    public void setLceFolder(String lceFolder) { this.lceFolder = lceFolder; }

    public String getMinecraftExe() { return minecraftExe; }
    public void setMinecraftExe(String minecraftExe) { this.minecraftExe = minecraftExe; }
}