package com.amazonaws.rp.nightswatch.builder.appota;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.iot.*;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.BlockPublicAccessOptions;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketProps;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Random;

public class AppOTADemoIoTStack extends Stack {
    private final Logger log = LoggerFactory.getLogger("nightswatch-app-ota-demo-iot-stack");

    private final static ObjectMapper JSON =
            new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

    private final static String POLICY_NAME = "nw-app-ota-demo-dev-policy";
    private final static String THING_NAME = "nw-app-ota-demo-dev";
    private final static String CSR_NAME = "nw-app-ota-demo-dev-csr";
    private final static String CERT_NAME = "nw-app-ota-demo-dev-cert";
    private final static String JOB_DOC_BUCKET_NAME = "nw-app-ota-demo-job-docs";
    private final static String S3_PRESIGN_ROLE_NAME = "nw-app-ota-demo-s3-presign-role";

    private final static String DEVICE_FILE_BUCKET_NAME = "nw-app-ota-demo-dev-files";

    private final String jobDocBucketName;
    private final String deviceFileBucketName;

    public AppOTADemoIoTStack(final Construct parent, final String id) throws IOException {
        this(parent, id, null);
    }

    public AppOTADemoIoTStack(final Construct parent, final String id, final StackProps props) throws IOException {
        super(parent, id, props);

        Random rand = new Random();
        this.jobDocBucketName = String.format("%s-%d", JOB_DOC_BUCKET_NAME, Math.abs(rand.nextLong()));
        this.deviceFileBucketName = String.format("%s-%d", DEVICE_FILE_BUCKET_NAME, Math.abs(rand.nextLong()));

        // IoT thing stuff
        CfnPolicy policy = this.createThingPolicy();
        CfnThing thing = this.createThing();
        this.createThingCert(policy, thing);
        this.createJobDocS3Bucket();
        this.createPreSignRole();

        this.createDeviceFileS3Bucket();
    }

    private CfnPolicy createThingPolicy() throws IOException {
        // Create a policy
        String fileName = String.format("nw-app-ota-demo/%s.json", POLICY_NAME);
        URL inlinePolicyDoc = getClass().getClassLoader().getResource(fileName);
        if (inlinePolicyDoc == null)
            throw new IllegalArgumentException(String.format("the policy statement file %s not found", fileName));
        JsonNode node = JSON.readTree(inlinePolicyDoc);

        CfnPolicy policy = new CfnPolicy(this, POLICY_NAME, CfnPolicyProps.builder()
                .policyName(POLICY_NAME)
                .policyDocument(node)
                .build());

        // Output the policy configuration
        new CfnOutput(this, "policy-name", CfnOutputProps.builder()
                .value(Objects.requireNonNull(policy.getPolicyName()))
                .description("the policy name for NW app OTA demo")
                .build());

        new CfnOutput(this, "policy-arn", CfnOutputProps.builder()
                .value(policy.getAttrArn())
                .description("the policy arn for NW app OTA demo")
                .build());

        return policy;
    }

    private CfnThing createThing() {
        // Create a thing
        CfnThing thing = new CfnThing(this, THING_NAME, CfnThingProps.builder()
                .thingName(THING_NAME)
                .build());

        // Output the thing configuration
        new CfnOutput(this, "thing-name", CfnOutputProps.builder()
                .value(Objects.requireNonNull(thing.getThingName()))
                .description("the thing name for NW app OTA demo")
                .build());

        return thing;
    }

    private void createThingCert(CfnPolicy policy, CfnThing thing) throws IOException {
        // Load CSR
        String fileName = String.format("nw-app-ota-demo/%s.pem", CSR_NAME);
        URL inlineCSRPem = getClass().getClassLoader().getResource(fileName);
        if (inlineCSRPem == null)
            throw new IllegalArgumentException(String.format("CSR file %s not found", fileName));
        String csrPem = new String(inlineCSRPem.openStream().readAllBytes());

        // Create a certificate
        CfnCertificate cert = new CfnCertificate(this, CERT_NAME, CfnCertificateProps.builder()
                .certificateSigningRequest(csrPem)
                .status("ACTIVE")
                .build());

        cert.addDependsOn(policy);
        cert.addDependsOn(thing);

        // Attach the policy to the certificate
        new CfnPolicyPrincipalAttachment(this, "nw-app-ota-demo-policy2cert",
                CfnPolicyPrincipalAttachmentProps.builder()
                        .policyName(POLICY_NAME)
                        .principal(cert.getAttrArn())
                        .build());

        // Attach the certificate to the thing
        new CfnThingPrincipalAttachment(this, "nw-app-ota-demo-cert2thing", CfnThingPrincipalAttachmentProps.builder()
                .thingName(THING_NAME)
                .principal(cert.getAttrArn())
                .build());

        // Output the thing configuration
        new CfnOutput(this, "cert-id", CfnOutputProps.builder()
                .value(cert.getRef())
                .description("the thing certificate ID for NW app OTA demo")
                .build());

        new CfnOutput(this, "cert-arn", CfnOutputProps.builder()
                .value(cert.getAttrArn())
                .description("the thing certificate ARN for NW app OTA demo")
                .build());
    }

    private void createJobDocS3Bucket() {
        // Create a S3 bucket for job document
        Bucket jobDocBucket = new Bucket(this, this.jobDocBucketName, BucketProps.builder()
                .removalPolicy(RemovalPolicy.DESTROY)
                .bucketName(this.jobDocBucketName)
                .build());

        new CfnOutput(this, "job-doc-bucket-name", CfnOutputProps.builder()
                .value(jobDocBucket.getBucketName())
                .description("the name of s3 bucket to save job document for NW app OTA demo")
                .build());
    }

    private void createPreSignRole() {
        // Create an IAM role for pre-sign the file stored as a S3 object
        Role iotPreSignS3Role = new Role(this, S3_PRESIGN_ROLE_NAME, RoleProps.builder()
                .assumedBy(new ServicePrincipal("iot.amazonaws.com"))
                .path("/service-role/")
                .managedPolicies(Lists.newArrayList(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonS3ReadOnlyAccess")))
                .build());

        new CfnOutput(this, "s3-pre-sign-iam-role-arn", CfnOutputProps.builder()
                .value(iotPreSignS3Role.getRoleArn())
                .description("the S3 pre-sign IAM role ARN for NW app OTA demo")
                .build());
    }

    private void createDeviceFileS3Bucket() {
        Bucket devFileBucket = new Bucket(this, this.deviceFileBucketName, BucketProps.builder()
                .blockPublicAccess(new BlockPublicAccess(BlockPublicAccessOptions.builder()
                        .blockPublicAcls(false)
                        .blockPublicPolicy(true)
                        .restrictPublicBuckets(true)
                        .build()))
                .removalPolicy(RemovalPolicy.DESTROY)
                .publicReadAccess(false)
                .bucketName(this.deviceFileBucketName)
                .build());

        new CfnOutput(this, "dev-files-bucket-name", CfnOutputProps.builder()
                .value(devFileBucket.getBucketName())
                .description(
                        "the name of s3 bucket to save device assert files to act IoT device for NW app OTA demo")
                .build());
    }
}
