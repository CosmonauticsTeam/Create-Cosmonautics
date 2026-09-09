package dev.devce.rocketnautics.content.sputnik.model;

import dev.devce.rocketnautics.content.sputnik.node.INodeHandler;
import dev.devce.rocketnautics.content.sputnik.node.SputnikNodeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SputnikNode {
    private final int id;
    private final String typeId;
    private String title;
    private float x;
    private float y;
    private float width;
    private float height;
    private double customNumber = 0.0;
    private String customString = "";
    private final List<SputnikPin> inputs = new ArrayList<>();
    private final List<SputnikPin> outputs = new ArrayList<>();
    private final Map<String, Object> runtimeValues = new HashMap<>();

    public SputnikNode(int id, String typeId, float x, float y) {
        this.id = id;
        this.typeId = typeId;
        this.x = x;
        this.y = y;

        INodeHandler handler = SputnikNodeRegistry.get(typeId);
        if (handler != null) {
            this.title = handler.getTitle();
            this.inputs.addAll(handler.createInputs());
            this.outputs.addAll(handler.createOutputs());
        } else {
            this.title = typeId;
        }

        if (("sputnik_link".equals(typeId) || "satellite_comms".equals(typeId)) && this.customNumber <= 0.0) {
            this.customNumber = 1.0;
        }

        recalculateDimensions();
    }

    public void recalculateDimensions() {
        if ("constant_number".equals(typeId) || "constant_string".equals(typeId) || "constant_boolean".equals(typeId)
                || "display".equals(typeId) || "display_bridge".equals(typeId) || "visualizer".equals(typeId)) {
            this.width = 190.0f;
            this.height = 80.0f;
        } else if ("sputnik_link".equals(typeId)) {
            this.width = 220.0f;
            this.height = 106.0f;
        } else if ("satellite_comms".equals(typeId)) {
            this.width = 230.0f;
            this.height = 130.0f;
        } else if ("engine_ignition".equals(typeId) || "engine_thrust".equals(typeId)) {
            this.width = 270.0f;
            this.height = 110.0f;
        } else if ("engine_vector".equals(typeId)) {
            this.width = 270.0f;
            this.height = 132.0f;
        } else if ("gyrodyne_control".equals(typeId)) {
            this.width = 280.0f;
            this.height = 190.0f;
        } else {
            int maxPins = Math.max(1, Math.max(inputs.size(), outputs.size()));
            int maxInLen = inputs.stream().mapToInt(p -> p.getName().length()).max().orElse(0);
            int maxOutLen = outputs.stream().mapToInt(p -> p.getName().length()).max().orElse(0);
            float neededWidth = (maxInLen + maxOutLen) * 7.5f + 84.0f;
            this.width = Math.max(190.0f, Math.max(neededWidth, title.length() * 8.5f + 48.0f));
            this.height = Math.max(80.0f, 28.0f + 16.0f + ((maxPins - 1) * 24.0f) + 20.0f);
        }
    }

    public int getId() {
        return id;
    }

    public String getTypeId() {
        return typeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public double getCustomNumber() {
        return customNumber;
    }

    public void setCustomNumber(double customNumber) {
        this.customNumber = customNumber;
    }

    public String getCustomString() {
        return customString;
    }

    public void setCustomString(String customString) {
        this.customString = customString;
    }

    public List<SputnikPin> getInputs() {
        return inputs;
    }

    public List<SputnikPin> getOutputs() {
        return outputs;
    }

    public Map<String, Object> getRuntimeValues() {
        return runtimeValues;
    }

    public SputnikPin findPin(String pinId) {
        for (SputnikPin p : inputs) {
            if (p.getId().equals(pinId))
                return p;
        }
        for (SputnikPin p : outputs) {
            if (p.getId().equals(pinId))
                return p;
        }
        return null;
    }
}
