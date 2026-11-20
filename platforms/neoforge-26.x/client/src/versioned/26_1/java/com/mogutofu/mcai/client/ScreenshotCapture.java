package com.mogutofu.mcai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * 截图实现 —— MC 26.1 版（{@code Minecraft#getMainRenderTarget()}）。
 *
 * <p>26.2 起这个访问器搬到了 {@code GameRenderer#mainRenderTarget()}，
 * 所以这里单独一份；两个文件由 client/build.gradle 按 {@code -Pmc=} 二选一。</p>
 */
public final class ScreenshotCapture {

    private ScreenshotCapture() {
    }

    public static void capture(Minecraft minecraft, Consumer<byte[]> onImage, Consumer<Throwable> onError) {
        var target = minecraft.getMainRenderTarget();
        Screenshot.takeScreenshot(target, image -> {
            File temp = null;
            try {
                temp = File.createTempFile("mcai-shot", ".png");
                try {
                    image.writeToFile(temp);
                } finally {
                    image.close();
                }
                byte[] bytes = Files.readAllBytes(temp.toPath());
                onImage.accept(bytes);
            } catch (Throwable broken) {
                onError.accept(broken);
            } finally {
                if (temp != null && !temp.delete()) {
                    temp.deleteOnExit();
                }
            }
        });
    }
}
