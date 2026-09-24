package com.yche.springmind.storage.service;

import com.yche.springmind.common.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 在对象存储未配置时提供显式失败实现，避免应用以不完整能力静默运行。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
@ConditionalOnExpression(
        "'${storage.minio.endpoint:}' == '' "
                + "or '${storage.minio.access-key:}' == '' "
                + "or '${storage.minio.secret-key:}' == ''"
)
public class MissingObjectStorageService implements ObjectStorageService {

    private final String bucket;

    /**
     * 创建并初始化 {@link MissingObjectStorageService}，保存该组件运行所需的依赖与配置。
     *
     * @param environment Spring 运行环境与配置来源
     */
    public MissingObjectStorageService(Environment environment) {
        this.bucket = environment.getProperty("storage.minio.bucket", "springmind-documents");
    }

    /**
     * 返回 {@code defaultBucket} 对应的配置或状态值。
     *
     * @return 处理后得到的字符串结果
     */
    @Override
    public String getDefaultBucket() {
        return bucket;
    }

    /**
     * 返回 {@code object} 对应的配置或状态值。
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @return 可读取目标内容的输入流，调用方负责在使用后关闭
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Override
    public InputStream getObject(String bucket, String objectKey) {
        throw new BusinessException("对象存储未配置");
    }

    /**
     * 执行 {@code putObject} 对应的业务步骤。
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @param inputStream 待读取或上传的数据流
     * @param objectSize 方法参数 {@code objectSize}
     * @param contentType 文件的 MIME 类型
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Override
    public void putObject(String bucket, String objectKey, InputStream inputStream, long objectSize, String contentType) {
        throw new BusinessException("对象存储未配置");
    }

    /**
     * 执行 {@code composeObject} 对应的业务步骤。
     *
     * @param bucket 对象存储桶名称
     * @param targetObjectKey 方法参数 {@code targetObjectKey}
     * @param sourceObjectKeys 方法参数 {@code sourceObjectKeys}
     * @param contentType 文件的 MIME 类型
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Override
    public void composeObject(String bucket, String targetObjectKey, java.util.List<String> sourceObjectKeys, String contentType) {
        throw new BusinessException("对象存储未配置");
    }

    /**
     * 执行 {@code deleteObject} 对应的业务步骤。
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Override
    public void deleteObject(String bucket, String objectKey) {
        throw new BusinessException("对象存储未配置");
    }
}
