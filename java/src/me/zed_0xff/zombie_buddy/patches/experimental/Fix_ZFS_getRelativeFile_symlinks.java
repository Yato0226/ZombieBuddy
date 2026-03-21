package me.zed_0xff.zombie_buddy.patches.experimental;

import me.zed_0xff.zombie_buddy.Logger;
import me.zed_0xff.zombie_buddy.Patch;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

// getRelativeFile(
//  file:/users/zed/zomboid/mods/zscienceskill/42.13/,
//      "/Users/zed/Zomboid/mods/ZScienceSkill/tmp/cache_client_42.13.2/mods/ZScienceSkill/42.13/media/scripts/ZScienceSkill_items.txt"
// ) => "/Users/zed/Zomboid/mods/ZScienceSkill/tmp/cache_client_42.13.2/mods/ZScienceSkill/42.13/media/scripts/ZScienceSkill_items.txt"
//
// but should've returned relative path. it because it's a symlink
//
// getRelativeFile(
//  file:/Users/zed/Zomboid/mods/ZBSpec/mods/ZBSpec/41/,
//      "/Users/zed/Zomboid/mods/ZBSpec/mods/ZBSpec/common/media/lua/shared/zbsHook.lua"
// ) => "/Users/zed/Zomboid/mods/ZBSpec/mods/ZBSpec/common/media/lua/shared/zbsHook.lua"
//
// readlink /Users/zed/Zomboid/mods/ZBSpec/mods/ZBSpec/41/media/lua => ../../common/media/lua

public class Fix_ZFS_getRelativeFile_symlinks {
    @Patch(className= "zombie.ZomboidFileSystem", methodName= "getRelativeFile")
    public static class Patch_ZFS_getRelativeFile {
        @Patch.OnExit
        public static void exit(URI root, String path, @Patch.Return(readOnly = false) String result) {
            if (result == null || path == null || root == null) return;
            if (!result.equals(path) || !path.startsWith("/")) return;
            if (root.getScheme() == null || !root.getScheme().equals("file")) return;

            try {
                File rootFile = new File(root);
                String rootCanonical = rootFile.getCanonicalPath();
                String pathCanonical = new File(path).getCanonicalPath();

                // Direct non-symlink case.
                String rootPrefix = rootCanonical.endsWith(File.separator) ? rootCanonical : (rootCanonical + File.separator);
                if (pathCanonical.startsWith(rootPrefix)) {
                    result = pathCanonical.substring(rootPrefix.length()).replace(File.separatorChar, '/');
                    Logger.info("Fixed getRelativeFile() result: " + result + " (direct)");
                    return; // Patch.Return: result is written back
                }

                // Symlink case: scan under root up to depth 2 and map canonical target back to
                // the symlink's logical location relative to root.
                String viaSymlink = resolveViaSymlinkUnderRoot(rootFile, pathCanonical, 2);
                if (viaSymlink != null) {
                    result = viaSymlink;
                    Logger.info("Fixed getRelativeFile() result: " + result + " (via-symlink)");
                    return;
                }
            } catch (Exception e) {
                // leave result unchanged
            }
            // get false warnings when game tries to find same file in different dirs
            // Logger.warn("Couldn't fix getRelativeFile() result " + Logger.formatArg(result) + " for root " + Logger.formatArg(root));
        }

        public static String resolveViaSymlinkUnderRoot(File rootFile, String pathCanonical, int maxDepth) {
            if (rootFile == null || pathCanonical == null || maxDepth < 0) return null;
            File[] children = rootFile.listFiles();
            if (children == null) return null;

            Path rootAbs = rootFile.toPath().toAbsolutePath().normalize();
            for (File child : children) {
                if (child == null) continue;
                Path childPath = child.toPath();
                try {
                    if (Files.isSymbolicLink(childPath)) {
                        String linkCanonical = child.getCanonicalPath();
                        String linkPrefix = linkCanonical.endsWith(File.separator) ? linkCanonical : (linkCanonical + File.separator);
                        if (pathCanonical.equals(linkCanonical) || pathCanonical.startsWith(linkPrefix)) {
                            String remainder = "";
                            if (pathCanonical.startsWith(linkPrefix)) {
                                remainder = pathCanonical.substring(linkPrefix.length()).replace(File.separatorChar, '/');
                            }

                            Path childAbs = childPath.toAbsolutePath().normalize();
                            String logicalPrefix = rootAbs.relativize(childAbs).toString().replace(File.separatorChar, '/');
                            if (logicalPrefix.isEmpty()) return remainder;
                            return remainder.isEmpty() ? logicalPrefix : (logicalPrefix + "/" + remainder);
                        }
                        continue;
                    }
                    if (maxDepth > 0 && child.isDirectory()) {
                        String nested = resolveViaSymlinkUnderRoot(child, pathCanonical, maxDepth - 1);
                        if (nested != null) return nested;
                    }
                } catch (Exception ignored) {
                    // ignore broken/inaccessible entry and continue scanning
                }
            }
            return null;
        }
    }
}
