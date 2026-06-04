package com.yaahua.vcam;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.SurfaceHolder;

public class SharedState {
    // ========== Camera1 状态 ==========
    public static Surface mSurface;
    public static SurfaceTexture mSurfacetexture;
    public static MediaPlayer mMediaPlayer;
    public static SurfaceTexture fake_SurfaceTexture;
    public static Camera origin_preview_camera;
    public static Camera camera_onPreviewFrame;
    public static Camera start_preview_camera;
    public static volatile byte[] data_buffer = {0};
    public static byte[] input;
    public static int mhight;
    public static int mwidth;
    public static boolean is_someone_playing;
    public static boolean is_hooked;
    public static VideoToFrames hw_decode_obj;
    public static SurfaceTexture c1_fake_texture;
    public static Surface c1_fake_surface;
    public static SurfaceHolder ori_holder;
    public static MediaPlayer mplayer1;
    public static Camera mcamera1;
    public static Class camera_callback_calss;
    public static int onemhight;
    public static int onemwidth;

    // ========== Camera2 状态 ==========
    public static Surface c2_preview_Surfcae;
    public static Surface c2_preview_Surfcae_1;
    public static Surface c2_reader_Surfcae;
    public static Surface c2_reader_Surfcae_1;
    public static MediaPlayer c2_player;
    public static MediaPlayer c2_player_1;
    public static Surface c2_virtual_surface;
    public static SurfaceTexture c2_virtual_surfaceTexture;
    public static boolean need_recreate;
    public static CameraDevice.StateCallback c2_state_cb;
    public static CaptureRequest.Builder c2_builder;
    public static SessionConfiguration fake_sessionConfiguration;
    public static SessionConfiguration sessionConfiguration;
    public static OutputConfiguration outputConfiguration;
    public static VideoToFrames c2_hw_decode_obj;
    public static VideoToFrames c2_hw_decode_obj_1;
    public static boolean is_first_hook_build = true;
    public static int imageReaderFormat = 0;
    public static int c2_ori_width = 1280;
    public static int c2_ori_height = 720;
    public static Class c2_state_callback;

    // ========== 通用状态 ==========
    public static String video_path = "/storage/emulated/0/DCIM/Camera1/";
    public static boolean need_to_show_toast = true;
    public static Context toast_content;

    // ========== 播放控制 ==========
    public static volatile long seekPositionMs = -1;  // <0 表示不seek
    public static volatile boolean playPaused = false;
    public static volatile String currentVideoPath = null;
    // 断点续传：Map<文件路径, 播放位置ms>
    public static final java.util.Map<String, Long> videoPositions = new java.util.concurrent.ConcurrentHashMap<>();
}