package com.amazonaws.rp.nightswatch.builder.appota;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.ec2.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppOTADemoDeviceStack extends Stack {
    private final Logger log = LoggerFactory.getLogger("nightswatch-app-ota-demo-device-stack");

    private final String ec2ImageID;
    private final String ec2Flavor;
    private final String ec2KeyName;
    private final String ec2SetupScriptURLBase64;

    public AppOTADemoDeviceStack(final Construct parent, final String id) {
        this(parent, id, null);
    }

    public AppOTADemoDeviceStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

        Object ec2DeviceImageIDObj = this.getNode().tryGetContext("ec2-image-id");
        if (ec2DeviceImageIDObj == null)
            this.ec2ImageID = "ami-0cd744adeca97abb1"; // Ubuntu 18.04lts x64, in ap-northeast-1 region
        else
            this.ec2ImageID = ec2DeviceImageIDObj.toString();

        Object ec2FlavorObj = this.getNode().tryGetContext("ec2-instance-type");
        if (ec2FlavorObj == null)
            this.ec2Flavor = "t2.small";
        else
            this.ec2Flavor = ec2FlavorObj.toString();

        Object ec2KeyNameObj = this.getNode().tryGetContext("ec2-key-name");
        if (ec2KeyNameObj == null)
            this.ec2KeyName = null;
        else
            this.ec2KeyName = ec2KeyNameObj.toString();

        Object ec2SetupScriptURLObj = this.getNode().tryGetContext("ec2-setup-script-url-base64");
        if (ec2SetupScriptURLObj == null) {
            this.ec2SetupScriptURLBase64 = null;
        } else {
            String s = ec2SetupScriptURLObj.toString();
            if (!Base64.isBase64(s))
                throw new IllegalArgumentException(
                        "parameter ec2-setup-script-url-base64 is not a valid base64 string");
            this.ec2SetupScriptURLBase64 = s;
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
        List<Object> ingressRules = new ArrayList<>();

        if (this.ec2KeyName != null) {
            CfnSecurityGroup.IngressProperty ingressProperty = CfnSecurityGroup.IngressProperty.builder()
                    .withCidrIp("0.0.0.0/0")
                    .withIpProtocol("TCP")
                    .withFromPort(22)
                    .withToPort(22)  // open SSH
                    .build();
            ingressRules.add(ingressProperty);
        }

        return new CfnSecurityGroup(this, "nw-app-ota-demo-sg", CfnSecurityGroupProps.builder()
                .withGroupName("nw-app-ota-demo-sg")
                .withGroupDescription("Nights Watch App OTA demo security group.")
                .withVpcId(vpc.getRef())
                .withSecurityGroupIngress(ingressRules)
                .build());
    }

    private void createEC2Device(CfnSubnet subnet, CfnSecurityGroup sg) {
        String cmd = null;

        if (this.ec2SetupScriptURLBase64 != null) {
            String ec2SetupScriptURL = new String(Base64.decodeBase64(this.ec2SetupScriptURLBase64));
            cmd = String.format("#!/bin/bash\n" +
                            "sudo apt install -y unzip\n" +
                            "curl -o /tmp/setup.py -fs '%s'\n" +
                            "python3 /tmp/setup.py\n",
                    ec2SetupScriptURL);
            cmd = new String(Base64.encodeBase64(cmd.getBytes()));
        }

        CfnInstance instance = new CfnInstance(this, "nw-app-ota-demo-ec2-device", CfnInstanceProps.builder()
                .withImageId(this.ec2ImageID)
                .withInstanceType(this.ec2Flavor)
                .withSubnetId(subnet.getRef())
                .withSecurityGroupIds(Arrays.asList(sg.getRef()))
                .withKeyName(this.ec2KeyName)
                .withTags(Arrays.asList(CfnTag.builder().withKey("Name").withValue("nw-app-ota-demo-device").build()))
                .withUserData(cmd)
                .build());

        new CfnOutput(this, "ec2-device-id", CfnOutputProps.builder()
                .withValue(instance.getRef())
                .withDescription("the EC2 instance ID as IoT device for NW app OTA demo")
                .build());

        new CfnOutput(this, "ec2-public-ip", CfnOutputProps.builder()
                .withValue(instance.getAttrPublicIp())
                .withDescription("the EC2 instance public IP as IoT device for NW app OTA demo")
                .build());
    }
}
