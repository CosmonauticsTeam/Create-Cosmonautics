package dev.devce.rocketnautics.content.blocks.gyrodyne;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum GyrodyneMode implements StringRepresentable {
    OFF("off", "gui.rocketnautics.gyrodyne.mode.off"),
    SAS("sas", "gui.rocketnautics.gyrodyne.mode.sas"),
    PROGRADE("prograde", "gui.rocketnautics.gyrodyne.mode.prograde"),
    RETROGRADE("retrograde", "gui.rocketnautics.gyrodyne.mode.retrograde");

    private final String name;
    private final String translationKey;

    GyrodyneMode(String name, String translationKey) {
        this.name = name;
        this.translationKey = translationKey;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component getComponent() {
        return Component.translatable(translationKey);
    }

    public static GyrodyneMode fromIndex(int index) {
        GyrodyneMode[] values = values();
        if (index < 0 || index >= values.length) return OFF;
        return values[index];
    }
}
