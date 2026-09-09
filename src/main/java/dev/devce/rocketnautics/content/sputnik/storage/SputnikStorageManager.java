package dev.devce.rocketnautics.content.sputnik.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.devce.rocketnautics.content.sputnik.model.SputnikGraph;
import dev.devce.rocketnautics.content.sputnik.model.SputnikLink;
import dev.devce.rocketnautics.content.sputnik.model.SputnikNode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class SputnikStorageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SputnikStorageManager.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final Object LOCK = new Object();

    public static class NodeDto {
        public int id;
        public String type;
        public String title;
        public float x;
        public float y;
        public float w;
        public float h;
        public double num;
        public String str;
    }

    public static class LinkDto {
        public int fromNode;
        public String fromPin;
        public int toNode;
        public String toPin;
    }

    public static class GraphDto {
        public int version = 1;
        public int nextNodeId;
        public float panX;
        public float panY;
        public float zoom;
        public List<NodeDto> nodes = new ArrayList<>();
        public List<LinkDto> links = new ArrayList<>();
    }

    public static Path getBaseDir(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("sputnik");
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            LOGGER.error("Failed to create sputnik base directory", e);
        }
        return path;
    }

    public static Path getSatelliteDir(MinecraftServer server, int sputnikId) {
        Path path = getBaseDir(server).resolve("sp" + sputnikId);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            LOGGER.error("Failed to create sputnik satellite directory: sp" + sputnikId, e);
        }
        return path;
    }

    public static int allocateId(MinecraftServer server) {
        synchronized (LOCK) {
            Path counterFile = getBaseDir(server).resolve("id_counter.dat");
            int currentId = 1;

            if (Files.exists(counterFile)) {
                try {
                    String str = Files.readString(counterFile, StandardCharsets.UTF_8).trim();
                    currentId = Integer.parseInt(str);
                } catch (Exception ignored) {}
            }

            int allocated = currentId;
            try {
                Files.writeString(counterFile, String.valueOf(currentId + 1), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.error("Failed to update sputnik id counter", e);
            }
            return allocated;
        }
    }

    public static void saveGraph(MinecraftServer server, int sputnikId, SputnikGraph graph) {
        synchronized (LOCK) {
            Path file = getSatelliteDir(server, sputnikId).resolve("graph.json.gz");
            byte[] bytes = serializeToGzip(graph);
            try {
                Files.write(file, bytes);
            } catch (IOException e) {
                LOGGER.error("Failed to save sputnik graph: sp" + sputnikId, e);
            }
        }
    }

    public static SputnikGraph loadGraph(MinecraftServer server, int sputnikId) {
        synchronized (LOCK) {
            Path file = getSatelliteDir(server, sputnikId).resolve("graph.json.gz");
            if (!Files.exists(file)) {
                SputnikGraph defaultGraph = SputnikGraph.createDefault();
                saveGraph(server, sputnikId, defaultGraph);
                return defaultGraph;
            }

            try {
                byte[] bytes = Files.readAllBytes(file);
                return deserializeFromGzip(bytes);
            } catch (Exception e) {
                LOGGER.error("Failed to load sputnik graph: sp" + sputnikId, e);
                return SputnikGraph.createDefault();
            }
        }
    }

    public static byte[] serializeToGzip(SputnikGraph graph) {
        GraphDto dto = new GraphDto();
        dto.nextNodeId = graph.allocateNodeId();
        dto.panX = graph.getPanX();
        dto.panY = graph.getPanY();
        dto.zoom = graph.getZoom();

        for (SputnikNode node : graph.getNodes()) {
            NodeDto nd = new NodeDto();
            nd.id = node.getId();
            nd.type = node.getTypeId();
            nd.title = node.getTitle();
            nd.x = node.getX();
            nd.y = node.getY();
            nd.w = node.getWidth();
            nd.h = node.getHeight();
            nd.num = node.getCustomNumber();
            nd.str = node.getCustomString();
            dto.nodes.add(nd);
        }

        for (SputnikLink link : graph.getLinks()) {
            LinkDto ld = new LinkDto();
            ld.fromNode = link.getFromNodeId();
            ld.fromPin = link.getFromPinId();
            ld.toNode = link.getToNodeId();
            ld.toPin = link.getToPinId();
            dto.links.add(ld);
        }

        String json = GSON.toJson(dto);
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(raw);
            gzos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.error("GZIP serialization failed", e);
            return new byte[0];
        }
    }

    public static SputnikGraph deserializeFromGzip(byte[] gzipped) {
        if (gzipped == null || gzipped.length == 0) {
            return SputnikGraph.createDefault();
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(gzipped);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             InputStreamReader reader = new InputStreamReader(gzis, StandardCharsets.UTF_8)) {

            GraphDto dto = GSON.fromJson(reader, GraphDto.class);
            if (dto == null) return SputnikGraph.createDefault();

            SputnikGraph graph = new SputnikGraph();
            graph.setNextNodeId(Math.max(1, dto.nextNodeId));
            graph.setPanX(dto.panX);
            graph.setPanY(dto.panY);
            graph.setZoom(dto.zoom > 0 ? dto.zoom : 1.0f);

            for (NodeDto nd : dto.nodes) {
                SputnikNode node = new SputnikNode(nd.id, nd.type, nd.x, nd.y);
                if (nd.title != null && !nd.title.isEmpty()) node.setTitle(nd.title);
                node.recalculateDimensions();
                node.setCustomNumber(nd.num);
                node.setCustomString(nd.str != null ? nd.str : "");
                graph.addNode(node);
            }

            for (LinkDto ld : dto.links) {
                graph.addLink(ld.fromNode, ld.fromPin, ld.toNode, ld.toPin);
            }

            return graph;
        } catch (Exception e) {
            LOGGER.error("GZIP deserialization failed", e);
            return SputnikGraph.createDefault();
        }
    }
}
