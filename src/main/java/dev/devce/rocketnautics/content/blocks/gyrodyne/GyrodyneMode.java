package dev.devce.rocketnautics.content.blocks.gyrodyne;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum GyrodyneMode implements StringRepresentable {
    OFF("off", "gui.rocketnautics.gyrodyne.mode.off"),
    SAS("sas", "gui.rocketnautics.gyrodyne.mode.sas"),
    HOLD("hold", "gui.rocketnautics.gyrodyne.mode.hold"),
    PROGRADE("prograde", "gui.rocketnautics.gyrodyne.mode.prograde"),
    RETROGRADE("retrograde", "gui.rocketnautics.gyrodyne.mode.retrograde"),
    NORMAL("normal", "gui.rocketnautics.gyrodyne.mode.normal"),
    ANTINORMAL("antinormal", "gui.rocketnautics.gyrodyne.mode.antinormal"),
    RADIAL_IN("radial_in", "gui.rocketnautics.gyrodyne.mode.radial_in"),
    RADIAL_OUT("radial_out", "gui.rocketnautics.gyrodyne.mode.radial_out"),
    HORIZON("horizon", "gui.rocketnautics.gyrodyne.mode.horizon"),
    SUN("sun", "gui.rocketnautics.gyrodyne.mode.sun");

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

    public String getTranslationKey() {
        return translationKey;
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

