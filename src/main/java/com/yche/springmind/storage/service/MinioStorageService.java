package com.yche.springmind.storage.service;

import com.yche.springmind.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 MinIO 实现对象存储契约，处理普通对象和分片上传临时对象。
 *
 * <p>位于业务服务层：编排领域操作和基础设施调用，并集中维护事务、权限校验及失败处理边界。</p>
 */
@Service
@ConditionalOnProperty(prefix = "storage.minio", name = {"endpoint", "access-key", "secret-key"})
public class MinioStorageService implements ObjectStorageService {

    private static final long UNKNOWN_STREAM_SIZE = -1L;
    private final MinioClient minioClient;
    private final String bucket;
    private final Object bucketLock = new Object();
    private final Set<String> readyBuckets = ConcurrentHashMap.newKeySet();

    /**
     * 创建并初始化 {@link MinioStorageService}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；读写 MinIO 对象存储中的原始文件。
     *
     * @param environment Spring 运行环境与配置来源
     */
    public MinioStorageService(Environment environment) {
        String endpoint = requiredProperty(environment, "storage.minio.endpoint");
        String accessKey = requiredProperty(environment, "storage.minio.access-key");
        String secretKey = requiredProperty(environment, "storage.minio.secret-key");
        this.bucket = environment.getProperty("storage.minio.bucket", "springmind-documents");
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
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
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException("对象存储读取失败", exception);
        }
    }

    /**
     * 执行 {@code putObject} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；读写 MinIO 对象存储中的原始文件；捕获依赖异常并转换、记录或执行降级策略。
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
        try {
            ensureBucketExists(bucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, objectSize, UNKNOWN_STREAM_SIZE)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException("对象存储上传失败", exception);
        }
    }

    /**
     * 执行 {@code composeObject} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；读写 MinIO 对象存储中的原始文件；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param bucket 对象存储桶名称
     * @param targetObjectKey 方法参数 {@code targetObjectKey}
     * @param sourceObjectKeys 方法参数 {@code sourceObjectKeys}
     * @param contentType 文件的 MIME 类型
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Override
    public void composeObject(String bucket, String targetObjectKey, List<String> sourceObjectKeys, String contentType) {
        try {
            ensureBucketExists(bucket);
            List<ComposeSource> sources = sourceObjectKeys.stream()
                    .map(sourceObjectKey -> ComposeSource.builder()
                            .bucket(bucket)
                            .object(sourceObjectKey)
                            .build())
                    .toList();
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucket)
                            .object(targetObjectKey)
                            .sources(sources)
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException("对象存储合并失败", exception);
        }
    }

    /**
     * 执行 {@code deleteObject} 对应的业务步骤。
     * <p>
     * 实现要点：读写 MinIO 对象存储中的原始文件；捕获依赖异常并转换、记录或执行降级策略。
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储中的对象键
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException("对象存储删除失败", exception);
        }
    }

    /**
     * 执行 {@code ensureBucketExists} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；读写 MinIO 对象存储中的原始文件。
     *
     * @param bucket 对象存储桶名称
     * @throws Exception 当输入、状态或依赖不满足方法约束时抛出
     */
    private void ensureBucketExists(String bucket) throws Exception {
        if (readyBuckets.contains(bucket)) {
            return;
        }
        synchronized (bucketLock) {
            if (readyBuckets.contains(bucket)) {
                return;
            }
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            readyBuckets.add(bucket);
        }
    }

    /**
     * 执行 {@code requiredProperty} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param environment Spring 运行环境与配置来源
     * @param propertyName 方法参数 {@code propertyName}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String requiredProperty(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("对象存储配置缺失: " + propertyName);
        }
        return value;
    }
}
