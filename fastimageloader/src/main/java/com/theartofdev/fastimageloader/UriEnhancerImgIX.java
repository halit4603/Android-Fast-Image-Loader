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

/**
 * URL enhancer for imgIX (http://www.imgix.com) service.<br/>
 * Add image load specification as query params to the image URL.
 */
public class UriEnhancerImgIX implements UriEnhancer {

    @Override
    public String enhance(String url, ImageLoadSpec spec) {
        StringBuilder sb = new StringBuilder(url);

        int qIdx = url.indexOf('?');
        sb.append(qIdx > -1 ? '&' : '?');

        if (spec.getFormat() == ImageLoadSpec.Format.JPEG)
            sb.append("auto=jpeg&");
        else if (spec.getFormat() == ImageLoadSpec.Format.PNG)
            sb.append("auto=png&");
        else if (spec.getFormat() == ImageLoadSpec.Format.WEBP)
            sb.append("auto=webp&");

        sb.append("fit=crop&");

        sb.append("w=").append(spec.getWidth()).append("&");
        sb.append("h=").append(spec.getHeight());
        return sb.toString();
    }
}