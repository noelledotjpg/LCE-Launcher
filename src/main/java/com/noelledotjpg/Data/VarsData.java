package com.noelledotjpg.Data;

public class VarsData {

    private boolean setupDone;
    private String  lceFolder;
    private String  minecraftExe;

    public static final String VERSION = "1.0.0";

    public boolean isSetupDone()               { return setupDone; }
    public void    setSetupDone(boolean v)     { this.setupDone = v; }

    public String  getLceFolder()              { return lceFolder; }
    public void    setLceFolder(String v)      { this.lceFolder = v; }

    public String  getMinecraftExe()           { return minecraftExe; }
    public void    setMinecraftExe(String v)   { this.minecraftExe = v; }
}
