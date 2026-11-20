package com.mogutofu.mcai.core;

/** 一个待发送的附件（截图或本地文件）。最多 1 个。 */
public final class Attachment {

    public final String name;
    public final String mediaType;
    public final boolean image;
    public final byte[] data;

    public Attachment(String name, String mediaType, boolean image, byte[] data) {
        this.name = name == null || name.isEmpty() ? "attachment.bin" : name;
        this.mediaType = mediaType == null || mediaType.isEmpty() ? "application/octet-stream" : mediaType;
        this.image = image;
        this.data = data == null ? new byte[0] : data;
    }
}
