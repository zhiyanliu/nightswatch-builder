package com.amazonaws.rp.nightswatch.builder.utils;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import org.slf4j.Logger;

import java.io.File;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;

public class S3 {
    public String getObjectPreSignedUrl(final Logger log, final String bucketName,
                                        final String objectName, final int expiredDays) {
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

        Calendar c = Calendar.getInstance();
        c.setTime(new Date());  // now
        c.add(Calendar.DATE, expiredDays);

        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucketName, objectName);
        req.setExpiration(c.getTime());
        URL preSignedURL = s3Client.generatePresignedUrl(req);

        return preSignedURL.toString();
    }

    public String uploadFile(final Logger log, final String bucketName, final String filePath) {
        File file = new File(filePath);

        try {
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

            log.debug("connected to AWS S3 service");

            PutObjectRequest req = new PutObjectRequest(bucketName, file.getName(), file);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("application/octet-stream");

            log.debug(String.format("uploading file %s to S3 bucket %s...", file.getName(), bucketName));

            s3Client.putObject(req);

            log.info(String.format("file %s has been uploaded to the bucket %s", file.getName(), bucketName));

            return String.format("https://s3.amazonaws.com/%s/%s", bucketName, file.getName());
        } catch (SdkClientException e) {
            e.printStackTrace();
            log.error(String.format("failed to upload file %s to S3 bucket %s", file.getName(), bucketName));
            throw e;
        }
    }

    public void emptyBucket(final Logger log, String bucketName) throws AmazonServiceException {
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

        log.debug("connected to AWS S3 service");

        ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName).withMaxKeys(10);
        ListObjectsV2Result result;

        try {
            do {
                result = s3Client.listObjectsV2(req);

                for (S3ObjectSummary objSummary : result.getObjectSummaries()) {
                    log.debug(String.format(
                            "deleting file %s from the bucket %s ...", objSummary.getKey(), bucketName));
                    s3Client.deleteObject(bucketName, objSummary.getKey());
                }

                // If there are more than maxKeys keys in the bucket, get a continuation token
                // and list the next objects.
                String token = result.getNextContinuationToken();
                req.setContinuationToken(token);
            } while (result.isTruncated());
        } catch (AmazonServiceException e) {
            if (!e.getMessage().contains("does not exist"))
                throw e;
        }
    }
}
