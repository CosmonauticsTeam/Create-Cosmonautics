package dev.devce.rocketnautics.content.sputnik.model;

public class SputnikPin {
    private final String id;
    private final String name;
    private final PinType type;
    private final boolean input;

    public SputnikPin(String id, String name, PinType type, boolean input) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.input = input;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PinType getType() {
        return type;
    }

    public boolean isInput() {
        return input;
    }
}
