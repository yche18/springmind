package com.yche.springmind.storage.service;

import java.io.InputStream;

/** Abstract object storage operations used by upload and ingestion workflows. */
public interface ObjectStorageService {

    String getDefaultBucket();

    /** Open an object stream; the caller is responsible for closing it. */
    InputStream getObject(String bucket, String objectKey);

    /** Store exactly {@code objectSize} bytes from the supplied stream. */
    void putObject(String bucket, String objectKey, InputStream inputStream, long objectSize, String contentType);

    /** Compose source objects in list order into a single target object. */
    void composeObject(String bucket, String targetObjectKey, java.util.List<String> sourceObjectKeys, String contentType);

    /** Delete an object without failing when it is already absent. */
    void deleteObject(String bucket, String objectKey);
}
