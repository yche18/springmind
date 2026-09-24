package com.yche.springmind.document.controller;

import com.yche.springmind.common.api.ApiResponse;
import com.yche.springmind.document.model.dto.DocumentQuery;
import com.yche.springmind.document.model.dto.UploadDocumentRequest;
import com.yche.springmind.document.model.dto.UploadInitRequest;
import com.yche.springmind.document.model.dto.UploadChunkRequest;
import com.yche.springmind.document.model.vo.DocumentListItemVO;
import com.yche.springmind.document.model.vo.DocumentPreviewVO;
import com.yche.springmind.document.model.vo.UploadInitResponse;
import com.yche.springmind.document.model.vo.UploadStatusResponse;
import com.yche.springmind.document.service.DocumentService;
import com.yche.springmind.document.service.DocumentUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供文档上传、分片续传、列表、预览、状态查询和删除等 HTTP 接口。
 *
 * <p>位于接口层：负责接收 HTTP 请求、触发参数校验并调用业务服务；业务规则由 Service 层统一维护。</p>
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentUploadService documentUploadService;

    /**
     * 创建并初始化 {@link DocumentController}，保存该组件运行所需的依赖与配置。
     *
     * @param documentService 方法参数 {@code documentService}
     * @param documentUploadService 方法参数 {@code documentUploadService}
     */
    public DocumentController(DocumentService documentService, DocumentUploadService documentUploadService) {
        this.documentService = documentService;
        this.documentUploadService = documentUploadService;
    }

    /**
     * 初始化分片上传会话，计算分片数量并返回客户端续传所需的信息。
     *
     * @param uploadRequest 上传请求参数
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;UploadInitResponse&gt;} 表示
     */
    @PostMapping(path = "/upload/init", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<UploadInitResponse> initUpload(
            @RequestBody UploadInitRequest uploadRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(documentUploadService.initUpload(request, uploadRequest));
    }

    /**
     * 校验上传会话归属和分片范围后，将单个分片写入对象存储并记录状态。
     *
     * @param uploadRequest 上传请求参数
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;UploadStatusResponse&gt;} 表示
     */
    @PostMapping(path = "/upload/chunks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadStatusResponse> uploadChunk(
            @ModelAttribute UploadChunkRequest uploadRequest,
            HttpServletRequest request
    ) {
        documentUploadService.uploadChunk(request, uploadRequest);
        return ApiResponse.success(documentUploadService.getUploadStatus(request, uploadRequest.uploadId()));
    }

    /**
     * 返回 {@code uploadStatus} 对应的配置或状态值。
     *
     * @param uploadId 分片上传任务唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 查询得到的上传状态结果
     */
    @GetMapping("/upload/{uploadId}")
    public ApiResponse<UploadStatusResponse> getUploadStatus(
            @PathVariable String uploadId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(documentUploadService.getUploadStatus(request, uploadId));
    }

    /**
     * 校验所有分片齐备后合并对象，创建文档记录并触发异步入库。
     *
     * @param uploadId 分片上传任务唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Long&gt;} 表示
     */
    @PostMapping("/upload/{uploadId}/complete")
    public ApiResponse<Long> completeUpload(
            @PathVariable String uploadId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(documentUploadService.completeUpload(request, uploadId));
    }

    /**
     * 接收并保存文档原文件与元数据，随后发布异步 ETL 入库事件。
     *
     * @param uploadRequest 上传请求参数
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Long&gt;} 表示
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> uploadDocument(
            @ModelAttribute UploadDocumentRequest uploadRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(documentService.uploadDocument(request, uploadRequest));
    }

    /**
     * 处理 {@code listDocuments} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param query 用于检索或筛选的查询条件
     * @param request 已经通过控制器基础校验的请求对象
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    @GetMapping
    public List<DocumentListItemVO> listDocuments(
            @ModelAttribute DocumentQuery query,
            HttpServletRequest request
    ) {
        return documentService.listDocuments(request, query);
    }

    /**
     * 校验删除权限后软删除文档，并清理对象存储及检索索引中的关联数据。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(
            @PathVariable Long documentId,
            @RequestParam Long groupId,
            HttpServletRequest request
    ) {
        documentService.softDeleteDocument(request, groupId, documentId);
        return ApiResponse.success(null);
    }

    /**
     * 处理 {@code retryDocumentIngestion} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @PostMapping("/{documentId}/retry-ingestion")
    public ApiResponse<Void> retryDocumentIngestion(
            @PathVariable Long documentId,
            @RequestParam Long groupId,
            HttpServletRequest request
    ) {
        documentService.retryFailedDocumentIngestion(request, groupId, documentId);
        return ApiResponse.success(null);
    }

    /**
     * 处理 {@code previewDocument} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param documentId 文档唯一标识
     * @param groupId 群组唯一标识
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code DocumentPreviewVO} 表示
     */
    @GetMapping("/{documentId}/preview")
    public DocumentPreviewVO previewDocument(
            @PathVariable Long documentId,
            @RequestParam Long groupId,
            HttpServletRequest request
    ) {
        return documentService.previewDocument(request, groupId, documentId);
    }
}
