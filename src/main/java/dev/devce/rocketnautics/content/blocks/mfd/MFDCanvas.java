package dev.devce.rocketnautics.content.blocks.mfd;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Arrays;

public class MFDCanvas {
    public final int width;
    public final int height;
    private final int[] pixels;
    private boolean dirty = true;

    @OnlyIn(Dist.CLIENT)
    private DynamicTexture dynamicTexture;
    @OnlyIn(Dist.CLIENT)
    private ResourceLocation textureLocation;

    public MFDCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        clear(0xFF000000);
    }

    public MFDCanvas() {
        this(64, 64);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int[] getPixels() {
        return pixels;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void setPixel(int x, int y, int argb) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            pixels[y * width + x] = argb;
            dirty = true;
        }
    }

    public void setPixelFast(int x, int y, int argb) {
        pixels[y * width + x] = argb;
        dirty = true;
    }

    public int getPixel(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return pixels[y * width + x];
        }
        return 0;
    }

    public void clear(int argb) {
        Arrays.fill(pixels, argb);
        dirty = true;
    }

    public void fillRect(int x, int y, int w, int h, int argb) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(width, x + w);
        int y1 = Math.min(height, y + h);

        for (int cy = y0; cy < y1; cy++) {
            int rowOffset = cy * width;
            Arrays.fill(pixels, rowOffset + x0, rowOffset + x1, argb);
        }
        dirty = true;
    }

    public void drawRect(int x, int y, int w, int h, int argb) {
        drawHLine(x, x + w - 1, y, argb);
        drawHLine(x, x + w - 1, y + h - 1, argb);
        drawVLine(x, y, y + h - 1, argb);
        drawVLine(x + w - 1, y, y + h - 1, argb);
    }

    public void drawHLine(int x0, int x1, int y, int argb) {
        if (y < 0 || y >= height) return;
        int start = Math.max(0, Math.min(x0, x1));
        int end = Math.min(width - 1, Math.max(x0, x1));
        int row = y * width;
        for (int cx = start; cx <= end; cx++) {
            pixels[row + cx] = argb;
        }
        dirty = true;
    }

    public void drawVLine(int x, int y0, int y1, int argb) {
        if (x < 0 || x >= width) return;
        int start = Math.max(0, Math.min(y0, y1));
        int end = Math.min(height - 1, Math.max(y0, y1));
        for (int cy = start; cy <= end; cy++) {
            pixels[cy * width + x] = argb;
        }
        dirty = true;
    }

    public void drawLine(int x0, int y0, int x1, int y1, int argb) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            setPixel(x0, y0, argb);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    public void drawCircle(int xc, int yc, int r, int argb, boolean filled) {
        int x = 0;
        int y = r;
        int d = 3 - 2 * r;
        while (y >= x) {
            if (filled) {
                drawHLine(xc - x, xc + x, yc - y, argb);
                drawHLine(xc - x, xc + x, yc + y, argb);
                drawHLine(xc - y, xc + y, yc - x, argb);
                drawHLine(xc - y, xc + y, yc + x, argb);
            } else {
                setPixel(xc + x, yc + y, argb);
                setPixel(xc - x, yc + y, argb);
                setPixel(xc + x, yc - y, argb);
                setPixel(xc - x, yc - y, argb);
                setPixel(xc + y, yc + x, argb);
                setPixel(xc - y, yc + x, argb);
                setPixel(xc + y, yc - x, argb);
                setPixel(xc - y, yc - x, argb);
            }
            x++;
            if (d > 0) {
                y--;
                d = d + 4 * (x - y) + 10;
            } else {
                d = d + 4 * x + 6;
            }
        }
    }

    public void drawImageScaled(int[] srcPixels, int srcW, int srcH, int dstX, int dstY, int dstW, int dstH) {
        if (srcPixels == null || srcW <= 0 || srcH <= 0) return;

        for (int dy = 0; dy < dstH; dy++) {
            int py = dstY + dy;
            if (py < 0 || py >= height) continue;

            int sy = dy * srcH / dstH;
            int srcRow = sy * srcW;
            int dstRow = py * width;

            for (int dx = 0; dx < dstW; dx++) {
                int px = dstX + dx;
                if (px < 0 || px >= width) continue;

                int sx = dx * srcW / dstW;
                int pixel = srcPixels[srcRow + sx];
                pixels[dstRow + px] = pixel;
            }
        }
        dirty = true;
    }

    public void drawImageFullScreen(int[] srcPixels, int srcW, int srcH) {
        drawImageScaled(srcPixels, srcW, srcH, 0, 0, width, height);
    }

    public void drawString(String text, int x, int y, int color) {
        if (text == null) return;
        int curX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                curX = x;
                y += 6;
                continue;
            }
            drawChar(c, curX, y, color);
            curX += 4;
        }
    }

    public void drawChar(char c, int x, int y, int color) {
        if (c >= 'a' && c <= 'z') {
            c = (char) (c - 32);
        }
        int charIdx = c - 32;
        if (charIdx < 0 || charIdx >= FONT_3X5.length) {
            charIdx = '?' - 32;
        }
        int glyph = FONT_3X5[charIdx];
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                if ((glyph & (1 << (14 - (row * 3 + col)))) != 0) {
                    setPixel(x + col, y + row, color);
                }
            }
        }
    }

    private static final short[] FONT_3X5 = {
            (short) 0x0000, (short) 0x2492, (short) 0x5A80, (short) 0x5EFD, (short) 0x3916, (short) 0x52A5, (short) 0x2AA3, (short) 0x2400,
            (short) 0x2922, (short) 0x212A, (short) 0x0B50, (short) 0x05D0, (short) 0x0014, (short) 0x01C0, (short) 0x0002, (short) 0x12A4,
            (short) 0x7B6F, (short) 0x2C97, (short) 0x73E7, (short) 0x73CF, (short) 0x5BC9, (short) 0x74CF, (short) 0x74EF, (short) 0x7249,
            (short) 0x7BEF, (short) 0x7BCF, (short) 0x0410, (short) 0x0414, (short) 0x1489, (short) 0x0E38, (short) 0x4224, (short) 0x7362,
            (short) 0x7BEE, (short) 0x2BED, (short) 0x6DB6, (short) 0x7497, (short) 0x6B6E, (short) 0x74E7, (short) 0x74E4, (short) 0x74AF,
            (short) 0x5BED, (short) 0x724F, (short) 0x12DF, (short) 0x5B6D, (short) 0x4927, (short) 0x5FED, (short) 0x6B6D, (short) 0x7B6F,
            (short) 0x7BF4, (short) 0x7B79, (short) 0x7BFD, (short) 0x74CF, (short) 0x7249, (short) 0x5B6F, (short) 0x5B6A, (short) 0x5BFD,
            (short) 0x5AA5, (short) 0x5A49, (short) 0x72A7, (short) 0x324E, (short) 0x4111, (short) 0x3926, (short) 0x2500, (short) 0x0007
    };

    @OnlyIn(Dist.CLIENT)
    public void bindTexture(String textureId) {
        if (dynamicTexture == null) {
            dynamicTexture = new DynamicTexture(width, height, false);
            textureLocation = net.minecraft.client.Minecraft.getInstance().getTextureManager()
                    .register(textureId, dynamicTexture);
        }
        if (dirty) {
            NativeImage img = dynamicTexture.getPixels();
            if (img != null) {
                for (int y = 0; y < height; y++) {
                    int row = y * width;
                    for (int x = 0; x < width; x++) {
                        int argb = pixels[row + x];
                        int a = (argb >> 24) & 0xFF;
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;
                        img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                    }
                }
                dynamicTexture.upload();
            }
            dirty = false;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    @OnlyIn(Dist.CLIENT)
    public void close() {
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
    }
}
