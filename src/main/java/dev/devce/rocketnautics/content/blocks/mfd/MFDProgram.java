package dev.devce.rocketnautics.content.blocks.mfd;

public interface MFDProgram {
    String getName();
    void render(MFDCanvas canvas, MFDBlockEntity blockEntity, float partialTicks);
}
