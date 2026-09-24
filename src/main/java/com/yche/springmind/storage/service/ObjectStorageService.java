package com.yche.springmind.storage.service;

import java.io.InputStream;

/**
 * 定义原始文档上传、读取、合并和删除所需的对象存储契约。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
public interface ObjectStorageService {

    /**
     * 返回 {@code defaultBucket} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    String getDefaultBucket();

    /**
     * 返回 {@code object} 对应的配置或状态值。
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @return 可读取目标内容的输入流，调用方负责在使用后关闭
     */
    InputStream getObject(String bucket, String objectKey);

    /**
     * 执行 {@code putObject} 对应的业务步骤。
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @param inputStream 待读取或上传的数据流
     * @param objectSize 方法参数 {@code objectSize}
     * @param contentType 文件的 MIME 类型
     */
    void putObject(String bucket, String objectKey, InputStream inputStream, long objectSize, String contentType);

    /**
     * 执行 {@code composeObject} 对应的业务步骤。
     *
     * @param bucket 对象存储桶名称
     * @param targetObjectKey 方法参数 {@code targetObjectKey}
     * @param sourceObjectKeys 方法参数 {@code sourceObjectKeys}
     * @param contentType 文件的 MIME 类型
     */
    void composeObject(String bucket, String targetObjectKey, java.util.List<String> sourceObjectKeys, String contentType);

    /**
     * 执行 {@code deleteObject} 对应的业务步骤。
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     */
    void deleteObject(String bucket, String objectKey);
}
