package com.amazonaws.rp.nightswatch.builder.appota;

import com.amazonaws.rp.nightswatch.builder.utils.IoTCore;
import com.amazonaws.rp.nightswatch.builder.utils.S3;
import com.amazonaws.rp.nightswatch.builder.utils.StackOutputQuerier;
import com.amazonaws.services.iot.AWSIot;
import com.amazonaws.services.iot.AWSIotClientBuilder;
import com.amazonaws.services.iot.model.CertificateDescription;
import com.amazonaws.services.iot.model.DescribeCertificateRequest;
import com.amazonaws.services.iot.model.DescribeCertificateResult;
import com.amazonaws.services.iot.model.UpdateCertificateRequest;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AppOTADemoAssert {
    private final Logger log = LoggerFactory.getLogger("nightswatch-app-ota-demo-asset");
    private final StackOutputQuerier outputQuerier = new StackOutputQuerier();
    private final S3 s3Util = new S3();
    private final IoTCore jobDeleter = new IoTCore();

    private final static String PUB_KEY_NAME = "nw-app-ota-demo-dev-public";
    private final static String PRV_KEY_NAME = "nw-app-ota-demo-dev-private";
    private final static String ROOT_CA_NAME = "root-ca.crt";

    private final static String CREDENTIALS_FILE_NAME = "credentials.zip";
    private final static String RANGER_PKG_FILE_NAME = "nightswatch-ranger.tar.gz";

    public void provision(final String appOTADemoIoTStackName) throws IOException {
        String devFileBucketName = this.outputQuerier.query(this.log, appOTADemoIoTStackName, "devfilesbucketname");
        if (devFileBucketName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of s3 bucket to save device assert files not found, " +
                            "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        String certId = this.outputQuerier.query(this.log, appOTADemoIoTStackName, "certid");
        if (certId == null)
            throw new IllegalArgumentException(String.format("the thing certificate ID not found, " +
                    "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        // Night's Watch - Ranger stuff
        String zipFilePath = this.prepareCredentials(certId);
        String pkgFilePath = this.prepareNightsWatchRangerPackage();
        this.s3Util.uploadFile(this.log, devFileBucketName, zipFilePath);
        this.s3Util.uploadFile(this.log, devFileBucketName, pkgFilePath);

        String preSignedCredentialsPackageURL = this.s3Util.getObjectPreSignedUrl(
                this.log, devFileBucketName, AppOTADemoAssert.CREDENTIALS_FILE_NAME, 7);
        String preSignedRangerPackageURL = this.s3Util.getObjectPreSignedUrl(
                this.log, devFileBucketName, AppOTADemoAssert.RANGER_PKG_FILE_NAME, 7);

        String scriptFilePath = this.prepareSetupScript(preSignedCredentialsPackageURL, preSignedRangerPackageURL);

        this.s3Util.uploadFile(this.log, devFileBucketName, scriptFilePath);

        log.info(String.format("the device files is prepared at %s", devFileBucketName));
    }

    public void deProvision(final String appOTADemoIoTStackName) {
        String devFileBucketName = this.outputQuerier.query(this.log, appOTADemoIoTStackName, "devfilesbucketname");
        if (devFileBucketName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of s3 bucket to save device assert files not found, " +
                            "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        String jobDocBucketName = this.outputQuerier.query(this.log, appOTADemoIoTStackName, "jobdocbucketname");
        if (jobDocBucketName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of s3 bucket to save job document not found, " +
                            "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        String certId = this.outputQuerier.query(this.log, appOTADemoIoTStackName, "certid");
        if (certId == null)
            throw new IllegalArgumentException(String.format("the thing certificate ID not found, " +
                    "is the NW app OTA demo stack %s invalid?", appOTADemoIoTStackName));

        this.deactivateThingCert(certId);
        log.info(String.format("the device certificate %s is deactivated", certId));

        this.s3Util.emptyBucket(this.log, devFileBucketName);
        log.info(String.format("the device files S3 bucket %s is cleaned up to empty", devFileBucketName));

        this.s3Util.emptyBucket(this.log, jobDocBucketName);
        log.info(String.format("the job docs S3 bucket %s is cleaned up to empty", jobDocBucketName));

        this.deleteJobs();
        log.info("all jobs are deleted");
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

    private String prepareCredentials(String certId) throws IOException {
        String credentialsPath = String.format("%s/target/app-ota-demo/credentials",
                System.getProperty("user.dir"));

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
        String zipFilePath = String.format("%s/%s", credentialsPath, CREDENTIALS_FILE_NAME);
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

    private String prepareNightsWatchRangerPackage() throws IOException {
        String packageDstPath = String.format("%s/target/nightswatch-ranger",
                System.getProperty("user.dir"));

        File packageDstPathFile = new File(packageDstPath);
        FileUtils.deleteDirectory(packageDstPathFile);
        boolean ok = packageDstPathFile.mkdirs();
        if (!ok)
            throw new IOException(String.format(
                    "failed to create IoT device Night's Watch - Ranger package directory at %s", packageDstPath));

        String packageDstFilePath = String.format("%s/%s", packageDstPath, RANGER_PKG_FILE_NAME);

        String packageSrcFileName = String.format("nightswatch-ranger_x64/%s", RANGER_PKG_FILE_NAME);
        URL packageSrc = getClass().getClassLoader().getResource(packageSrcFileName);
        if (packageSrc == null)
            throw new IllegalArgumentException(
                    String.format("Night's Watch - Ranger package file %s not found", packageSrcFileName));

        FileOutputStream out = new FileOutputStream(packageDstFilePath);
        out.write(packageSrc.openStream().readAllBytes());
        out.close();

        log.info(String.format(
                "the Night's Watch - Ranger package of the IoT device are prepared at %s", packageDstFilePath));

        return packageDstFilePath;
    }

    private String prepareSetupScript(String preSignedCredentialsPackageURL,
                                      String preSignedRangerPackageURL) throws IOException {
        String scriptDstPath = String.format("%s/target/app-ota-demo/setup-script",
                System.getProperty("user.dir"));

        File scriptDstPathFile = new File(scriptDstPath);
        FileUtils.deleteDirectory(scriptDstPathFile);
        boolean ok = scriptDstPathFile.mkdirs();
        if (!ok)
            throw new IOException(String.format(
                    "failed to create IoT device setup script directory at %s", scriptDstPath));

        String scriptDstFilePath = String.format(
                "%s/%s", scriptDstPath, AppOTADemoDeviceStack.SETUP_SCRIPT_FILE_NAME);

        String scriptSrcFileName = String.format(
                "nw-app-ota-demo/%s", AppOTADemoDeviceStack.SETUP_SCRIPT_FILE_NAME);
        URL scriptSrc = getClass().getClassLoader().getResource(scriptSrcFileName);
        if (scriptSrc == null)
            throw new IllegalArgumentException(
                    String.format("setup script file %s not found", scriptSrcFileName));

        String script = new String(scriptSrc.openStream().readAllBytes());

        script = script.replace("<CREDENTIALS_PACKAGE_URL>", preSignedCredentialsPackageURL);
        script = script.replace("<NW_RANGER_PACKAGE_URL>", preSignedRangerPackageURL);

        PrintWriter out = new PrintWriter(scriptDstFilePath);
        out.print(script);
        out.close();

        log.info(String.format("setup script of the IoT device are prepared at %s", scriptDstFilePath));

        return scriptDstFilePath;
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

    private void deleteJobs() {
        // delete the potential existing jobs might related to the thing
        this.jobDeleter.deleteJob(this.log, AppOTADemoApplication.APP_V1_DEPLOY_JOB_ID);
        this.jobDeleter.deleteJob(this.log, AppOTADemoApplication.APP_V2_DEPLOY_JOB_ID);
        this.jobDeleter.deleteJob(this.log, AppOTADemoApplication.APP_V1_DESTROY_JOB_ID);
        this.jobDeleter.deleteJob(this.log, AppOTADemoApplication.APP_V2_DESTROY_JOB_ID);
    }
}
