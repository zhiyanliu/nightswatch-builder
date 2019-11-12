package com.amazonaws.rp.nightswatch.builder.appota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class NWBuilderApp {
    private static final Logger log = LoggerFactory.getLogger("nightswatch-builder");

    private static final String APP_OTA_DEMO_IOT_STACK_NAME = "nightswatch-app-ota-demo-iot";
    private static final String APP_OTA_DEMO_DEVICE_STACK_NAME = "nightswatch-app-ota-demo-dev";

    public static void main(final String[] argv) throws Exception {
        if (argv.length == 0) {
            App cdkApp = new App();

            // `cdk deploy` follows region config provide by AWSSDK (`~/.aws/config`)
            // `cdk deploy -c KEY=VALUE (array)` to add/overwrite context.
            Object regionObj = cdkApp.getNode().tryGetContext("region");
            String region = null;
            if (regionObj != null)
                region = regionObj.toString();

            StackProps props = StackProps.builder()
                    .withEnv(Environment.builder().withRegion(region).build())
                    .build();
            new AppOTADemoIoTStack(cdkApp, APP_OTA_DEMO_IOT_STACK_NAME, props);
            new AppOTADemoDeviceStack(cdkApp, APP_OTA_DEMO_DEVICE_STACK_NAME, props);

            // required until https://github.com/awslabs/jsii/issues/456 is resolve
            cdkApp.synth();
        } else if ("app-ota-demo".equals(argv[0])) {
            try {
                if (argv.length == 2 && "service-endpoint".equals(argv[1])) {
                    new AppOTADemoService().queryEndpoint(APP_OTA_DEMO_IOT_STACK_NAME);
                } else if (argv.length == 2 && "prepare-asset".equals(argv[1])) {
                    new AppOTADemoAssert().provision(APP_OTA_DEMO_IOT_STACK_NAME);
                } else if (argv.length == 2 && "cleanup-asset".equals(argv[1])) {
                    new AppOTADemoAssert().deProvision(APP_OTA_DEMO_IOT_STACK_NAME);
                } else if (argv.length == 2 && "prepare-app-v1".equals(argv[1])) {
                    new AppOTADemoApplication().provisionV1(APP_OTA_DEMO_IOT_STACK_NAME, "x64", "containerized");
                } else if (argv.length == 2 && "prepare-app-v2".equals(argv[1])) {
                    new AppOTADemoApplication().provisionV2(APP_OTA_DEMO_IOT_STACK_NAME, "x64", "containerized");
                } else if (argv.length == 2 && "prepare-native-app-v1".equals(argv[1])) {
                    new AppOTADemoApplication().provisionV1(APP_OTA_DEMO_IOT_STACK_NAME, "x64", "native");
                } else if (argv.length == 2 && "prepare-native-app-v2".equals(argv[1])) {
                    new AppOTADemoApplication().provisionV2(APP_OTA_DEMO_IOT_STACK_NAME, "x64", "native");
                } else {
                    log.error("invalid demo command");
                }
            } catch (Exception e) {
                log.error(e.getMessage());
                System.exit(255);
            }
        } else {
            log.error("invalid parameter, refer document");
            System.exit(2);
        }
    }
}
