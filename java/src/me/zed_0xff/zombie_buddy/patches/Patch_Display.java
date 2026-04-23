package me.zed_0xff.zombie_buddy.patches;

import me.zed_0xff.zombie_buddy.JavaStateDumper;
import me.zed_0xff.zombie_buddy.Patch;

public class Patch_Display {
    @Patch(className = "org.lwjglx.opengl.Display", methodName = "create")
    public static class Patch_create {
        @Patch.OnExit
        public static void exit() {
            JavaStateDumper.installKeyCallback();
        }
    }
}
