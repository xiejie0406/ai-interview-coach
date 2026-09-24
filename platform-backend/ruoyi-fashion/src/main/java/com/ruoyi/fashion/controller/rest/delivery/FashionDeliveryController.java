package com.ruoyi.fashion.controller.rest.delivery;

import java.nio.charset.StandardCharsets;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.fashion.application.delivery.DeliveryDownload;
import com.ruoyi.fashion.application.delivery.FashionDeliveryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/fashion/quotes/{quoteId}/files")
public class FashionDeliveryController extends BaseController {
    private final FashionDeliveryService service;

    public FashionDeliveryController(FashionDeliveryService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:query')")
    @GetMapping
    public AjaxResult workspace(@PathVariable String quoteId) {
        return success(service.workspace(quoteId));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:export')")
    @Log(title = "智能选品交付文件生成", businessType = BusinessType.EXPORT, isSaveRequestData = false)
    @PostMapping
    public AjaxResult request(@PathVariable String quoteId, @RequestBody DeliveryFileRequest request) {
        return success(service.request(quoteId, request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:export')")
    @Log(title = "智能选品交付文件重试", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/{fileId}/retry")
    public AjaxResult retry(@PathVariable String quoteId, @PathVariable String fileId,
            @RequestParam long rowVersion) {
        return success(service.retry(quoteId, fileId, rowVersion, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:export')")
    @Log(title = "智能选品交付文件下载", businessType = BusinessType.EXPORT, isSaveRequestData = false)
    @GetMapping("/{fileId}/artifacts/{artifactNo}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String quoteId,
            @PathVariable String fileId, @PathVariable int artifactNo) {
        DeliveryDownload download = service.download(quoteId, fileId, artifactNo);
        long operatorId = getUserId();
        StreamingResponseBody body = output -> {
            output.write(download.content());
            output.flush();
            service.markDownloaded(download, operatorId);
        };
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.artifact().fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(download.artifact().contentType()))
                .contentLength(download.content().length).body(body);
    }

    @PreAuthorize("@ss.hasPermi('fashion:operations:retention')")
    @Log(title = "智能选品交付保留期延长", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/{fileId}/retention")
    public AjaxResult extendRetention(@PathVariable String quoteId, @PathVariable String fileId,
            @RequestBody RetentionExtensionRequest request) {
        return success(service.extendRetention(quoteId, fileId, request.rowVersion(),
                request.retainUntil(), getUserId()));
    }
}
