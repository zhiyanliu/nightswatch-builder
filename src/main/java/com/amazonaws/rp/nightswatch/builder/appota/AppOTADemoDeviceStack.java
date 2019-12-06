package com.amazonaws.rp.nightswatch.builder.appota;

import com.amazonaws.rp.nightswatch.builder.utils.S3;
import com.amazonaws.rp.nightswatch.builder.utils.StackOutputQuerier;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.ec2.*;

import java.util.*;

public class AppOTADemoDeviceStack extends Stack {
    private final Logger log = LoggerFactory.getLogger("nightswatch-app-ota-demo-device-stack");
    private final StackOutputQuerier outputQuerier = new StackOutputQuerier();
    private final S3 s3util = new S3();

    private final String ec2ImageID;
    private final String ec2KeyName;
    private final String ec2SetupScriptURL;

    public final static String SETUP_SCRIPT_FILE_NAME = "setup.py";

    public AppOTADemoDeviceStack(final Construct parent, final String id, final String appOTADemoIoTStackName) {
        this(parent, id, null, appOTADemoIoTStackName);
    }

    public AppOTADemoDeviceStack(final Construct parent, final String id, final StackProps props,
                                 final String appOTADemoIoTStackName) {
        super(parent, id, props);

        Object ec2DeviceImageIDObj = this.getNode().tryGetContext("ec2-image-id");
        if (ec2DeviceImageIDObj == null)
            // will lookup the Ubuntu 18.04 x86_64 AMI
            this.ec2ImageID = null;
        else
            this.ec2ImageID = ec2DeviceImageIDObj.toString();

        Object ec2KeyNameObj = this.getNode().tryGetContext("ec2-key-name");
        if (ec2KeyNameObj == null)
            this.ec2KeyName = null;
        else
            this.ec2KeyName = ec2KeyNameObj.toString();

        String devFileBucketName = this.outputQuerier.query(this.log, appOTADemoIoTStackName, "devfilesbucketname");
        if (devFileBucketName == null) {
            // instead of to raise exception, since CDK needs (e.g. list and bootstrap)
            this.ec2SetupScriptURL = null;
        } else {
            this.ec2SetupScriptURL =
                    this.s3util.getObjectPreSignedUrl(this.log, devFileBucketName, SETUP_SCRIPT_FILE_NAME, 7);
        }

        // EC2 instance (act device) stuff
        CfnInternetGateway igw = this.createIGW();
        CfnVPC vpc = this.createVPC(igw);
        CfnSubnet subnet = this.createSubnet(vpc, igw);
        CfnSecurityGroup sg = this.createSecurityGroup(vpc);
        this.createEC2Device(subnet, sg);
    }

    private CfnInternetGateway createIGW() {
        // Create an IGW for the VPC
        return new CfnInternetGateway(this, "nw-app-ota-demo-igw",
                CfnInternetGatewayProps.builder().build());
    }

    private CfnVPC createVPC(CfnInternetGateway igw) {
        // Create a VPC for the subnet
        CfnVPC vpc = new CfnVPC(this, "nw-app-ota-demp-vpc", CfnVPCProps.builder()
                .cidrBlock("192.168.1.0/24")
                .enableDnsHostnames(true)
                .enableDnsSupport(true)
                .enableDnsSupport(true)
                .build());

        new CfnVPCGatewayAttachment(this, "nw-app-ota-demo-igw2vpc", CfnVPCGatewayAttachmentProps.builder()
                .internetGatewayId(igw.getRef())
                .vpcId(vpc.getRef())
                .build());

        return vpc;
    }

