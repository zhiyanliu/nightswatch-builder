package com.amazonaws.rp.nightswatch.builder;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.*;
import com.amazonaws.services.iot.AWSIot;
import com.amazonaws.services.iot.AWSIotClientBuilder;
import com.amazonaws.services.iot.model.CertificateDescription;
import com.amazonaws.services.iot.model.DescribeCertificateRequest;
import com.amazonaws.services.iot.model.DescribeCertificateResult;
import com.amazonaws.services.iot.model.UpdateCertificateRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AppOTADemoAssert {
    private static Logger log = LoggerFactory.getLogger("nightswatch-app-ota-demo-asset");

    private final static String PUB_KEY_NAME = "nw-app-ota-demo-dev-public";
    private final static String PRV_KEY_NAME = "nw-app-ota-demo-dev-private";
    private final static String ROOT_CA_NAME = "root-ca.crt";

    private final static String RANGER_PKG_FILE_NAME = "nightswatch-ranger.tar.gz";
    private final static String INIT_SCRIPT_FILE_NAME = "init.py";

    public void provision(final String appOTADemoIoTStackName) throws IOException {

        String devFileBucketName = this.queryDeviceFileBucketName(appOTADemoIoTStackName);
        if (devFileBucketName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of s3 bucket to save EC2 instance files not found, " +
                            "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        String certId = this.queryThingCertificateId(appOTADemoIoTStackName);
        if (certId == null)
            throw new IllegalArgumentException(String.format("the thing certificate ID not found, " +
                    "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        // Night's Watch - Ranger stuff
        String zipFilePath = this.prepareCredentials(certId);
        String preSignedCredentialsPackageURL = this.uploadCredentials(devFileBucketName, zipFilePath);
        String preSignedRangerPackageURL = this.uploadNightsWatchRangerPackage(devFileBucketName);
        String scriptFilePath = this.prepareInitScript(preSignedCredentialsPackageURL, preSignedRangerPackageURL);
        String preSignedInitScriptURL = this.uploadInitScript(devFileBucketName, scriptFilePath);

        System.out.println();
        System.out.println("Outputs:");
        System.out.println(String.format("init script file URL (base64): %s",
                new String(Base64.encodeBase64(preSignedInitScriptURL.getBytes()))));
    }

    public void deProvision(final String appOTADemoIoTStackName) {
        String devFileBucketName = this.queryDeviceFileBucketName(appOTADemoIoTStackName);
        if (devFileBucketName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of s3 bucket to save EC2 instance files not found, " +
                            "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        String jobDocBucketName = this.queryJobDocBucketName(appOTADemoIoTStackName);
        if (jobDocBucketName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of s3 bucket to save job document not found, " +
                            "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        String certId = this.queryThingCertificateId(appOTADemoIoTStackName);
        if (certId == null)
            throw new IllegalArgumentException(String.format("the thing certificate ID not found, " +
                    "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        this.deactivateThingCert(certId);

        this.emptyS3Bucket(devFileBucketName);
        log.info(String.format("the device files S3 bucket %s is cleaned up to empty", devFileBucketName));

        this.emptyS3Bucket(jobDocBucketName);
        log.info(String.format("the job docs S3 bucket %s is cleaned up to empty", jobDocBucketName));
    }

    private void generateCredentials(String certId, String certFilePath, String rootCaPath,
                                     String publicKeyPath, String privateKeyPath) throws IOException {

        AWSIot iotClient = AWSIotClientBuilder.defaultClient();

        log.debug("connected to AWS IoT service");

        log.debug(String.format("fetching certificate %s ...", certId));

        DescribeCertificateRequest req = new DescribeCertificateRequest();
        req.setCertificateId(certId);
        DescribeCertificateResult describeCertificateResult = iotClient.describeCertificate(req);
        CertificateDescription certDesc = describeCertificateResult.getCertificateDescription();

        PrintWriter out = new PrintWriter(certFilePath);
        out.print(certDesc.getCertificatePem());
        out.close();

        log.info(String.format("the IoT device certificate %s is downloaded at %s, status: %s",
                certId, certFilePath, certDesc.getStatus()));

        String fileName = String.format("nw-app-ota-demo/%s", ROOT_CA_NAME);
        URL rootCa = getClass().getClassLoader().getResource(fileName);
        if (rootCa == null)
            throw new IllegalArgumentException(
                    String.format("root CA certificate file %s not found", fileName));

        out = new PrintWriter(rootCaPath);
        out.print(new String(rootCa.openStream().readAllBytes()));
        out.close();

        log.info(String.format("the IoT device root CA certificate is generated at %s", rootCaPath));

        fileName = String.format("nw-app-ota-demo/%s.key", PUB_KEY_NAME);
        URL key = getClass().getClassLoader().getResource(fileName);
        if (key == null)
            throw new IllegalArgumentException(String.format("private key file %s not found", fileName));

        out = new PrintWriter(publicKeyPath);
        out.print(new String(key.openStream().readAllBytes()));
        out.close();

        log.info(String.format("the IoT device public key is generated at %s", publicKeyPath));

        fileName = String.format("nw-app-ota-demo/%s.key", PRV_KEY_NAME);
        key = getClass().getClassLoader().getResource(fileName);
        if (key == null)
            throw new IllegalArgumentException(String.format("private key file %s not found", fileName));

        out = new PrintWriter(privateKeyPath);
        out.print(new String(key.openStream().readAllBytes()));
        out.close();

        log.info(String.format("the IoT device private key is generated at %s", privateKeyPath));
    }

    private Map<String, String> getExports(AmazonCloudFormation client, String nextToken) {
        ListExportsResult exportResult = client.listExports(new ListExportsRequest().withNextToken(nextToken));
        Map<String, String> map = new HashMap<>();

        for (Export export : exportResult.getExports())
            map.put(export.getName(), export.getValue());

        if (exportResult.getNextToken() != null)
            map.putAll(this.getExports(client, exportResult.getNextToken()));

        return map;
    }

    private String queryStackOutput(String appOTADemoIoTStackName, String outputKey) {
        AmazonCloudFormation client = AmazonCloudFormationClientBuilder.defaultClient();

        DescribeStacksRequest req = new DescribeStacksRequest();
        req.setStackName(appOTADemoIoTStackName);

        DescribeStacksResult result = client.describeStacks(req);
        List<Stack> stacks = result.getStacks();

        if (stacks.size() == 0)
            throw new IllegalArgumentException(
                    String.format("stack %s not found, deploy it first", appOTADemoIoTStackName));

        List<Output> outputs = stacks.get(0).getOutputs();

        for (Output output : outputs) {
            if (output.getOutputKey().equals(outputKey))
                return output.getOutputValue();
        }

        return null;
    }

    private String queryDeviceFileBucketName(String appOTADemoIoTStackName) {
        return this.queryStackOutput(appOTADemoIoTStackName, "devfilesbucketname");
    }

    private String queryThingCertificateId(String appOTADemoIoTStackName) {
        return this.queryStackOutput(appOTADemoIoTStackName, "certid");
    }

    private String queryJobDocBucketName(String appOTADemoIoTStackName) {
        return this.queryStackOutput(appOTADemoIoTStackName, "jobdocbucketname");
    }

    private String prepareCredentials(String certId) throws IOException {
        String credentialsPath = System.getProperty("user.dir") + "/target/app-ota-demo/credentials";

        File credentialsPathFile = new File(credentialsPath);
        FileUtils.deleteDirectory(credentialsPathFile);
        boolean ok = credentialsPathFile.mkdirs();
        if (!ok)
            throw new IOException(
                    String.format("failed to create IoT device credentials directory at %s", credentialsPath));

        String certFilePath = String.format("%s/cert.pem", credentialsPath);
        String rootCaPath = String.format("%s/root-ca.crt", credentialsPath);
        String publicKeyPath = String.format("%s/public.key", credentialsPath);
        String privateKeyPath = String.format("%s/private.key", credentialsPath);

        this.generateCredentials(certId, certFilePath, rootCaPath, publicKeyPath, privateKeyPath);

        List<String> srcFiles = Arrays.asList(certFilePath, rootCaPath, publicKeyPath, privateKeyPath);
        String zipFilePath = String.format("%s/credentials.zip", credentialsPath);
        FileOutputStream fos = new FileOutputStream(zipFilePath);
        ZipOutputStream zipOut = new ZipOutputStream(fos);

        for (String srcFile : srcFiles) {
            File fileToZip = new File(srcFile);
            FileInputStream fis = new FileInputStream(fileToZip);
            ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
        }

        zipOut.close();
        fos.close();

        log.info(String.format("the credentials package of the IoT device are prepared at %s", zipFilePath));

        return zipFilePath;
    }

    private String uploadCredentials(final String devFileBucketName, final String zipFilePath) {
        try {
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

            log.debug("connected to AWS S3 service.");

            File file = new File(zipFilePath);

            PutObjectRequest req = new PutObjectRequest(devFileBucketName, file.getName(), file);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("application/octet-stream");

            log.debug(String.format("uploading credentials package file %s ...", file.getName()));

            s3Client.putObject(req);

            log.info(String.format("credentials package file %s uploaded to the bucket %s",
                    file.getName(), devFileBucketName));

            Calendar c = Calendar.getInstance();
            c.setTime(new Date());  // now
            c.add(Calendar.DATE, 7);  // one week

            GeneratePresignedUrlRequest req1 = new GeneratePresignedUrlRequest(devFileBucketName, file.getName());
            req1.setExpiration(c.getTime());
            URL preSignedURL = s3Client.generatePresignedUrl(req1);

            return preSignedURL.toString();
        } catch (SdkClientException e) {
            e.printStackTrace();
            log.error(String.format("failed to upload credentials package file to S3 bucket %s", devFileBucketName));
            throw e;
        }
    }

    private String uploadNightsWatchRangerPackage(final String devFileBucketName) throws IOException {
        try {
            String packageDstPath = System.getProperty("user.dir") + "/target/app-ota-demo/nightswatch-ranger";

            File packageDstPathFile = new File(packageDstPath);
            FileUtils.deleteDirectory(packageDstPathFile);
            boolean ok = packageDstPathFile.mkdirs();
            if (!ok)
                throw new IOException(String.format(
                        "failed to create IoT device Night's Watch - Ranger package directory at %s", packageDstPath));

            String packageDstFilePath = String.format("%s/%s", packageDstPath, RANGER_PKG_FILE_NAME);

            String packageSrcFileName = String.format("nightswatch-ranger/%s", RANGER_PKG_FILE_NAME);
            URL packageSrc = getClass().getClassLoader().getResource(packageSrcFileName);
            if (packageSrc == null)
                throw new IllegalArgumentException(
                        String.format("Night's Watch - Ranger package file %s not found", packageSrcFileName));

            FileOutputStream out = new FileOutputStream(packageDstFilePath);
            out.write(packageSrc.openStream().readAllBytes());
            out.close();

            log.info(String.format(
                    "the Night's Watch - Ranger package of the IoT device are prepared at %s", packageDstFilePath));

            AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

            log.debug("connected to AWS S3 service");

            File file = new File(packageDstFilePath);
            PutObjectRequest req = new PutObjectRequest(devFileBucketName, file.getName(), file);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("application/octet-stream");

            log.debug(String.format("uploading Night's Watch - Ranger package file %s ...", file.getName()));

            s3Client.putObject(req);

            log.info(String.format("Night's Watch - Ranger package file %s uploaded to the bucket %s",
                    file.getName(), devFileBucketName));

            Calendar c = Calendar.getInstance();
            c.setTime(new Date());  // now
            c.add(Calendar.DATE, 7);  // one week

            GeneratePresignedUrlRequest req1 = new GeneratePresignedUrlRequest(devFileBucketName, file.getName());
            req1.setExpiration(c.getTime());
            URL preSignedUrl = s3Client.generatePresignedUrl(req1);

            return preSignedUrl.toString();

        } catch (SdkClientException | IOException e) {
            e.printStackTrace();
            log.error(String.format("failed to upload Night's Watch - Ranger package file to S3 bucket %s",
                    devFileBucketName));
            throw e;
        }
    }

    private String prepareInitScript(String preSignedCredentialsPackageURL,
                                     String preSignedRangerPackageURL) throws IOException {
        String scriptDstPath = System.getProperty("user.dir") + "/target/app-ota-demo/init-script";

        File scriptDstPathFile = new File(scriptDstPath);
        FileUtils.deleteDirectory(scriptDstPathFile);
        boolean ok = scriptDstPathFile.mkdirs();
        if (!ok)
            throw new IOException(String.format(
                    "failed to create IoT device init script directory at %s", scriptDstPath));

        String scriptDstFilePath = String.format("%s/%s", scriptDstPath, INIT_SCRIPT_FILE_NAME);

        String scriptSrcFileName = String.format("nw-app-ota-demo/%s", INIT_SCRIPT_FILE_NAME);
        URL scriptSrc = getClass().getClassLoader().getResource(scriptSrcFileName);
        if (scriptSrc == null)
            throw new IllegalArgumentException(
                    String.format("init script file %s not found", scriptSrcFileName));

        String script = new String(scriptSrc.openStream().readAllBytes());

        script = script.replace("<CREDENTIALS_PACKAGE_URL>", preSignedCredentialsPackageURL);
        script = script.replace("<NW_RANGER_PACKAGE_URL>", preSignedRangerPackageURL);

        PrintWriter out = new PrintWriter(scriptDstFilePath);
        out.print(script);
        out.close();

        log.info(String.format("init script of the IoT device are prepared at %s", scriptDstFilePath));

        return scriptDstFilePath;
    }

    private String uploadInitScript(final String devFileBucketName, String scriptFilePath) {
        try {
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

            log.debug("connected to AWS S3 service");

            File file = new File(scriptFilePath);
            PutObjectRequest req = new PutObjectRequest(devFileBucketName, file.getName(), file);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("application/octet-stream");

            log.debug(String.format("uploading init script file %s ...", file.getName()));

            s3Client.putObject(req);

            log.info(String.format("init script file %s uploaded to the bucket %s",
                    file.getName(), devFileBucketName));

            Calendar c = Calendar.getInstance();
            c.setTime(new Date());  // now
            c.add(Calendar.DATE, 7);  // one week

            GeneratePresignedUrlRequest req1 = new GeneratePresignedUrlRequest(devFileBucketName, file.getName());
            req1.setExpiration(c.getTime());
            URL preSignedUrl = s3Client.generatePresignedUrl(req1);

            return preSignedUrl.toString();

        } catch (SdkClientException e) {
            e.printStackTrace();
            log.error(String.format("failed to upload init script file to S3 bucket %s", devFileBucketName));
            throw e;
        }
    }

    private void deactivateThingCert(String certId) {
        AWSIot iotClient = AWSIotClientBuilder.defaultClient();
        log.debug("connected to AWS IoT service");

        // Deactivate three certificates
        //      CLI: aws iot update-certificate --new-status INACTIVE --certificate-id <certificate_id>
        UpdateCertificateRequest req = new UpdateCertificateRequest();
        req.setCertificateId(certId);
        req.setNewStatus("INACTIVE");
        iotClient.updateCertificate(req);

        log.info(String.format("the certificate %s is deactivated", certId));
    }

    private void emptyS3Bucket(String devFileBucketName) {
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

        log.debug("connected to AWS S3 service");

        ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(devFileBucketName).withMaxKeys(10);
        ListObjectsV2Result result;

        try {
            do {
                result = s3Client.listObjectsV2(req);

                for (S3ObjectSummary objSummary : result.getObjectSummaries()) {
                    log.debug(String.format(
                            "deleting file %s from the bucket %s ...", objSummary.getKey(), devFileBucketName));
                    s3Client.deleteObject(devFileBucketName, objSummary.getKey());
                }

                // If there are more than maxKeys keys in the bucket, get a continuation token
                // and list the next objects.
                String token = result.getNextContinuationToken();
                req.setContinuationToken(token);
            } while (result.isTruncated());
        } catch (AmazonServiceException e) {
            e.printStackTrace();
            throw e;
        }
    }
}
