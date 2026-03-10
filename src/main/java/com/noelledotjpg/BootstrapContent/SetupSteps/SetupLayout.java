package com.noelledotjpg.BootstrapContent.SetupSteps;

import java.awt.*;

public class SetupLayout {
    public static final int WIDTH        = 400;
    public static final int INNER_WIDTH  = WIDTH - 20;

    private static final int BROWSE_WIDTH = 72;
    private static final int BROWSE_GAP   = 5;
    public static final int  FIELD_WIDTH  = INNER_WIDTH - BROWSE_WIDTH - BROWSE_GAP - 20;
    public static final int  BROWSE_X     = 10 + FIELD_WIDTH + BROWSE_GAP;

    public static final int CENTER_X      = (WIDTH - 274) / 2;

    private static final int BTN_WIDTH    = 100;
    private static final int BTN_GAP      = 10;
    private static final int BTN_GROUP    = BTN_WIDTH * 2 + BTN_GAP;
    public static final int  BTN_LEFT_X   = (WIDTH - BTN_GROUP) / 2;
    public static final int  BTN_RIGHT_X  = BTN_LEFT_X + BTN_WIDTH + BTN_GAP;

    public static final Dimension MAIN_SIZE        = new Dimension(315, 160);
    public static final Dimension STEP_SIZE        = new Dimension(WIDTH, 270);
    public static final Dimension BUILD_TOOLS_SIZE = new Dimension(WIDTH, 310);
    public static final Dimension LOADING_SIZE     = new Dimension(515, 420);
}