    private CfnSubnet createSubnet(CfnVPC vpc, CfnInternetGateway igw) {
        // Create a Subnet for the EC2 instance
        CfnSubnet subnet = new CfnSubnet(this, "nw-app-ota-demo-subnet", CfnSubnetProps.builder()
                .cidrBlock("192.168.1.0/24")
                .mapPublicIpOnLaunch(true)
                .vpcId(vpc.getRef())
                .build());

        CfnRouteTable routeTable = new CfnRouteTable(this, "nw-app-ota-demo-route-table", CfnRouteTableProps.builder()
                .vpcId(vpc.getRef())
                .build());

        new CfnRoute(this, "nw-app-ota-demo-route-igw", CfnRouteProps.builder()
                .destinationCidrBlock("0.0.0.0/0")
                .gatewayId(igw.getRef())
                .routeTableId(routeTable.getRef())
                .build());

        new CfnSubnetRouteTableAssociation(this, "nw-app-ota-demo-route-table4subnet",
                CfnSubnetRouteTableAssociationProps.builder()
                        .routeTableId(routeTable.getRef())
                        .subnetId(subnet.getRef())
                        .build());

        return subnet;
    }

    private CfnSecurityGroup createSecurityGroup(CfnVPC vpc) {
        List<Object> ingressRules = new ArrayList<>();

        if (this.ec2KeyName != null) {
            CfnSecurityGroup.IngressProperty ingressProperty = CfnSecurityGroup.IngressProperty.builder()
                    .cidrIp("0.0.0.0/0")
                    .ipProtocol("TCP")
                    .fromPort(22)
                    .toPort(22)  // open SSH
                    .build();
            ingressRules.add(ingressProperty);
        }

        return new CfnSecurityGroup(this, "nw-app-ota-demo-sg", CfnSecurityGroupProps.builder()
                .groupName("nw-app-ota-demo-sg")
                .groupDescription("Nights Watch App OTA demo security group.")
                .vpcId(vpc.getRef())
                .securityGroupIngress(ingressRules)
                .build());
    }

    private void createEC2Device(CfnSubnet subnet, CfnSecurityGroup sg) {
        IMachineImage image;

        if (this.ec2ImageID == null) {
            Map<String, List<String>> filters = new HashMap<>();
            filters.put("architecture", Collections.singletonList("x86_64"));
            filters.put("image-type", Collections.singletonList("machine"));
            filters.put("is-public", Collections.singletonList("true"));
            filters.put("state", Collections.singletonList("available"));
            filters.put("virtualization-type", Collections.singletonList("hvm"));

            image = LookupMachineImage.Builder.create()
                    .name("*ubuntu-bionic-18.04-amd64-server-*")
                    .windows(false)
                    // in order to use the image in the AWS Marketplace product,
                    // user needs to accept terms and subscribe.
                    // To prevent this additional action, we use amazon built-in image only here.
                    .owners(Collections.singletonList("amazon"))
                    .filters(filters)
                    .build();
        } else {
            Map<String, String> filters = new HashMap<>();
            filters.put(this.getRegion(), this.ec2ImageID);

            image = GenericLinuxImage.Builder.create(filters).build();
        }

        String cmd = String.format("#!/bin/bash\n" +
                        "sudo apt update\n" +
                        "sudo DEBIAN_FRONTEND=noninteractive apt install -y unzip\n" +
                        "curl -o /tmp/setup.py -fs '%s'\n" +
                        "python3 /tmp/setup.py\n",
                this.ec2SetupScriptURL);
        cmd = new String(Base64.encodeBase64(cmd.getBytes()));

        CfnInstance instance = new CfnInstance(this, "nw-app-ota-demo-ec2-device", CfnInstanceProps.builder()
                .imageId(image.getImage(this).getImageId())
                .instanceType("t2.small")
                .subnetId(subnet.getRef())
                .securityGroupIds(Collections.singletonList(sg.getRef()))
                .keyName(this.ec2KeyName)
                .tags(Collections.singletonList(CfnTag.builder().key("Name").value("nw-app-ota-demo-device").build()))
                .userData(cmd)
                .build());

        new CfnOutput(this, "ec2-device-id", CfnOutputProps.builder()
                .value(instance.getRef())
                .description("the EC2 instance ID as IoT device for NW app OTA demo")
                .build());

        new CfnOutput(this, "ec2-public-ip", CfnOutputProps.builder()
                .value(instance.getAttrPublicIp())
                .description("the EC2 instance public IP as IoT device for NW app OTA demo")
                .build());
    }
}
