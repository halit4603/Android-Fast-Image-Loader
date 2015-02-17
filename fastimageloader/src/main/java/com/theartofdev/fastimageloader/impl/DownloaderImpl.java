// "Therefore those skilled at the unorthodox
// are infinite as heaven and earth,
// inexhaustible as the great rivers.
// When they come to an end,
// they begin again,
// like the days and months;
// they die and are reborn,
// like the four seasons."
//
// - Sun Tsu,
// "The Art of War"

package com.theartofdev.fastimageloader.impl;

import android.content.Context;
import android.os.Handler;

import com.theartofdev.fastimageloader.Decoder;
import com.theartofdev.fastimageloader.HttpClient;
import com.theartofdev.fastimageloader.MemoryPool;
import com.theartofdev.fastimageloader.impl.util.FILLogger;
import com.theartofdev.fastimageloader.impl.util.FILUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * TODO:a add doc
 */
public final class DownloaderImpl implements com.theartofdev.fastimageloader.Downloader {

    //region: Fields and Consts

    /**
     * The HTTP client used to execute download image requests
     */
    private final HttpClient mClient;

    /**
     * Used to post execution to main thread.
     */
    private final Handler mHandler;

    /**
     * Used to load images from the disk.
     */
    private final Decoder mDecoder;

    /**
     * Threads service for download operations.
     */
    private final ThreadPoolExecutor mExecutor;

    /**
     * Threads service for pre-fetch download operations.
     */
    private final ThreadPoolExecutor mPrefetchExecutor;

    /**
     * the buffers used to download image
     */
    private final byte[][] mBuffers = new byte[4][];

    private MemoryPool mMemoryPool;
    //endregion

