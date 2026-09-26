package com.ruoyi.aden.application.collection;

import com.ruoyi.aden.infrastructure.storage.AdenCollectionFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class AdenCollectionFilesTest {
    @TempDir Path directory;
    @Test void validatesBytesAndKeepsOnlyInternalPaths() throws Exception {
        var files=new AdenCollectionFiles(directory.toString()); String ws=UUID.randomUUID().toString(), upload=UUID.randomUUID().toString();
        byte[] png;
        try(var out=new ByteArrayOutputStream()) { ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out); png=out.toByteArray(); }
        files.chunk(ws,upload,0,png);
        assertTrue(files.matches(ws,upload,0,png));
        assertThrows(IllegalArgumentException.class,()->files.finish(ws,upload,AdenCollectionFiles.sha256(png),"image/jpeg",png.length));
        assertThrows(IllegalArgumentException.class,()->files.finish(ws,upload,"0".repeat(64),"image/png",png.length));
        files.finish(ws,upload,AdenCollectionFiles.sha256(png),"image/png",png.length);
        assertArrayEquals(png,files.read(ws,AdenCollectionFiles.sha256(png)+".asset"));
        assertThrows(IllegalArgumentException.class,()->files.path(ws,"../../escape"));
        assertThrows(IllegalArgumentException.class,()->AdenCollectionFiles.validateImage("<svg onload='attack'/>".getBytes(),"image/png"));
    }
    @Test void rejectsLossyIntegersAndInvalidPrices() {
        assertThrows(IllegalArgumentException.class,()->AdenCollectionService.number(1.5));
        assertThrows(IllegalArgumentException.class,()->AdenCollectionService.validatePrice("-1"));
        assertThrows(IllegalArgumentException.class,()->AdenCollectionService.validatePrice("NaN"));
        assertDoesNotThrow(()->AdenCollectionService.validatePrice("19.99"));
    }
    @Test void decodesRealJpegAndWebpPreservesSourceAndCreatesBoundedThumbnail() throws Exception {
        var files=new AdenCollectionFiles(directory.toString()); String ws=UUID.randomUUID().toString();
        byte[] jpeg;
        try(var out=new ByteArrayOutputStream()) { ImageIO.write(new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB),"jpeg",out);jpeg=out.toByteArray(); }
        byte[] webp=java.util.Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAUAAAAdQro40sv+BiOh/AAA=");
        for(var entry:java.util.Map.of("image/jpeg",jpeg,"image/webp",webp).entrySet()) {
            String upload=UUID.randomUUID().toString(),hash=AdenCollectionFiles.sha256(entry.getValue());
            files.chunk(ws,upload,0,entry.getValue()); var info=files.finish(ws,upload,hash,entry.getKey(),entry.getValue().length);
            assertTrue(info.width()>0 && info.height()>0);
            assertArrayEquals(entry.getValue(),files.read(ws,hash+".asset"));
            var thumb=ImageIO.read(new java.io.ByteArrayInputStream(files.read(ws,hash+".thumb.png")));
            assertTrue(thumb.getWidth()<=320 && thumb.getHeight()<=320);
            assertThrows(IllegalArgumentException.class,()->AdenCollectionFiles.validateImage(java.util.Arrays.copyOf(entry.getValue(),entry.getValue().length-3),entry.getKey()));
        }
        byte[] oversized=webp.clone(); // VP8L: 16384 x 16384, 必须在解码像素前拒绝。
        oversized[21]=(byte)255; oversized[22]=(byte)255; oversized[23]=(byte)255; oversized[24]=(byte)15;
        assertThrows(IllegalArgumentException.class,()->AdenCollectionFiles.validateImage(oversized,"image/webp"));
    }
}
