package com.ruoyi.fashion.controller.rest.material;

import java.time.Instant;
import java.util.List;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.material.FashionProductMaterialService;
import com.ruoyi.fashion.application.material.MaterialFile;
import org.springframework.http.MediaType;
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

@RestController
@RequestMapping("/fashion/materials")
public class FashionProductMaterialController extends BaseController {
    private final FashionProductMaterialService service;
    private final ObjectMapper objectMapper;

    public FashionProductMaterialController(FashionProductMaterialService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("@ss.hasPermi('fashion:product:image')")
    @Log(title = "智能选品图片素材预览", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult preview(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam String sourceCode,
            @RequestParam String asOf,
            @RequestParam String mappings) {
        try {
            List<MaterialFile> uploads = files.stream()
                    .map(file -> {
                        try {
                            return new MaterialFile(file.getOriginalFilename(), file.getBytes());
                        } catch (java.io.IOException exception) {
                            throw new ServiceException("读取上传图片失败");
                        }
                    }).toList();
            List<ImageMappingRequest> requested = objectMapper.readValue(
                    mappings, new TypeReference<List<ImageMappingRequest>>() {});
            return success(service.preview(sourceCode, uploads,
                    requested.stream().map(ImageMappingRequest::toCommand).toList(),
                    parseInstant(asOf), getUserId()));
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("图片映射 JSON 无效");
        }
    }

    @PreAuthorize("@ss.hasPermi('fashion:product:image')")
    @GetMapping("/{batchId}")
    public AjaxResult detail(@PathVariable String batchId) {
        return success(service.get(batchId));
    }

    @PreAuthorize("@ss.hasPermi('fashion:product:image')")
    @Log(title = "智能选品图片素材确认", businessType = BusinessType.IMPORT)
    @PostMapping("/{batchId}/confirm")
    public AjaxResult confirm(@PathVariable String batchId, @RequestBody MaterialConfirmRequest request) {
        return success(service.confirm(batchId, request.rowVersion, getUserId()));
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new ServiceException("asOf 必须是 ISO-8601 UTC 时间");
        }
    }
}
