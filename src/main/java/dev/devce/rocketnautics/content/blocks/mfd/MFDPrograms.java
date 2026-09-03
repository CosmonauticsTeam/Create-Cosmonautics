package dev.devce.rocketnautics.content.blocks.mfd;

import dev.devce.rocketnautics.content.blocks.mfd.programs.AltimeterSpeedProgram;
import dev.devce.rocketnautics.content.blocks.mfd.programs.AttitudeIndicatorProgram;
import dev.devce.rocketnautics.content.blocks.mfd.programs.FDAIProgram;
import dev.devce.rocketnautics.content.blocks.mfd.programs.SecretGifProgram;

import java.util.ArrayList;
import java.util.List;

public class MFDPrograms {
    private static final List<MFDProgram> REGISTRY = new ArrayList<>();

    static {
        register(new FDAIProgram());
        register(new AttitudeIndicatorProgram());
        register(new AltimeterSpeedProgram());
        register(new SecretGifProgram());
    }

    public static void register(MFDProgram program) {
        REGISTRY.add(program);
    }

    public static int getCount() {
        return REGISTRY.size();
    }

    public static MFDProgram get(int index) {
        if (REGISTRY.isEmpty()) return null;
        int idx = Math.floorMod(index, REGISTRY.size());
        return REGISTRY.get(idx);
    }
}
