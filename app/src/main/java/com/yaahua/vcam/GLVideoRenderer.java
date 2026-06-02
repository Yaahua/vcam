package com.yaahua.vcam;

import android.view.Surface;

/** GL视频渲染器存根。后续版本将实现 OpenGL 旋转渲染。 */
public class GLVideoRenderer {
    private Surface inputSurface;
    private boolean initialized;

    private GLVideoRenderer(Surface outputSurface, String tag) {
        // 存根实现：直接使用 outputSurface
        this.inputSurface = outputSurface;
        this.initialized = (outputSurface != null);
    }

    public static GLVideoRenderer createSafely(Surface output, String tag) {
        if (output == null) return null;
        try {
            return new GLVideoRenderer(output, tag);
        } catch (Exception e) {
            LogUtil.log("【CS】GLVideoRenderer 创建失败: " + e);
            return null;
        }
    }

    public static void releaseSafely(GLVideoRenderer renderer) {
        if (renderer != null) {
            try {
                renderer.inputSurface = null;
                renderer.initialized = false;
            } catch (Exception ignored) {}
        }
    }

    public Surface getInputSurface() { return inputSurface; }
    public boolean isInitialized() { return initialized; }
    public void setRotation(int degrees) {}

    public android.graphics.Bitmap captureFrameWithRotation(int w, int h, int rotation) {
        return null;
    }
}