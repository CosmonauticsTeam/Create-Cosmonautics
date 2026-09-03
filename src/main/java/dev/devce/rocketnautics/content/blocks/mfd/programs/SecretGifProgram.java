package dev.devce.rocketnautics.content.blocks.mfd.programs;

import dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity;
import dev.devce.rocketnautics.content.blocks.mfd.MFDCanvas;
import dev.devce.rocketnautics.content.blocks.mfd.MFDProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SecretGifProgram implements MFDProgram {
    private static final ResourceLocation GIF_LOCATION = ResourceLocation.fromNamespaceAndPath("websnodelib", "textures/gui/secret.gif");

    private static final List<int[]> SHARED_FRAMES = new ArrayList<>();
    private static final List<Integer> SHARED_DELAYS = new ArrayList<>();
    private static int totalDurationMs = 0;
    private static boolean loaded = false;
    private static boolean failed = false;

    @Override
    public String getName() {
        return "Secret GIF";
    }

    private static synchronized void ensureLoaded(int targetW, int targetH) {
        if (loaded || failed) return;
        try {
            var resourceOptional = Minecraft.getInstance().getResourceManager().getResource(GIF_LOCATION);
            if (resourceOptional.isEmpty()) {
                failed = true;
                return;
            }

            try (InputStream is = resourceOptional.get().open();
                 ImageInputStream stream = ImageIO.createImageInputStream(is)) {
                var readers = ImageIO.getImageReadersByFormatName("gif");
                if (!readers.hasNext()) {
                    failed = true;
                    return;
                }
                ImageReader reader = readers.next();
                reader.setInput(stream);

                int count = reader.getNumImages(true);
                for (int i = 0; i < count; i++) {
                    BufferedImage bImg = reader.read(i);
                    int srcW = bImg.getWidth();
                    int srcH = bImg.getHeight();

                    int[] raw = new int[srcW * srcH];
                    bImg.getRGB(0, 0, srcW, srcH, raw, 0, srcW);

                    int[] scaled = new int[targetW * targetH];
                    for (int dy = 0; dy < targetH; dy++) {
                        int sy = dy * srcH / targetH;
                        int srcRow = sy * srcW;
                        int dstRow = dy * targetW;
                        for (int dx = 0; dx < targetW; dx++) {
                            int sx = dx * srcW / targetW;
                            scaled[dstRow + dx] = raw[srcRow + sx];
                        }
                    }

                    SHARED_FRAMES.add(scaled);
                    SHARED_DELAYS.add(100);
                    totalDurationMs += 100;
                }
                reader.dispose();
                loaded = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            failed = true;
        }
    }

    @Override
    public void render(MFDCanvas canvas, MFDBlockEntity blockEntity, float partialTicks) {
        ensureLoaded(canvas.width, canvas.height);

        if (failed || SHARED_FRAMES.isEmpty()) {
            canvas.clear(0xFF101015);
            canvas.drawString("secret.gif not found", 8, 60, 0xFFFF3333);
            return;
        }

        long time = System.currentTimeMillis();
        long elapsed = time % Math.max(1, totalDurationMs);

        int currentFrame = 0;
        long acc = 0;
        for (int i = 0; i < SHARED_FRAMES.size(); i++) {
            acc += SHARED_DELAYS.get(i);
            if (elapsed < acc) {
                currentFrame = i;
                break;
            }
        }

        int[] src = SHARED_FRAMES.get(currentFrame);
        System.arraycopy(src, 0, canvas.getPixels(), 0, canvas.width * canvas.height);
        canvas.markDirty();
    }
}
