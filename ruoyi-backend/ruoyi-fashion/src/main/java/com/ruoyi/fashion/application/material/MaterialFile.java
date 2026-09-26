package com.ruoyi.fashion.application.material;

public record MaterialFile(String filename, byte[] content) {
    public MaterialFile {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
