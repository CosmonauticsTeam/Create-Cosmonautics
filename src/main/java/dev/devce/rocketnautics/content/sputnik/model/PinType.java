package dev.devce.rocketnautics.content.sputnik.model;

import imgui.ImColor;

public enum PinType {
    NUMBER(ImColor.rgb(63, 195, 217)),
    BOOLEAN(ImColor.rgb(73, 209, 100)),
    VECTOR3(ImColor.rgb(180, 100, 240)),
    STRING(ImColor.rgb(235, 150, 60));

    private final int color;

    PinType(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }
}
