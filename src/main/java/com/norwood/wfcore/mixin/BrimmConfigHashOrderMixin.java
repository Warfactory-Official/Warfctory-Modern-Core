package com.norwood.wfcore.mixin;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes Brimm's join-time config verification hash deterministic across machines.
 *
 * <p>Brimm's {@code ConfigsManager.init()} builds a SHA-256 over {@code config/brimm/overrides/*.xml} by
 * calling {@code MessageDigest.update()} once per file, iterating the array returned by
 * {@code File.listFiles()} — which it never sorts. {@code File.listFiles()} has no defined order (on ext4 it
 * follows the per-filesystem htree hash seed), so two installs with byte-identical override files compute
 * <em>different</em> hashes. On join the server compares its own hash against the client's; a mismatch kicks
 * the player ("brimm: Config verification failed"). Stock Brimm ships zero override files so the digest is
 * over an empty set and the bug never surfaces; this pack ships 8 overrides and hits it head-on.
 *
 * <p>Fix: redirect that single {@code listFiles()} call to return the files sorted by name. Both server and
 * client run wfcore, so both hash in the same (alphabetical) order and agree. Insertion order into Brimm's
 * {@code configs} map is irrelevant to lookups, so this only affects the hash — behaviour is otherwise
 * unchanged.
 */
@Mixin(targets = "blackoutInteractive.brimmArmors.common.configurations.ConfigsManager", remap = false)
public abstract class BrimmConfigHashOrderMixin {

    @Redirect(
            method = "init",
            at = @At(value = "INVOKE", target = "Ljava/io/File;listFiles()[Ljava/io/File;")
    )
    private static File[] wfcore$sortedOverrideList(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
        }
        return files;
    }
}
