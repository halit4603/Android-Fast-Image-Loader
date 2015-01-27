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

package com.theartofdev.fastimageloader;

import android.graphics.Bitmap;

/**
 * Builder for creating {@link com.theartofdev.fastimageloader.ImageLoadSpec} instances.
 * <p/>
 * Defaults:<br/>
 * Format - JPEG<br/>
 * Max Density - 1.5<br/>
 * Pixel Config - ARGB_8888<br/>
 */
public final class ImageLoadSpecBuilder {

    //region: Fields and Consts

    /**
     * the width of the image in pixels
     */
    private int mWidth = -1;

    /**
     * the height of the image in pixels
     */
    private int mHeight = -1;

    /**
     * the max pixel per inch density to load the image in
     */
    private float mMaxDensity = 1.5f;

    /**
     * The format of the image.
     */
    private ImageLoadSpec.Format mFormat = ImageLoadSpec.Format.JPEG;

    /**
     * the pixel configuration to load the image in (4 bytes per image pixel, 2 bytes, etc.)
     */
    private Bitmap.Config mPixelConfig = Bitmap.Config.ARGB_8888;
    //endregion

    /**
     * The format of the image to download.
     */
    public ImageLoadSpecBuilder setFormat(ImageLoadSpec.Format format) {
        mFormat = format;
        return this;
    }

    /**
     * the pixel configuration to load the image in (4 bytes per image pixel, 2 bytes, etc.)
     */
    public ImageLoadSpecBuilder setPixelConfig(Bitmap.Config pixelConfig) {
        mPixelConfig = pixelConfig;
        return this;
    }

    /**
     * the width and height of the image in pixels to the size of the screen.
     */
    public ImageLoadSpecBuilder setDimensionByDisplay() {
        mWidth = Utils.displaySize.x;
        mHeight = Utils.displaySize.y;
        return this;
    }

    /**
     * the width and height of the image to unbound, will be the size of the downloaded image.
     */
    public ImageLoadSpecBuilder setUnboundDimension() {
        mWidth = 0;
        mHeight = 0;
        return this;
    }

    /**
     * the width and height of the image in pixels to the same value (square).
     */
    public ImageLoadSpecBuilder setDimension(int size) {
        mWidth = size;
        mHeight = size;
        return this;
    }

    /**
     * the width and height of the image in pixels.
     */
    public ImageLoadSpecBuilder setDimension(int width, int height) {
        mWidth = width;
        mHeight = height;
        return this;
    }

    /**
     * the width of the image in pixels.
     */
    public ImageLoadSpecBuilder setWidth(int width) {
        mWidth = width;
        return this;
    }

    /**
     * the height of the image in pixels.
     */
    public ImageLoadSpecBuilder setHeight(int height) {
        mHeight = height;
        return this;
    }

    /**
     * the width and height of the image in pixels to the same value (square).
     */
    public ImageLoadSpecBuilder setDimensionByResource(int resId) {
        mWidth = mHeight = Utils.application.getResources().getDimensionPixelSize(resId);
        return this;
    }

    /**
     * the width and height of the image in pixels.
     */
    public ImageLoadSpecBuilder setDimensionByResource(int widthResId, int heightResId) {
        mWidth = Utils.application.getResources().getDimensionPixelSize(widthResId);
        mHeight = Utils.application.getResources().getDimensionPixelSize(heightResId);
        return this;
    }

    /**
     * the width of the image by reading dimension resource by the given key.
     */
    public ImageLoadSpecBuilder setWidthByResource(int resId) {
        mWidth = Utils.application.getResources().getDimensionPixelSize(resId);
        return this;
    }

    /**
     * the height of the image by reading dimension resource by the given key.
     */
    public ImageLoadSpecBuilder setHeightByResource(int resId) {
        mHeight = Utils.application.getResources().getDimensionPixelSize(resId);
        return this;
    }

    /**
     * the max pixel per inch density to load the image in
     */
    public ImageLoadSpecBuilder setMaxDensity(float maxDensity) {
        if (maxDensity <= 0.5)
            throw new IllegalArgumentException("max density must be > .5");
        mMaxDensity = maxDensity;
        return this;
    }

    /**
     * Create spec by set parameters.
     */
    public ImageLoadSpec build() {
        if (mWidth < 0 || mHeight < 0)
            throw new IllegalArgumentException("width and height must be set");
        if ((mWidth == 0 && mHeight > 0) || (mHeight == 0 && mWidth > 0))
            throw new IllegalArgumentException("width and height must be either unbound or both positive");

        float densityAdj = Utils.density > mMaxDensity ? mMaxDensity / Utils.density : 1f;
        return new ImageLoadSpec((int) (mWidth * densityAdj), (int) (mHeight * densityAdj), mFormat, mPixelConfig);
    }
}