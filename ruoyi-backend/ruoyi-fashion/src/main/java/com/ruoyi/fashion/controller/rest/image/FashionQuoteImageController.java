package com.ruoyi.fashion.controller.rest.image;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.io.IOException;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.image.FashionQuoteImageService;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@FashionModuleEnabled
@RestController
@RequestMapping("/fashion/quotes/{quoteId}/images")
public class FashionQuoteImageController extends BaseController {
    private final FashionQuoteImageService service;

    public FashionQuoteImageController(FashionQuoteImageService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('fashion:image:list')")
    @GetMapping
    public AjaxResult workspace(@PathVariable String quoteId) {
        return success(service.workspace(quoteId));
    }

    @PreAuthorize("@ss.hasPermi('fashion:image:create')")
    @Log(title = "智能选品图片任务创建", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    public AjaxResult create(@PathVariable String quoteId, @RequestBody QuoteImageCreateRequest request) {
        return success(service.create(quoteId, request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:image:create')")
    @Log(title = "智能选品外部图片上传", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult upload(@PathVariable String quoteId,
            @RequestPart("file") MultipartFile file,
            @RequestParam String comboId,
            @RequestParam String imageType,
            @RequestParam String requestKey,
            @RequestParam long quoteRowVersion,
            @RequestParam long comboRowVersion,
            @RequestParam String comboVisualHash) {
        try {
            return success(service.upload(quoteId, comboId, imageType, requestKey, quoteRowVersion,
                    comboRowVersion, comboVisualHash, file.getOriginalFilename(), file.getBytes(), getUserId()));
        } catch (IOException exception) {
            throw new ServiceException("读取上传图片失败");
        }
    }

    @PreAuthorize("@ss.hasPermi('fashion:image:create')")
    @Log(title = "智能选品图片任务取消", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/{imageId}/cancel")
    public AjaxResult cancel(@PathVariable String quoteId, @PathVariable String imageId,
            @RequestParam long rowVersion) {
        return success(service.cancel(quoteId, imageId, rowVersion, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:image:review')")
    @Log(title = "智能选品图片复核", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/{imageId}/reviews")
    public AjaxResult review(@PathVariable String quoteId, @PathVariable String imageId,
            @RequestBody QuoteImageReviewRequest request) {
        return success(service.review(quoteId, imageId, request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:image:review')")
    @Log(title = "智能选品图片采用", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/{imageId}/results/{resultNo}/adopt")
    public AjaxResult adopt(@PathVariable String quoteId, @PathVariable String imageId,
            @PathVariable int resultNo, @RequestParam long rowVersion) {
        return success(service.adopt(quoteId, imageId, resultNo, rowVersion, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:image:list')")
    @GetMapping("/combinations/{comboId}/slots/{slotCode}/original")
    public ResponseEntity<byte[]> original(@PathVariable String quoteId, @PathVariable String comboId,
            @PathVariable String slotCode) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.originalContent(quoteId, comboId, slotCode));
    }

    @PreAuthorize("@ss.hasPermi('fashion:image:list')")
    @GetMapping("/{imageId}/results/{resultNo}/content")
    public ResponseEntity<byte[]> content(@PathVariable String quoteId, @PathVariable String imageId,
            @PathVariable int resultNo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.resultContent(quoteId, imageId, resultNo));
    }
}
