package dev.devce.rocketnautics.content.sputnik.model;

import java.util.Objects;

public class SputnikLink {
    private final int fromNodeId;
    private final String fromPinId;
    private final int toNodeId;
    private final String toPinId;

    public SputnikLink(int fromNodeId, String fromPinId, int toNodeId, String toPinId) {
        this.fromNodeId = fromNodeId;
        this.fromPinId = fromPinId;
        this.toNodeId = toNodeId;
        this.toPinId = toPinId;
    }

    public int getFromNodeId() {
        return fromNodeId;
    }

    public String getFromPinId() {
        return fromPinId;
    }

    public int getToNodeId() {
        return toNodeId;
    }

    public String getToPinId() {
        return toPinId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SputnikLink link)) return false;
        return fromNodeId == link.fromNodeId &&
                toNodeId == link.toNodeId &&
                Objects.equals(fromPinId, link.fromPinId) &&
                Objects.equals(toPinId, link.toPinId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromNodeId, fromPinId, toNodeId, toPinId);
    }
}
