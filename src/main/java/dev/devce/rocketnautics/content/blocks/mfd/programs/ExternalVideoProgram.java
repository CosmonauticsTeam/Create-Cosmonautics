package dev.devce.rocketnautics.content.blocks.mfd.programs;

import dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity;
import dev.devce.rocketnautics.content.blocks.mfd.MFDCanvas;
import dev.devce.rocketnautics.content.blocks.mfd.MFDProgram;

public class ExternalVideoProgram implements MFDProgram {

    private static final int BG_COLOR       = 0xFF04060A;
    private static final int SCANLINE_COLOR = 0xFF070C14;
    private static final int FRAME_BORDER   = 0xFF0F2030;
    private static final int TEXT_GREEN     = 0xFF00FF88;
    private static final int TEXT_MUTED     = 0xFF008877;
    private static final int ACCENT_CYAN    = 0xFF00E5FF;

    @Override
    public String getName() {
        return "VIDEO";
    }

    @Override
    public void render(MFDCanvas canvas, MFDBlockEntity blockEntity, float partialTicks) {
        if (blockEntity != null && blockEntity.hasExternalVideo()) {
            int[] buffer = blockEntity.getExternalVideoBuffer();
            if (buffer != null && buffer.length == 64 * 64) {
                System.arraycopy(buffer, 0, canvas.getPixels(), 0, 64 * 64);
                canvas.markDirty();
                return;
            }
        }
        renderStandbyScreen(canvas);
    }

    private void renderStandbyScreen(MFDCanvas canvas) {
        canvas.clear(BG_COLOR);

        for (int y = 0; y < 64; y += 4) {
            canvas.drawHLine(0, 63, y, SCANLINE_COLOR);
        }

        canvas.drawRect(0, 0, 64, 64, FRAME_BORDER);
        canvas.drawRect(4, 4, 56, 56, FRAME_BORDER);

        canvas.drawString("AV-1", 7, 7, TEXT_GREEN);
        canvas.drawString("64X64", 41, 7, TEXT_MUTED);

        canvas.drawString("NO SIGNAL", 15, 27, ACCENT_CYAN);
        canvas.drawString("STANDBY", 19, 36, TEXT_MUTED);

        canvas.drawHLine(14, 50, 44, FRAME_BORDER);
    }
}
