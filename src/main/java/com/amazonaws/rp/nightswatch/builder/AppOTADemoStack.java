package com.amazonaws.rp.nightswatch.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Lists;
import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.ec2.*;
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
import java.util.Arrays;

public class AppOTADemoStack extends Stack {
    private final static ObjectMapper JSON =
            new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

    private final static String POLICY_NAME = "nw-app-ota-demo-dev-policy";
    private final static String THING_NAME = "nw-app-ota-demo-dev";
    private final static String CSR_NAME = "nw-app-ota-demo-dev-csr";
    private final static String CERT_NAME = "nw-app-ota-demo-dev-cert";
    private final static String JOB_DOC_BUCKET_NAME = "nw-app-ota-demo-job-docs";

    private final static String DEVICE_FILE_BUCKET_NAME = "nw-app-ota-demo-dev-files";

    private final String ec2ImageID;
    private final String ec2Flavor;
    private final String ec2KeyName;

    public AppOTADemoStack(final Construct parent, final String id) throws IOException {
        this(parent, id, null);
    }

    AppOTADemoStack(final Construct parent, final String id, final StackProps props) throws IOException {
        super(parent, id, props);

        Object ec2DeviceImageIDObj = this.getNode().tryGetContext("ec2-image-id");
        if (ec2DeviceImageIDObj == null)
            throw new IllegalArgumentException("Ubuntu x64 image ID of EC2 instance is not provided for this demo");
        this.ec2ImageID = ec2DeviceImageIDObj.toString();

        Object ec2FlavorObj = this.getNode().tryGetContext("ec2-instance-type");
        if (ec2FlavorObj == null)
            this.ec2Flavor = "t2.small";
        else
            this.ec2Flavor = ec2FlavorObj.toString();

        Object ec2KeyNameObj = this.getNode().tryGetContext("ec2-key-name");
        if (ec2KeyNameObj != null)
            this.ec2KeyName = ec2KeyNameObj.toString();
        else
            this.ec2KeyName = null;

        // IoT thing stuff
        CfnPolicy policy = this.createThingPolicy();
        CfnThing thing = this.createThing();
        this.createThingCert(policy, thing);
        this.createJobDocS3Bucket();
        this.createPreSignRole();

        // upload device certificate, key and root ca certificate.
        Bucket devFileBucket = this.createDeviceFileS3Bucket();
        this.uploadDeviceFiles(devFileBucket);

        // EC2 instance (act device) stuff
        CfnInternetGateway igw = this.createIGW();
        CfnVPC vpc = this.createVPC(igw);
        CfnSubnet subnet = this.createSubnet(vpc, igw);
        CfnSecurityGroup sg = this.createSecurityGroup(vpc);
        this.createEC2Device(subnet, sg);
    }

    private CfnPolicy createThingPolicy() throws IOException {
        // Create a policy
        CfnPolicy policy;

        try {
            URL inlinePolicyDoc = getClass().getClassLoader().getResource(POLICY_NAME + ".json");
            JsonNode node = JSON.readTree(inlinePolicyDoc);

            policy = new CfnPolicy(this, POLICY_NAME, CfnPolicyProps.builder()
                    .withPolicyName(POLICY_NAME)
                    .withPolicyDocument(node)
                    .build());
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }

        // Output the policy configuration
        new CfnOutput(this, "policy-name", CfnOutputProps.builder()
                .withValue(policy.getPolicyName())
                .withDescription("the policy name for NW app OTA demo")
                .build());

        new CfnOutput(this, "policy-arn", CfnOutputProps.builder()
                .withValue(policy.getAttrArn())
                .withDescription("the policy arn for NW app OTA demo")
                .build());

        return policy;
    }

    private CfnThing createThing() throws IOException {
        // Create a thing
        CfnThing thing = new CfnThing(this, THING_NAME, CfnThingProps.builder()
                .withThingName(THING_NAME)
                .build());

        // Output the thing configuration
        new CfnOutput(this, "thing-name", CfnOutputProps.builder()
                .withValue(thing.getThingName())
                .withDescription("the thing name for NW app OTA demo")
                .build());

        return thing;
    }

    private void createThingCert(CfnPolicy policy, CfnThing thing) throws IOException {
        // Load CSR
        String csrPem;

        try {
            URL inlineCSRPem = getClass().getClassLoader().getResource(CSR_NAME + ".pem");
            csrPem = new String(inlineCSRPem.openStream().readAllBytes());
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }

        // Create a certificate
        CfnCertificate cert = new CfnCertificate(this, CERT_NAME, CfnCertificateProps.builder()
                .withCertificateSigningRequest(csrPem)
                .withStatus("ACTIVE")
                .build());

        cert.addDependsOn(policy);
        cert.addDependsOn(thing);

        // Attach the policy to the certificate
        new CfnPolicyPrincipalAttachment(this, "nw-app-ota-demo-policy2cert",
                CfnPolicyPrincipalAttachmentProps.builder()
                        .withPolicyName(POLICY_NAME)
                        .withPrincipal(cert.getAttrArn())
                        .build());

        // Attach the certificate to the thing
        new CfnThingPrincipalAttachment(this, "nw-app-ota-demo-cert2thing", CfnThingPrincipalAttachmentProps.builder()
                .withThingName(THING_NAME)
                .withPrincipal(cert.getAttrArn())
                .build());

        // Output the thing configuration
        new CfnOutput(this, "cert-id", CfnOutputProps.builder()
                .withValue(cert.getRef())
                .withDescription("the certificate ID for NW app OTA demo")
                .build());

        new CfnOutput(this, "cert-arn", CfnOutputProps.builder()
                .withValue(cert.getAttrArn())
                .withDescription("the certificate ARN for NW app OTA demo")
                .build());
    }

