package com.mogutofu.mcai.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 截图：GL11.glReadPixels 读主 framebuffer + ImageIO 写 PNG（Java 8 自带，不额外打包依赖）。
 *
 * <p>1.12.2 的 {@code ScreenShotHelper} 只会往 screenshots/ 落盘再回一句翻译文本，
 * 拿字节还得再读一次文件，所以这里直接读像素自己编码。像素是左下角原点，写 PNG 前上下翻一次。</p>
 */
final class ScreenshotCapture {

    private ScreenshotCapture() {
    }

    /** @return PNG 字节；读不到画面时返回空数组 */
    static byte[] capture() throws IOException {
        Minecraft minecraft = Minecraft.getMinecraft();
        int width = Display.getWidth();
        int height = Display.getHeight();
        if (width <= 0 || height <= 0) {
            return new byte[0];
        }

        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        Framebuffer framebuffer = minecraft.getFramebuffer();
        if (framebuffer != null) {
            framebuffer.bindFramebuffer(true);
        }
        try {
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            if (framebuffer != null) {
                framebuffer.unbindFramebuffer();
            }
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] row = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < height; y++) {
            int sourceRow = (height - y - 1) * width * 4;
            int targetRow = y * width;
            for (int x = 0; x < width; x++) {
                int index = sourceRow + x * 4;
                int red = pixels.get(index) & 0xFF;
                int green = pixels.get(index + 1) & 0xFF;
                int blue = pixels.get(index + 2) & 0xFF;
                row[targetRow + x] = (red << 16) | (green << 8) | blue;
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, width * height / 4));
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("没有可用的 PNG 编码器");
        }
        return out.toByteArray();
    }
}
