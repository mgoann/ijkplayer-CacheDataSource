package com.fengxing.ams.tvclient.videomedia;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.Log;

import com.fengxing.ams.tvclient.Utils;
import com.fengxing.ams.tvclient.intf.IPageMediaDataSource;
import com.fengxing.ams.tvclient.util.StringUtil;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Administrator on 2016/6/4.
 */
public class NetworkMediaDataSource extends AbstractMediaDataSource {

    OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private boolean isUsingCache;

    public NetworkMediaDataSource(long videoId, String url, File cacheRootFile, boolean isUsingCache) {
        this.url = url;
        this.videoId = videoId;
        this.rootDisk = cacheRootFile;
        this.isUsingCache = isUsingCache;
        syncPageSet.clear();
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        //由于ijkplayer每次调用size很小，会频繁的创建连接，所以根据positoin，把这个分页的数据预取，然后保存到文件
        long exceptPage = this.getPageForPosition(position);
        if (exceptPage != this.getCurrentPage()) {
            //读取该分页的数据，从网络读取缓存数据
            return readFormNetwork(position, buffer, offset, size);
        } else {
            //直接从buffer中读取
            return this.readFormBuffer(position, buffer, offset, size);
        }
    }

    @Override
    public void desctory() {
        super.desctory();
    }

    private int readFormNetwork(long position, byte[] buffer, int offset, int size) throws IOException {
        int exceptedSize = 0;
        long exceptPage = this.getPageForPosition(position);
        long startPosition = this.getStartPosition(exceptPage);
        long endPosition = this.getEndPosition(exceptPage, size);
        Response response = null;
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Range", "bytes=" + startPosition + "-" + endPosition)
                    .build();
            response = client.newCall(request).execute();
            if (response.code() == 416) {
                //写入最大长度文件
//                FileUtils.writeStringToFile(new File(rootDisk, "/" + videoId + "/media.d"), String.valueOf(position));
                return -1;
            }
            byte[] b = response.body().bytes();
            if (b == null || b.length == 0) {
                //如果网络侧返回0，直接返回-1，视频肯定结束了
                return -1;
            }
            //Content-Rangebytes 6000000-17000000/67139006
            String responseRange = response.header("Content-Range");
            long firstPos = this.getFirstBytePos(responseRange);
            long lastPos = this.getLastBytePos(responseRange);
            long maxPos = this.getMaxBytePos(responseRange);
            if ((endPosition <= maxPos && lastPos != endPosition) || startPosition != firstPos) {
                Log.w(TAG, "read data form server not completed, start=" + startPosition + ", end=" + endPosition + ", first=" + firstPos + ", last=" + lastPos);
            }
            //将数据写入缓存
            this.write(position, b);
            //启动异步线程写cache文件
            if (!syncPageSet.contains(exceptPage)) {
                syncPageSet.add(exceptPage);
                //启动异常线程进行文件写入操作
                //检查缓存开关
                if (isUsingCache) {
                    new CacheAsyncTask(this.videoId, exceptPage, b, maxPos).execute();
                }
            }
        } finally {
            if (response != null)
                response.close();
        }
        return this.readFormBuffer(position, buffer, offset, size);
    }

    private long getFirstBytePos(String responseRange) {
        return Long.valueOf(StringUtil.subString(responseRange, " ", "-"));
    }

    private long getLastBytePos(String responseRange) {
        return Long.valueOf(StringUtil.subString(responseRange, "-", "/"));
    }

    private long getMaxBytePos(String responseRange) {
        return Long.valueOf(StringUtil.subString(responseRange, "/", null));
    }

    /**
     * 尽量避免Task直接访问CacheOutputStream的成员变量，避免线程安全问题
     */
    private class CacheAsyncTask extends AsyncTask<Object, Object, Object> {
        private long videoIdTask;
        private long pageTask;
        private byte[] b;
        private long maxPos;

        public CacheAsyncTask(long videoId, long page, byte[] b, long maxPos) {
            this.videoIdTask = videoId;
            this.pageTask = page;
            this.b = b;
            this.maxPos = maxPos;
        }

        @Override
        protected Object doInBackground(Object... params) {
            File cacheFile = new File(rootDisk, "/" + videoIdTask + "/" + pageTask + ".c");
            File mediaFile = new File(rootDisk, "/" + videoIdTask + "/media.c");
            try {
                //写文件前先判断磁盘空间
                StatFs sf = new StatFs(rootDisk.getPath());
                //预留500M空间
                long freeSize = sf.getFreeBytes() - 500 * 1024 * 1024;
                if (freeSize > b.length && !cacheFile.exists()) {
                    FileUtils.writeByteArrayToFile(cacheFile, b);
                } else {
                    //删除旧视频文件
                    File[] files =  rootDisk.listFiles();
                    long lastMidified = Long.MAX_VALUE;
                    File lastMidifiedFile = null;
                    for (File file : files) {
                        if (file.lastModified() < lastMidified) {
                            lastMidifiedFile = file;
                            lastMidified = file.lastModified();
                        }
                    }
                    if (lastMidifiedFile != null) {
                        Utils.deleteFile(lastMidifiedFile);
                    }
                }

                if (!mediaFile.exists()) {
                    FileUtils.writeStringToFile(mediaFile, String.valueOf(maxPos));
                }
            } catch (FileNotFoundException e) {
                android.util.Log.e(TAG, android.util.Log.getStackTraceString(e));
            } catch (IOException e) {
                android.util.Log.e(TAG, android.util.Log.getStackTraceString(e));
            }
            syncPageSet.remove(pageTask);

            return null;
        }
    }



}
