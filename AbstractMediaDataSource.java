package com.fengxing.ams.tvclient.videomedia;

import com.fengxing.ams.tvclient.intf.IPageMediaDataSource;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Administrator on 2016/6/4.
 */
public abstract class AbstractMediaDataSource implements IPageMediaDataSource {
    protected static final String TAG = "CacheDataSource";

    private final Lock lock = new ReentrantLock();

    //一次申明重复利用，子类不能直接操作cache
    private ByteBuffer cacheBuffer = ByteBuffer.allocate((int) PAGE_MAX_SIZE);
    protected String url;
    private long currentPage = -1;
    protected long videoId;
    protected File rootDisk;
    private int zeroBackTime = 0;

    @Override
    public abstract int readAt(long position, byte[] buffer, int offset, int size) throws IOException;

    @Override
    public void desctory() {
        lock.lock();

        try {
            cacheBuffer.clear();
            cacheBuffer = null;
            currentPage = -1;
        } finally {
            lock.unlock();
        }

    }

    public int readFormBuffer(long position, byte[] buffer, int offset, int size) {
        lock.lock();

        try {
            cacheBuffer.rewind();
            int exceptPosition = (int) (position % PAGE_MAX_SIZE);
            if (exceptPosition >= cacheBuffer.limit())
                return -1;
            cacheBuffer.position(exceptPosition);
            //如果没有元素则返回0
            if (!cacheBuffer.hasRemaining()) {
                zeroBackTime++;
                if (zeroBackTime > 3) {
                    return -1;
                }
                return 0;
            } else {
                zeroBackTime = 0;
            }
            int exceptSize = size > cacheBuffer.remaining() ? cacheBuffer.remaining() : size;
            cacheBuffer.get(buffer, offset, exceptSize);
            return exceptSize;
        } finally {
            lock.unlock();
        }

    }

    public long getPageForPosition(long position) {
        return position / PAGE_MAX_SIZE;
    }

    public long getCurrentPage() {
        return this.currentPage;
    }

    public long getStartPosition(long page) {
        return page * PAGE_MAX_SIZE;
    }

    public long getEndPosition(long page, int size) {
        return ((page + 1L) * PAGE_MAX_SIZE) - 1L;
    }

    public void write(long position, byte[] b) {
        lock.lock();

        try {
            //清理缓存数据，将位置设置为 0，将限制设置为容量
            if (cacheBuffer != null) {
                cacheBuffer.clear();
                //放入数据
                cacheBuffer.put(b).position(0).limit(b.length);
                this.currentPage = this.getPageForPosition(position);
            }
        } finally {
            lock.unlock();
        }

    }
}