    private void createJobDocS3Bucket() {
        // Create a S3 bucket for job document
        Bucket jobDocBucket = new Bucket(this, JOB_DOC_BUCKET_NAME, BucketProps.builder()
                .withRemovalPolicy(RemovalPolicy.DESTROY)
                .withPublicReadAccess(true)
                .withBucketName(JOB_DOC_BUCKET_NAME)
                .build());

        new CfnOutput(this, "job-doc-bucket-name", CfnOutputProps.builder()
                .withValue(jobDocBucket.getBucketName())
                .withDescription("the name of s3 bucket to save job document for NW app OTA demo")
                .build());
    }

    private void createPreSignRole() {
        // Create an IAM role for pre-sign the file stored as a S3 object
        Role iotPreSignS3Role = new Role(this, "IoTPreSignS3ObjectRole", RoleProps.builder()
                .withAssumedBy(new ServicePrincipal("iot.amazonaws.com"))
                .withPath("/service-role/")
                .withManagedPolicies(Lists.newArrayList(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonS3ReadOnlyAccess")))
                .build());

        new CfnOutput(this, "s3-pre-sign-iam-role-arn", CfnOutputProps.builder()
                .withValue(iotPreSignS3Role.getRoleArn())
                .withDescription("the S3 pre-sign IAM role ARN for NW app OTA demo")
                .build());
    }

    private Bucket createDeviceFileS3Bucket() {
        Bucket devFileBucket = new Bucket(this, DEVICE_FILE_BUCKET_NAME, BucketProps.builder()
                .withBlockPublicAccess(new BlockPublicAccess(BlockPublicAccessOptions.builder()
                        .withBlockPublicAcls(false)
                        .withIgnorePublicAcls(false)
                        .withBlockPublicPolicy(false)
                        .withRestrictPublicBuckets(false)
                        .build()))
                .withRemovalPolicy(RemovalPolicy.DESTROY)
                .withPublicReadAccess(true)
                .withBucketName(DEVICE_FILE_BUCKET_NAME)
                .build());

        return devFileBucket;
    }

    private void uploadDeviceFiles(Bucket devFileBucket) {

    }

    private CfnInternetGateway createIGW() {
        // Create an IGW for the VPC
        CfnInternetGateway igw = new CfnInternetGateway(this, "nw-app-ota-demo-igw",
                CfnInternetGatewayProps.builder().build());

        return igw;
    }

    private CfnVPC createVPC(CfnInternetGateway igw) {
        // Create a VPC for the subnet
        CfnVPC vpc = new CfnVPC(this, "nw-app-ota-demp-vpc", CfnVPCProps.builder()
                .withCidrBlock("192.168.1.0/24")
                .withEnableDnsHostnames(true)
                .withEnableDnsSupport(true)
                .withEnableDnsSupport(true)
                .build());

        new CfnVPCGatewayAttachment(this, "nw-app-ota-demo-igw2vpc", CfnVPCGatewayAttachmentProps.builder()
                .withInternetGatewayId(igw.getRef())
                .withVpcId(vpc.getRef())
                .build());

        return vpc;
    }

    private CfnSubnet createSubnet(CfnVPC vpc, CfnInternetGateway igw) {
        // Create a Subnet for the EC2 instance
        CfnSubnet subnet = new CfnSubnet(this, "nw-app-ota-demo-subnet", CfnSubnetProps.builder()
                .withCidrBlock("192.168.1.0/24")
                .withMapPublicIpOnLaunch(true)
                .withVpcId(vpc.getRef())
                .build());

        CfnRouteTable routeTable = new CfnRouteTable(this, "nw-app-ota-demo-route-table", CfnRouteTableProps.builder()
                .withVpcId(vpc.getRef())
                .build());

        new CfnRoute(this, "nw-app-ota-demo-route-igw", CfnRouteProps.builder()
                .withDestinationCidrBlock("0.0.0.0/0")
                .withGatewayId(igw.getRef())
                .withRouteTableId(routeTable.getRef())
                .build());

        new CfnSubnetRouteTableAssociation(this, "nw-app-ota-demo-route-table4subnet",
                CfnSubnetRouteTableAssociationProps.builder()
                        .withRouteTableId(routeTable.getRef())
                        .withSubnetId(subnet.getRef())
                        .build());

        return subnet;
    }

    private CfnSecurityGroup createSecurityGroup(CfnVPC vpc) {
        CfnSecurityGroup.IngressProperty ingressProperty = CfnSecurityGroup.IngressProperty.builder()
                .withCidrIp("0.0.0.0/0")
                .withIpProtocol("TCP")
                .withFromPort(22)
                .withToPort(22)  // open SSH
                .build();

        CfnSecurityGroup sg = new CfnSecurityGroup(this, "nw-app-ota-demo-sg", CfnSecurityGroupProps.builder()
                .withGroupName("nw-app-ota-demo-sg")
                .withGroupDescription("Nights Watch App OTA demo security group.")
                .withVpcId(vpc.getRef())
                .withSecurityGroupIngress(Arrays.asList(ingressProperty))
                .build());

        return sg;
    }

    private void createEC2Device(CfnSubnet subnet, CfnSecurityGroup sg) {
        CfnInstance instance = new CfnInstance(this, "nw-app-ota-demo-ec2-device", CfnInstanceProps.builder()
                .withImageId(this.ec2ImageID)
                .withInstanceType(this.ec2Flavor)
                .withSubnetId(subnet.getRef())
                .withSecurityGroupIds(Arrays.asList(sg.getRef()))
                .withKeyName(this.ec2KeyName)
                .withTags(Arrays.asList(CfnTag.builder().withKey("Name").withValue("nw-app-ota-demo-device").build()))
                .build());
    }
}
