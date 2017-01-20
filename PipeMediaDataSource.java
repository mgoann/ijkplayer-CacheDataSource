package com.fengxing.ams.tvclient.videomedia;

import android.content.Context;

import com.fengxing.ams.tvclient.Utils;
import com.fengxing.ams.tvclient.intf.IPageMediaDataSource;

import java.io.IOException;

/**
 * Created by Administrator on 2016/6/3.
 */
public class PipeMediaDataSource extends AbstractMediaDataSource {

    private IPageMediaDataSource diskMediaDataSource;
    private IPageMediaDataSource networkMediaDataSource;



    public PipeMediaDataSource(long videoId, String url, Context context, boolean isUsingCache) {
        //初始化diskDataSource和networkDataSource
        diskMediaDataSource = new DiskMediaDataSource(videoId, Utils.getCacheRootFile(context));
        networkMediaDataSource = new NetworkMediaDataSource(videoId, url, Utils.getCacheRootFile(context), isUsingCache);
    }

    /**
     * 读取数据内容，数据有可能来至disk，也可能是网络，也可能是两者集合
     * @param position
     * @param buffer
     * @param offset
     * @param size
     * @return
     */
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        //计算page
        long startPage = this.getPageForPosition(position);
        long endPage = this.getPageForPosition(position+size-1);
        int readLength = 0;
        int totalSize = size;
        for (long i = 0; i <= endPage - startPage; i++) {
            //计算起始位
            long startPosition = position + readLength;
            //计算本页读取的size
            int exceptSize = 0;
            int leftSize = (int) (DiskMediaDataSource.PAGE_MAX_SIZE - (startPosition % DiskMediaDataSource.PAGE_MAX_SIZE));
            if (leftSize > totalSize) {
                exceptSize = totalSize;
            } else {
                exceptSize = leftSize;
            }
            int readLengthFormDisk = diskMediaDataSource.readAt(startPosition, buffer, offset + readLength, exceptSize);
            if (readLengthFormDisk == -1 && readLength <= 1)
                return -1;
            //disk中没有读取到
            int readLengthFormNetwork = 0;
            if (readLengthFormDisk == 0)
                readLengthFormNetwork = networkMediaDataSource.readAt(startPosition, buffer, offset + readLength, exceptSize);
            //如果返回-1，视频结束
            if (readLengthFormNetwork == -1  && readLength <= 1)
                return -1;
            readLength += (readLengthFormDisk + readLengthFormNetwork);
            totalSize = size - readLength;
        }
        return readLength;
    }

    @Override
    public void desctory() {
        if (diskMediaDataSource != null)
            diskMediaDataSource.desctory();
        if (networkMediaDataSource != null)
            networkMediaDataSource.desctory();
    }

}
