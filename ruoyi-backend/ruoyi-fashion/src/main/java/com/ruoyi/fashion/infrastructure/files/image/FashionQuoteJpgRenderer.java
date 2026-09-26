package com.ruoyi.fashion.infrastructure.files.image;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.delivery.DeliveryQuoteSnapshot;
import com.ruoyi.fashion.application.delivery.GeneratedDeliveryArtifact;
import com.ruoyi.fashion.application.delivery.port.FashionDeliveryRenderer;
import com.ruoyi.fashion.infrastructure.files.DeliveryFileNames;
import com.ruoyi.fashion.infrastructure.files.FashionDeliveryMedia;
import org.springframework.stereotype.Component;

@FashionModuleEnabled
@Component
public final class FashionQuoteJpgRenderer implements FashionDeliveryRenderer {
    private final FashionDeliveryMedia media;

    public FashionQuoteJpgRenderer(FashionDeliveryMedia media) {
        this.media = media;
    }

    @Override
    public String fileType() { return "jpg"; }

    @Override
    public List<GeneratedDeliveryArtifact> render(DeliveryQuoteSnapshot quote) {
        List<GeneratedDeliveryArtifact> result = new ArrayList<>();
        for (DeliveryQuoteSnapshot.Combo combo : quote.combos()) {
            Source source = source(combo);
            if (source == null) continue;
            byte[] content = toJpeg(media.readVerified(source.key(), source.sha256()));
            String name = DeliveryFileNames.base(quote) + "_" + DeliveryFileNames.safe(combo.comboNo(), 24)
                    + "_" + source.role() + ".jpg";
            result.add(new GeneratedDeliveryArtifact(name, "image/jpeg", content, null, source.role(),
                    combo.lines().stream().map(DeliveryQuoteSnapshot.Line::skuCode).toList(),
                    source.sourceMode(), source.reviewStatus()));
        }
        if (result.isEmpty()) throw new ServiceException("报价没有可导出的 JPG 图片");
        return List.copyOf(result);
    }

    private static Source source(DeliveryQuoteSnapshot.Combo combo) {
        if (combo.adoptedImage() != null) {
            return new Source(combo.adoptedImage().objectKey(), combo.adoptedImage().sha256(), "采用图",
                    combo.adoptedImage().sourceMode(), combo.adoptedImage().reviewStatus());
        }
        return combo.lines().stream().filter(line -> line.imageKey() != null).findFirst()
                .map(line -> new Source(line.imageKey(), line.imageHash(), "原图",
                        "product-original", "confirmed")).orElse(null);
    }

    private static byte[] toJpeg(byte[] source) {
        try {
            BufferedImage input = ImageIO.read(new ByteArrayInputStream(source));
            if (input == null || input.getWidth() < 1 || input.getHeight() < 1) {
                throw new ServiceException("交付图片无法解码");
            }
            BufferedImage rgb = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                graphics.drawImage(input, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(rgb, "jpg", output) || output.size() == 0) {
                throw new ServiceException("JPG 编码器不可用");
            }
            return output.toByteArray();
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("JPG 生成失败");
        }
    }

    private record Source(String key, String sha256, String role, String sourceMode, String reviewStatus) {
    }
}
