package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.WFCore;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class ModInventory {

    public record LoadedFile(String fileName, Path path, String mods) {}

    private ModInventory() {}


    public static List<LoadedFile> loadedFiles() {
        List<LoadedFile> out = new ArrayList<>();
        try {
            for (IModFileInfo info : ModList.get().getModFiles()) {
                if (info == null || info.getFile() == null) {
                    continue;
                }
                Path path = info.getFile().getFilePath();
                String fileName = path != null && path.getFileName() != null
                        ? path.getFileName().toString()
                        : String.valueOf(info.getFile().getFileName());
                out.add(new LoadedFile(fileName, path, joinMods(info.getMods())));
            }
        } catch (Throwable t) {
            WFCore.LOGGER.debug("[wfcore-modaudit] could not enumerate loaded mod files: {}", t.toString());
        }
        return out;
    }

    public static Map<String, String> modsByFileName() {
        Map<String, String> map = new LinkedHashMap<>();
        for (LoadedFile file : loadedFiles()) {
            map.merge(file.fileName(), file.mods(), (a, b) -> a.isEmpty() ? b : (b.isEmpty() ? a : a + ", " + b));
        }
        return map;
    }

    private static String joinMods(List<IModInfo> mods) {
        if (mods == null || mods.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (IModInfo mod : mods) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(mod.getModId()).append('@').append(mod.getVersion());
        }
        return sb.toString();
    }

    public static String sha256hex(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int read;
            while ((read = in.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
            return toHex(md.digest());
        } catch (Throwable t) {
            WFCore.LOGGER.debug("[wfcore-modaudit] could not hash {}: {}", file, t.toString());
            return null;
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static String sanitize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }
}
