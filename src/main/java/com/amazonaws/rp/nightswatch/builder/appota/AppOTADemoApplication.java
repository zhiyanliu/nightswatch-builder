package com.amazonaws.rp.nightswatch.builder.appota;

import com.amazonaws.SdkClientException;
import com.amazonaws.rp.nightswatch.builder.utils.IoTJobDeleter;
import com.amazonaws.rp.nightswatch.builder.utils.StackOutputQuerier;
import com.amazonaws.services.iot.AWSIot;
import com.amazonaws.services.iot.AWSIotClientBuilder;
import com.amazonaws.services.iot.model.DescribeThingRequest;
import com.amazonaws.services.iot.model.DescribeThingResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class AppOTADemoApplication {
    private final Logger log = LoggerFactory.getLogger("nightswatch-app-ota-demo-app");
    private final StackOutputQuerier outputQuerier = new StackOutputQuerier();
    private final IoTJobDeleter jobDeleter = new IoTJobDeleter();

    private final static String APP_PKG_NAME = "app_xxx_pkg";
    private final static String APP_DEPLOY_JOB_DOC_NAME = "deploy_app_xxx_pkg";
    private final static String APP_DESTROY_JOB_DOC_NAME = "destroy_app_xxx_pkg";

    public final static String APP_V1_DEPLOY_JOB_ID = "nw-app-ota-demo-deploy-app-v1";
    public final static String APP_V2_DEPLOY_JOB_ID = "nw-app-ota-demo-deploy-app-v2";
    public final static String APP_V1_DESTROY_JOB_ID = "nw-app-ota-demo-destroy-app-v1";
    public final static String APP_V2_DESTROY_JOB_ID = "nw-app-ota-demo-destroy-app-v2";

    public void provisionV1(final String appOTADemoIoTStackName, final String arch, final String containerFlag)
            throws IOException {
        String devFileBucketName = this.queryDeviceFileBucketName(appOTADemoIoTStackName);
        if (devFileBucketName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of s3 bucket to save device assert files not found, " +
                            "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        String jobDocBucketName = this.queryJobDocBucketName(appOTADemoIoTStackName);
        if (jobDocBucketName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of s3 bucket to save job documents not found, " +
                            "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        this.uploadPackage(devFileBucketName, arch, containerFlag, "v1");

        List<String> result = this.prepareAppJobDoc(devFileBucketName, arch, containerFlag,
                "v1", "deployment", APP_DEPLOY_JOB_DOC_NAME);
        String jobDocContent1 = result.get(0);
        String jobDocFilePath1 = result.get(1);

        String jobDocS3ObjectPath1 = this.uploadJobDoc(jobDocBucketName, jobDocFilePath1, "deployment");

        String cmd1 = this.generateCommand(appOTADemoIoTStackName, jobDocS3ObjectPath1, APP_V1_DEPLOY_JOB_ID);

        result = this.prepareAppJobDoc(devFileBucketName, arch, containerFlag,
                "v1", "destroy", APP_DESTROY_JOB_DOC_NAME);
        String jobDocContent2 = result.get(0);
        String jobDocFilePath2 = result.get(1);

        String jobDocS3ObjectPath2 = this.uploadJobDoc(jobDocBucketName, jobDocFilePath2, "destroy");

        String cmd2 = this.generateCommand(appOTADemoIoTStackName, jobDocS3ObjectPath2, APP_V1_DESTROY_JOB_ID);

        System.out.println();
        System.out.println("Outputs:");
        System.out.println(String.format("application deployment job document:\n%s", jobDocContent1));
        System.out.println(String.format("application deployment job document url:\n\t%s", jobDocS3ObjectPath1));
        System.out.println(String.format("application deployment command line:\n%s", cmd1));
        System.out.println(String.format("application destroy job document:\n%s", jobDocContent2));
        System.out.println(String.format("application destroy job document url:\n\t%s", jobDocS3ObjectPath2));
        System.out.println(String.format("application destroy command line:\n%s", cmd2));
    }

    public void provisionV2(final String appOTADemoIoTStackName, final String arch, final String containerFlag)
            throws IOException {
        String devFileBucketName = this.queryDeviceFileBucketName(appOTADemoIoTStackName);
        if (devFileBucketName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of s3 bucket to save device assert files not found, " +
                            "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        String jobDocBucketName = this.queryJobDocBucketName(appOTADemoIoTStackName);
        if (jobDocBucketName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of s3 bucket to save job documents not found, " +
                            "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        this.uploadPackage(devFileBucketName, arch, containerFlag, "v2");

        List<String> result = this.prepareAppJobDoc(devFileBucketName, arch, containerFlag,
                "v2", "deployment", APP_DEPLOY_JOB_DOC_NAME);
        String jobDocContent1 = result.get(0);
        String jobDocFilePath1 = result.get(1);

        String jobDocS3ObjectPath1 = this.uploadJobDoc(jobDocBucketName, jobDocFilePath1, "deployment");

        String cmd1 = this.generateCommand(appOTADemoIoTStackName, jobDocS3ObjectPath1, APP_V2_DEPLOY_JOB_ID);

        result = this.prepareAppJobDoc(devFileBucketName, arch, containerFlag,
                "v2", "destroy", APP_DESTROY_JOB_DOC_NAME);
        String jobDocContent2 = result.get(0);
        String jobDocFilePath2 = result.get(1);

        String jobDocS3ObjectPath2 = this.uploadJobDoc(jobDocBucketName, jobDocFilePath2, "destroy");

        String cmd2 = this.generateCommand(appOTADemoIoTStackName, jobDocS3ObjectPath2, APP_V2_DESTROY_JOB_ID);

        System.out.println();
        System.out.println("Outputs:");
        System.out.println(String.format("application deployment job document:\n%s", jobDocContent1));
        System.out.println(String.format("application deployment job document url:\n\t%s", jobDocS3ObjectPath1));
        System.out.println(String.format("application deployment command line:\n%s", cmd1));
        System.out.println(String.format("application destroy job document:\n%s", jobDocContent2));
        System.out.println(String.format("application destroy job document url:\n\t%s", jobDocS3ObjectPath2));
        System.out.println(String.format("application destroy command line:\n%s", cmd2));
    }

    private String queryDeviceFileBucketName(final String appOTADemoIoTStackName) {
        return this.outputQuerier.query(appOTADemoIoTStackName, "devfilesbucketname");
    }

    private String queryJobDocBucketName(final String appOTADemoIoTStackName) {
        return this.outputQuerier.query(appOTADemoIoTStackName, "jobdocbucketname");
    }

    private void uploadPackage(final String devFileBucketName, final String arch, final String containerFlag,
                               final String version) throws IOException {
        try {
            String packageDstPath = String.format("%s/target/app-ota-demo/app_%s_%s_%s",
                    System.getProperty("user.dir"), arch, containerFlag, version);

            File packageDstPathFile = new File(packageDstPath);
            FileUtils.deleteDirectory(packageDstPathFile);
            boolean ok = packageDstPathFile.mkdirs();
            if (!ok)
                throw new IOException(String.format(
                        "failed to create demo application package %s directory at %s", version, packageDstPath));

            String packageDstFilePath = String.format(
                    "%s/%s_%s_%s.tar.gz", packageDstPath, APP_PKG_NAME, containerFlag, version);

            String packageSrcFileName = String.format("nw-app-ota-demo/app_%s_%s_%s/%s.tar.gz",
                    arch, containerFlag, version, APP_PKG_NAME);
            URL packageSrc = getClass().getClassLoader().getResource(packageSrcFileName);
            if (packageSrc == null)
                throw new IllegalArgumentException(
                        String.format("application package file %s not found", packageSrcFileName));

            FileOutputStream out = new FileOutputStream(packageDstFilePath);
            out.write(packageSrc.openStream().readAllBytes());
            out.close();

            log.info(String.format(
                    "the application package of the IoT device are prepared at %s", packageDstFilePath));

            AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

            log.debug("connected to AWS S3 service");

            File file = new File(packageDstFilePath);
            PutObjectRequest req = new PutObjectRequest(devFileBucketName, file.getName(), file);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("application/octet-stream");

            log.debug(String.format("uploading application package file %s ...", file.getName()));

            s3Client.putObject(req);

            log.info(String.format("application package file %s uploaded to the bucket %s",
                    file.getName(), devFileBucketName));
        } catch (SdkClientException | IOException e) {
            e.printStackTrace();
            log.error(String.format("failed to upload application package file to S3 bucket %s",
                    devFileBucketName));
            throw e;
        }
    }

    private List<String> prepareAppJobDoc(final String jobDocBucketName, final String arch,
                                          final String containerFlag, final String version,
                                          final String deploymentFlag, final String jobDocName) throws IOException {
        try {
            String jobDocDstPath = String.format("%s/target/app-ota-demo/app_%s_%s_%s",
                    System.getProperty("user.dir"), arch, containerFlag, version);

            File jobDocDstPathFile = new File(jobDocDstPath);
            FileUtils.deleteDirectory(jobDocDstPathFile);
            boolean ok = jobDocDstPathFile.mkdirs();
            if (!ok)
                throw new IOException(String.format("failed to create demo application %s job document directory at %s",
                        deploymentFlag, jobDocDstPath));

            String jobDocDstFilePath = String.format("%s/%s_%s.json", jobDocDstPath, jobDocName, version);

            String jobDocSrcFileName = String.format("nw-app-ota-demo/app_%s_%s_%s/%s.json",
                    arch, containerFlag, version, jobDocName);
            URL jobDocSrcFilePath = getClass().getClassLoader().getResource(jobDocSrcFileName);
            if (jobDocSrcFilePath == null)
                throw new IllegalArgumentException(
                        String.format("application %s job document %s not found", deploymentFlag, jobDocSrcFileName));

            String doc = new String(jobDocSrcFilePath.openStream().readAllBytes());

            doc = doc.replace("<BUCKET_NAME>", jobDocBucketName);
            doc = String.format("\t%s", doc.replace("\n", "\n\t"));

            PrintWriter out = new PrintWriter(jobDocDstFilePath);
            out.print(doc);
            out.close();

            log.info(String.format("the application %s job document of the IoT device are prepared at %s",
                    deploymentFlag, jobDocDstFilePath));

            return Arrays.asList(doc, jobDocDstFilePath);
        } catch (IOException e) {
            e.printStackTrace();
            log.error(String.format("failed to prepare application %s job document", deploymentFlag));
            throw e;
        }
    }

    private String uploadJobDoc(final String jobDocBucketName, final String jobDocFilePath,
                                final String deploymentFlag) {

        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

        log.debug("connected to AWS S3 service");

        File file = new File(jobDocFilePath);
        PutObjectRequest req = new PutObjectRequest(jobDocBucketName, file.getName(), file);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/octet-stream");

        log.debug(String.format("uploading application %s job document file %s ...", deploymentFlag, file.getName()));

        s3Client.putObject(req);

        log.info(String.format("application %s job document file %s uploaded to the bucket %s",
                deploymentFlag, file.getName(), jobDocBucketName));

        return String.format("https://s3.amazonaws.com/%s/%s", jobDocBucketName, file.getName());
    }

    private String generateCommand(final String appOTADemoIoTStackName, final String jobDocS3ObjectPath,
                                   final String jobID) {

        String thingName = this.outputQuerier.query(appOTADemoIoTStackName, "thingname");
        if (thingName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of IoT device not found, is the NW app OTA demo stack %s invalid?",
                    appOTADemoIoTStackName));

        DescribeThingRequest req = new DescribeThingRequest();
        req.setThingName(thingName);

        AWSIot client = AWSIotClientBuilder.defaultClient();
        DescribeThingResult result = client.describeThing(req);
        String thingARN = result.getThingArn();

        String s3PreSignIAMRoleARN = this.outputQuerier.query(appOTADemoIoTStackName, "s3presigniamrolearn");
        if (s3PreSignIAMRoleARN == null)
            throw new IllegalArgumentException(String.format(
                    "the S3 pre-sign IAM role ARN not found," +
                            " is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        this.jobDeleter.delete(this.log, jobID);

        String cmd =
                "aws iot create-job \\\n" +
                        "\t--job-id <JOB_ID> \\\n" +
                        "\t--targets <THING_ARN> \\\n" +
                        "\t--document-source <JOB_DOC_URL> \\\n" +
                        "\t--presigned-url-config " +
                        "\"{\\\"roleArn\\\":\\\"<PRE_SIGN_ROLE_ARN>\\\", \\\"expiresInSec\\\":3600}\"";

        cmd = cmd.replace("<JOB_ID>", jobID);
        cmd = cmd.replace("<THING_ARN>", thingARN);
        cmd = cmd.replace("<JOB_DOC_URL>", jobDocS3ObjectPath);
        cmd = cmd.replace("<PRE_SIGN_ROLE_ARN>", s3PreSignIAMRoleARN);
        cmd = String.format("\t%s", cmd.replace("\n", "\n\t"));

        return cmd;
    }
}
