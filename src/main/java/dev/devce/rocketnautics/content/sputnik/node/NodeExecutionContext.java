package dev.devce.rocketnautics.content.sputnik.node;

import dev.devce.rocketnautics.content.blocks.SputnikBlockEntity;
import org.joml.Vector3d;

import java.util.Map;

public class NodeExecutionContext {
    private final SputnikBlockEntity blockEntity;
    private final Map<String, Object> inputs;
    private final Map<String, Object> outputs;

    public NodeExecutionContext(SputnikBlockEntity blockEntity, Map<String, Object> inputs, Map<String, Object> outputs) {
        this.blockEntity = blockEntity;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public SputnikBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public Object getInput(String pinId) {
        return inputs.get(pinId);
    }

    public double getNumberInput(String pinId, double defaultValue) {
        Object val = inputs.get(pinId);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return defaultValue;
    }

    public double getInputAsDouble(String pinId, double defaultValue) {
        return getNumberInput(pinId, defaultValue);
    }

    public boolean getBooleanInput(String pinId, boolean defaultValue) {
        Object val = inputs.get(pinId);
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        return defaultValue;
    }

    public boolean getInputAsBoolean(String pinId, boolean defaultValue) {
        return getBooleanInput(pinId, defaultValue);
    }

    public Vector3d getVectorInput(String pinId, Vector3d defaultValue) {
        Object val = inputs.get(pinId);
        if (val instanceof Vector3d v) {
            return v;
        }
        return defaultValue;
    }

    public String getStringInput(String pinId, String defaultValue) {
        Object val = inputs.get(pinId);
        if (val != null) {
            return val.toString();
        }
        return defaultValue;
    }

    public String getInputAsString(String pinId, String defaultValue) {
        return getStringInput(pinId, defaultValue);
    }

    public void setOutput(String pinId, Object value) {
        outputs.put(pinId, value);
    }
}
