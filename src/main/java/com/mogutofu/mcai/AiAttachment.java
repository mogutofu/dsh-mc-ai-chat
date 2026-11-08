package com.mogutofu.mcai;

/**
 * 一个已经收齐的附件，准备塞进发给 DSH 的 attachments 字段。
 */
public final class AiAttachment {

    /** 是否作为图片附件（DSH kind=image）；否则 kind=file。 */
    public final boolean image;
    public final String name;
    public final String mediaType;
    public final byte[] data;

    public AiAttachment(boolean image, String name, String mediaType, byte[] data) {
        this.image = image;
        this.name = name;
        this.mediaType = mediaType;
        this.data = data == null ? new byte[0] : data;
    }
}
