package com.noelledotjpg.MainContent;

import java.util.ArrayList;
import java.util.List;

public class LaunchArguments {

    private boolean fullscreen;

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public List<String> buildArgs(String profile) {

        List<String> args = new ArrayList<>();

        if (fullscreen)
            args.add("-fullscreen");

        if (profile != null && !profile.isBlank())
            args.add("-" + profile);

        return args;
    }
}