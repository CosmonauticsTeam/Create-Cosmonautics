package dev.devce.rocketnautics.content.sputnik.model;

import dev.devce.rocketnautics.content.blocks.SputnikBlockEntity;
import dev.devce.rocketnautics.content.sputnik.node.INodeHandler;
import dev.devce.rocketnautics.content.sputnik.node.NodeExecutionContext;
import dev.devce.rocketnautics.content.sputnik.node.SputnikNodeRegistry;

import java.util.*;

public class SputnikGraph {
    private final List<SputnikNode> nodes = new ArrayList<>();
    private final List<SputnikLink> links = new ArrayList<>();
    private int nextNodeId = 1;
    private float panX = 0.0f;
    private float panY = 0.0f;
    private float zoom = 1.0f;

    public List<SputnikNode> getNodes() {
        return nodes;
    }

    public List<SputnikLink> getLinks() {
        return links;
    }

    public int allocateNodeId() {
        return nextNodeId++;
    }

    public void setNextNodeId(int id) {
        this.nextNodeId = id;
    }

    public float getPanX() {
        return panX;
    }

    public void setPanX(float panX) {
        this.panX = panX;
    }

    public float getPanY() {
        return panY;
    }

    public void setPanY(float panY) {
        this.panY = panY;
    }

    public float getZoom() {
        return zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
    }

    public SputnikNode findNode(int id) {
        for (SputnikNode n : nodes) {
            if (n.getId() == id) return n;
        }
        return null;
    }

    public void addNode(SputnikNode node) {
        if (node.getId() >= nextNodeId) {
            nextNodeId = node.getId() + 1;
        }
        nodes.add(node);
    }

    public void removeNode(int nodeId) {
        nodes.removeIf(n -> n.getId() == nodeId);
        links.removeIf(l -> l.getFromNodeId() == nodeId || l.getToNodeId() == nodeId);
    }

    public void addLink(int fromNodeId, String fromPinId, int toNodeId, String toPinId) {
        removeLinksTo(toNodeId, toPinId);
        links.add(new SputnikLink(fromNodeId, fromPinId, toNodeId, toPinId));
    }

    public void removeLinksTo(int nodeId, String pinId) {
        links.removeIf(l -> l.getToNodeId() == nodeId && l.getToPinId().equals(pinId));
    }

    public void removeLinksFrom(int nodeId, String pinId) {
        links.removeIf(l -> l.getFromNodeId() == nodeId && l.getFromPinId().equals(pinId));
    }

    public void evaluate(SputnikBlockEntity blockEntity) {
        Map<String, Object> pinValues = new HashMap<>();

        List<SputnikNode> sorted = getTopologicalOrder();

        for (SputnikNode node : sorted) {
            INodeHandler handler = SputnikNodeRegistry.get(node.getTypeId());
            if (handler == null) continue;

            Map<String, Object> nodeInputs = new HashMap<>();
            for (SputnikPin pin : node.getInputs()) {
                String key = node.getId() + ":" + pin.getId();
                Object val = pinValues.get(key);
                if (val != null) {
                    nodeInputs.put(pin.getId(), val);
                }
            }

            Map<String, Object> nodeOutputs = new HashMap<>();
            NodeExecutionContext ctx = new NodeExecutionContext(blockEntity, nodeInputs, nodeOutputs);
            handler.execute(node, ctx);

            node.getRuntimeValues().clear();
            node.getRuntimeValues().putAll(nodeInputs);
            node.getRuntimeValues().putAll(nodeOutputs);

            for (Map.Entry<String, Object> entry : nodeOutputs.entrySet()) {
                String srcKey = node.getId() + ":" + entry.getKey();
                for (SputnikLink link : links) {
                    if (link.getFromNodeId() == node.getId() && link.getFromPinId().equals(entry.getKey())) {
                        String dstKey = link.getToNodeId() + ":" + link.getToPinId();
                        pinValues.put(dstKey, entry.getValue());
                    }
                }
            }
        }
    }

    private List<SputnikNode> getTopologicalOrder() {
        Map<Integer, Set<Integer>> inDegree = new HashMap<>();
        Map<Integer, List<Integer>> adj = new HashMap<>();

        for (SputnikNode node : nodes) {
            inDegree.put(node.getId(), new HashSet<>());
            adj.put(node.getId(), new ArrayList<>());
        }

        for (SputnikLink link : links) {
            if (inDegree.containsKey(link.getToNodeId()) && adj.containsKey(link.getFromNodeId())) {
                inDegree.get(link.getToNodeId()).add(link.getFromNodeId());
                adj.get(link.getFromNodeId()).add(link.getToNodeId());
            }
        }

        Queue<Integer> queue = new ArrayDeque<>();
        for (Map.Entry<Integer, Set<Integer>> entry : inDegree.entrySet()) {
            if (entry.getValue().isEmpty()) {
                queue.add(entry.getKey());
            }
        }

        List<SputnikNode> result = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            int curr = queue.poll();
            if (!visited.add(curr)) continue;
            SputnikNode node = findNode(curr);
            if (node != null) {
                result.add(node);
            }

            for (int neighbor : adj.getOrDefault(curr, List.of())) {
                Set<Integer> preds = inDegree.get(neighbor);
                if (preds != null) {
                    preds.remove(curr);
                    if (preds.isEmpty()) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        for (SputnikNode node : nodes) {
            if (!visited.contains(node.getId())) {
                result.add(node);
            }
        }

        return result;
    }

    public static SputnikGraph createDefault() {
        SputnikGraph g = new SputnikGraph();

        SputnikNode n1 = new SputnikNode(g.allocateNodeId(), "constant_number", 100, 140);
        n1.setCustomNumber(8.773);
        g.addNode(n1);

        SputnikNode n2 = new SputnikNode(g.allocateNodeId(), "math_add", 340, 120);
        g.addNode(n2);

        SputnikNode n3 = new SputnikNode(g.allocateNodeId(), "display", 560, 130);
        g.addNode(n3);

        g.addLink(n1.getId(), "val", n2.getId(), "a");
        g.addLink(n2.getId(), "out", n3.getId(), "in");

        return g;
    }
}