    /**
     * @param context
     * @param client the OkHttp client to use to download the images.
     * @param decoder Handler for loading image bitmap object from file on disk.
     */
    public DownloaderImpl(Context context, HttpClient client, MemoryPool memoryPool, Decoder decoder) {
        FILUtils.notNull(client, "client");
        FILUtils.notNull(memoryPool, "memoryPool");
        FILUtils.notNull(decoder, "imageLoader");

        mClient = client;
        mMemoryPool = memoryPool;
        mDecoder = decoder;

        mHandler = new Handler(context.getMainLooper());

        mExecutor = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), FILUtils.threadFactory("ImageDownloader", true));
        mExecutor.allowCoreThreadTimeOut(true);

        mPrefetchExecutor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), FILUtils.threadFactory("ImagePrefetchDownloader", true));
        mPrefetchExecutor.allowCoreThreadTimeOut(true);
    }

    @Override
    public void downloadAsync(final ImageRequest imageRequest, final boolean prefetch, final Callback callback) {
        Executor executor = prefetch ? mPrefetchExecutor : mExecutor;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                // mark start download, the first to do this will win (sync between prefetch and load)
                if ((prefetch || !imageRequest.isPrefetch()) && imageRequest.startDownload()) {
                    FILLogger.debug("Start image request download... [{}]", imageRequest);
                    final boolean canceled = download(imageRequest);
                    final boolean downloaded = imageRequest.getFileSize() > 0;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.loadImageDownloaderCallback(imageRequest, downloaded, canceled);
                        }
                    });
                } else {
                    FILLogger.debug("Image request download already handled [{}]", imageRequest);
                }
            }
        });
    }

    //region: Private methods

    /**
     * Download
     *
     * @return 0: downloaded, canceled - false, 10: downloaded
     */
    private boolean download(final ImageRequest imageRequest) {
        int responseCode = 0;
        Exception error = null;
        boolean canceled = false;
        boolean downloaded = false;
        long start = System.currentTimeMillis();
        try {
            canceled = !imageRequest.isValid();
            if (!canceled) {

                // start image download request
                HttpClient.HttpResponse httpResponse = mClient.execute(imageRequest.getEnhancedUri());

                // check handshake
                responseCode = httpResponse.getCode();
                if (responseCode < 300) {
                    canceled = !imageRequest.isValid();
                    if (!canceled) {

                        // download data
                        downloaded = download(imageRequest, httpResponse);
                        canceled = !imageRequest.isValid();
                    }
                } else {
                    error = new ConnectException(httpResponse.getCode() + ": " + httpResponse.getErrorMessage());
                    FILLogger.error("Failed to download image... [{}] [{}] [{}]", httpResponse.getCode(), httpResponse.getErrorMessage(), imageRequest);
                }
            }
        } catch (Exception e) {
            error = e;
            FILLogger.error("Failed to download image [{}]", e, imageRequest);
        }

        if (downloaded || error != null) {
            FILLogger.operation(imageRequest.getEnhancedUri(), imageRequest.getSpec().getKey(), responseCode, System.currentTimeMillis() - start, imageRequest.getFileSize(), error);
        }

        // if downloaded and request is still valid - load the image object
        if (downloaded) {
            canceled = !imageRequest.isValid();
            if (!canceled) {
                canceled = false;
                if (!imageRequest.isPrefetch()) {
                    mDecoder.decode(mMemoryPool, imageRequest, imageRequest.getFile(), imageRequest.getSpec());
                }
            }
        }

        return canceled;
    }

    /**
     * Download image data from the given web response.<br>
     * Download to temp file so if error occurred it won't result in corrupted cached file and handle
     * smart cancelling, if request is no longer valid but more than 50% has been downloaded, finish it but
     * don't load the image object.
     *
     * @return true - download successful, false - otherwise.
     */
    private boolean download(ImageRequest imageRequest, HttpClient.HttpResponse response) throws IOException {

        byte[] buffer = null;
        InputStream in = null;
        OutputStream out = null;
        File tmpFile = new File(imageRequest.getFile().getAbsolutePath() + "_tmp");
        try {
            in = response.getBodyStream();
            out = new FileOutputStream(tmpFile);

            int len = 0;
            int size = 0;
            buffer = getBuffer();

            // don't cancel download if passed 50%
            long contentLength = response.getContentLength();
            while ((contentLength < 0 || contentLength * .5f < size || imageRequest.isValid()) && (len = in.read(buffer)) != -1) {
                size += len;
                out.write(buffer, 0, len);
            }

            // if we finished download
            if (len == -1) {
                if (tmpFile.renameTo(imageRequest.getFile())) {
                    imageRequest.setFileSize(size);
                    return true;
                } else {
                    FILLogger.warn("Failed to rename temp download file to target file");
                }
            }
        } finally {
            FILUtils.closeSafe(out);
            FILUtils.closeSafe(in);
            FILUtils.deleteSafe(tmpFile);
            returnBuffer(buffer);
        }
        return false;
    }

    /**
     * Get buffer to be used for image download, use recycled if possible.
     */
    private byte[] getBuffer() {
        byte[] buffer = null;
        synchronized (mBuffers) {
            for (int i = 0; i < mBuffers.length; i++) {
                if (mBuffers[i] != null) {
                    buffer = mBuffers[i];
                    mBuffers[i] = null;
                    break;
                }
            }
        }
        if (buffer == null) {
            buffer = new byte[2048];
        }
        return buffer;
    }

    /**
     * Return buffer to be used for image download to recycled collection.
     */
    private void returnBuffer(byte[] buffer) {
        if (buffer != null) {
            synchronized (mBuffers) {
                for (int i = 0; i < mBuffers.length; i++) {
                    if (mBuffers[i] == null) {
                        mBuffers[i] = buffer;
                        break;
                    }
                }
            }
        }
    }
    //endregion

    //region: Inner class: Callback

    /**
     * Callback for getting cached image.
     */
    public static interface Callback {

        /**
         * Callback for getting cached image, if not cached will have null.
         */
        public void loadImageDownloaderCallback(ImageRequest imageRequest, boolean downloaded, boolean canceled);
    }
    //endregion
}