package com.yaahua.vcam;

/** MediaCodec YUV 解码器存根。后续版本实现从视频帧中提取 YUV 数据。 */
public class MediaCodecYuvDecoder {
    public MediaCodecYuvDecoder() {}

    public void setTargetSize(int width, int height) {}
    public void decode(String videoPath) {
        LogUtil.log("【CS】MediaCodecYuvDecoder 存根");
    }
    public void stopDecode() {}
    public byte[] getLatestFrame() { return null; }
}