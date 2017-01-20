package com.fengxing.ams.tvclient.videomedia;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.fengxing.ams.tvclient.R;
import com.fengxing.ams.tvclient.Utils;
import com.fengxing.ams.tvclient.intf.IPageMediaDataSource;
import com.fengxing.ams.tvclient.intf.PlayStatusCallback;
import com.fengxing.ams.tvclient.util.IOUtil;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;

import io.vov.vitamio.utils.Log;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;

/**
 * Created by Administrator on 2016/5/26.
 */
public class CacheMediaDataSource implements IMediaDataSource, PlayStatusCallback {

    private final long totalSize;
    private long videoId;
    private String url;
    private static final String TAG = "CacheMediaDataSource";

    private IPageMediaDataSource pipeMediaDataSource = null;

    private int errorTime = 0;

    public CacheMediaDataSource(String url, long videoId, long totalSize, Context context) {
        this.url = url;
        this.videoId = videoId;
        this.totalSize = totalSize;
        SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isUsingCache = preference.getBoolean("pref.using_media_cache", true);
        Log.i(TAG, "isUsingCache=" + isUsingCache);
        pipeMediaDataSource = new PipeMediaDataSource(videoId, url, context, isUsingCache);
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        try {
            if (size == 0)
                return 0;
            //读取server端的数据填充到buffer
            //判断是否有缓存文件
            long exceptPosition = position + offset;
            int readLength = pipeMediaDataSource.readAt(exceptPosition, buffer, offset, size);
            //每次正常返回都将计算器重置
            errorTime = 0;
            return readLength;
        } catch (Exception e) {
            android.util.Log.e(TAG, android.util.Log.getStackTraceString(e));
            //底层抛异常上来的话计数，如果连续3次都抛异常，则直接抛出异常给上层
            errorTime++;
            if (errorTime >= 3) {
                //抛出异常
//                throw e;
            }
            //返回0会触发buffer操作，如果底层出错应该给几次机会进行buffer
            return 0;
        }
    }

    @Override
    public long getSize() throws IOException {
        return totalSize;
    }

    @Override
    public void close() throws IOException {
        if (pipeMediaDataSource != null) {
            pipeMediaDataSource.desctory();
            pipeMediaDataSource = null;
        }
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onStop() {

    }
}
