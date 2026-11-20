package com.mogutofu.mcai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * 截图实现 —— MC 26.2 / 26.3 版（{@code GameRenderer#mainRenderTarget()}）。
 *
 * <p>26.1 用同名类覆盖（{@code src/versioned/26_1}），那边还是
 * {@code Minecraft#getMainRenderTarget()}。两个文件由 client/build.gradle
 * 按 {@code -Pmc=} 选一个编进来。</p>
 *
 * <p>26.x 的 {@code Screenshot.takeScreenshot(target, Consumer<NativeImage>)} 没有返回值，
 * 也没有 {@code NativeImage.asByteArray()}，所以回调里先落临时 PNG 再读字节。</p>
 */
public final class ScreenshotCapture {

    private ScreenshotCapture() {
    }

    public static void capture(Minecraft minecraft, Consumer<byte[]> onImage, Consumer<Throwable> onError) {
        var target = minecraft.gameRenderer.mainRenderTarget();
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
