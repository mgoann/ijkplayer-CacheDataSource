package com.fengxing.ams.tvclient.videomedia;

import android.util.Log;

import com.fengxing.ams.tvclient.intf.IPageMediaDataSource;
import com.fengxing.ams.tvclient.util.ArraysUtil;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by Administrator on 2016/6/3.
 */
public class DiskMediaDataSource extends AbstractMediaDataSource {

    private long maxPosition = Long.MAX_VALUE;

    public DiskMediaDataSource (long videoId, File rootDisk) {
        this.videoId = videoId;
        this.rootDisk = rootDisk;
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        long expectPage = getPageForPosition(position);
        if (syncPageSet.contains(expectPage)) {
            //正在写缓存文件，直接返回0，代表读取失败，从网络侧读取
            return 0;
        }

        if (maxPosition == Long.MAX_VALUE) {
            //设置maxPosition
            File mediaFile = new File(rootDisk, "/" + videoId + "/media.c");
            if (mediaFile.exists()) {
                try {
                    maxPosition = Long.valueOf(FileUtils.readFileToString(mediaFile));
                } catch (Exception e) {
                    Log.e(TAG, "read max postion error" + Log.getStackTraceString(e));
                }
            }
        }

        //判断position是否大于maxPosition
        if (position >= maxPosition)
            return -1;

        //外部必须保证获取的数据在一页范围内
        int readLength = 0;
        if (expectPage == this.getCurrentPage()) {
            //分页文件已经读取到缓存，直接从缓存获取数据
            readLength = readFormBuffer(position, buffer, offset, size);
            return readLength;
        } else {
            if (getDiskPageData(position)) {
                readLength = readFormBuffer(position, buffer, offset, size);
                return readLength;
            }
        }
        //其他情况统一返回0，表示没有读取到数据
        return 0;
    }

    private boolean getDiskPageData(long position) {
        long page = this.getPageForPosition(position);
        File pageFile = new File(rootDisk, "/" + videoId + "/" + page + ".c");
        //如果缓存文件不存在，不可读
        if (!pageFile.exists() && !pageFile.canRead())
            return false;
        //正在写入，则返回false
        if (syncPageSet.contains(page))
            return false;
        //如果不是最后一页则校验数据长度是否合法
        if (this.getMaxPageNum() != page) {
            //如果不是最后一页数据，则校验起长度
            if (pageFile.length() != PAGE_MAX_SIZE) {
                pageFile.deleteOnExit();
                return false;
            }

        }
        try {
            byte[] b = FileUtils.readFileToByteArray(pageFile);
            this.write(position, b);
            return true;
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return false;
        }
    }

    private long getMaxPageNum() {
        if (maxPosition == Long.MAX_VALUE)
            return -1;
        return this.getPageForPosition(maxPosition);
    }

    @Override
    public void desctory() {
        super.desctory();
    }
}
