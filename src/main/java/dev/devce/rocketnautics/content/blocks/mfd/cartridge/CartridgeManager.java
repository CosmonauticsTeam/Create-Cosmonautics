package dev.devce.rocketnautics.content.blocks.mfd.cartridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CartridgeManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class CartridgeMetadata {
        public String title = "";
        public String author = "";
        public String version = "1.0.0";
        public String description = "";

        public CartridgeMetadata() {}

        public CartridgeMetadata(String title, String author, String version, String description) {
            this.title = title;
            this.author = author;
            this.version = version;
            this.description = description;
        }
    }

    public static CartridgeMetadata getMetadata(String id) {
        Path metaFile = getCartridgeDir(id).resolve("metadata.json");
        if (Files.exists(metaFile)) {
            try (Reader reader = Files.newBufferedReader(metaFile)) {
                CartridgeMetadata meta = GSON.fromJson(reader, CartridgeMetadata.class);
                if (meta != null) return meta;
            } catch (Exception ignored) {}
        }
        return new CartridgeMetadata(id, "Anonymous", "1.0.0", "");
    }

    public static void saveMetadata(String id, CartridgeMetadata meta) {
        Path metaFile = getCartridgeDir(id).resolve("metadata.json");
        try {
            Files.createDirectories(metaFile.getParent());
            try (Writer writer = Files.newBufferedWriter(metaFile)) {
                GSON.toJson(meta, writer);
            }
        } catch (IOException ignored) {}
    }

    public static Path getCartridgesDir() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("cartridges");
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException ignored) {}
        }
        return dir;
    }

    public static Path getCartridgeDir(String id) {
        if (id == null || id.trim().isEmpty()) {
            id = "default";
        }
        String cleanId = id.replaceAll("[^a-zA-Z0-9_-]", "_");

        Path gameDirCartridge = FMLPaths.GAMEDIR.get().resolve("cartridges").resolve(cleanId);
        if (Files.exists(gameDirCartridge.resolve("main.lua"))) {
            return gameDirCartridge;
        }

        try {
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                Path worldCartridge = server.getWorldPath(LevelResource.ROOT).resolve("cartridges").resolve(cleanId);
                if (Files.exists(worldCartridge.resolve("main.lua"))) {
                    return worldCartridge;
                }
            }
        } catch (Throwable ignored) {}

        if (!Files.exists(gameDirCartridge)) {
            try {
                Files.createDirectories(gameDirCartridge.resolve("assets"));
                Path mainLua = gameDirCartridge.resolve("main.lua");
                if (!Files.exists(mainLua)) {
                    Files.writeString(mainLua, """
                            function init()
                                x = 32
                                y = 32
                            end

                            function update()
                                clear(0x0C1018)
                                setPixel(32, 32, 0x00FFCC)
                            end
                            """);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return gameDirCartridge;
    }

    public static List<String> listCartridges() {
        List<String> list = new ArrayList<>();
        Path base = getCartridgesDir();
        if (Files.exists(base) && Files.isDirectory(base)) {
            try (Stream<Path> stream = Files.list(base)) {
                stream.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .forEach(list::add);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public static void importFiles(String cartridgeId, List<Path> sourcePaths) {
        Path targetDir = getCartridgeDir(cartridgeId);
        Path assetsDir = targetDir.resolve("assets");
        try {
            Files.createDirectories(assetsDir);
        } catch (IOException ignored) {}

        for (Path src : sourcePaths) {
            if (!Files.exists(src)) continue;
            try {
                String fileName = src.getFileName().toString();
                Path dest;
                if (fileName.endsWith(".lua") || fileName.equals("metadata.json")) {
                    dest = targetDir.resolve(fileName);
                } else {
                    dest = assetsDir.resolve(fileName);
                }
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
