package dev.devce.rocketnautics.content.blocks.mfd;

import dev.devce.rocketnautics.content.blocks.mfd.programs.AltimeterSpeedProgram;
import dev.devce.rocketnautics.content.blocks.mfd.programs.AttitudeIndicatorProgram;
import dev.devce.rocketnautics.content.blocks.mfd.programs.ExternalVideoProgram;
import dev.devce.rocketnautics.content.blocks.mfd.programs.FDAIProgram;
import dev.devce.rocketnautics.content.blocks.mfd.programs.TerrainMapProgram;

import java.util.ArrayList;
import java.util.List;

public class MFDPrograms {
    private static final List<MFDProgram> REGISTRY = new ArrayList<>();

    static {
        register(new FDAIProgram());
        register(new AttitudeIndicatorProgram());
        register(new AltimeterSpeedProgram());
        register(new TerrainMapProgram());
        register(new ExternalVideoProgram());
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

    public static List<String> getNames() {
        List<String> list = new ArrayList<>();
        for (MFDProgram p : REGISTRY) {
            list.add(p.getName());
        }
        return list;
    }

    public static int findIndexByName(String name) {
        if (name == null) return -1;
        String lower = name.trim().toLowerCase();
        for (int i = 0; i < REGISTRY.size(); i++) {
            if (REGISTRY.get(i).getName().toLowerCase().equals(lower)) {
                return i;
            }
        }
        return -1;
    }
}
