package com.amazonaws.rp.nightswatch.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class NWBuilderApp {
    private static Logger log = LoggerFactory.getLogger("nightswatch-builder");

    private static final String appOTADemoIoTStackName = "nightswatch-app-ota-demo-iot";

    private static final String appOTADemoDeviceStackName = "nightswatch-app-ota-demo-dev";

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
            new AppOTADemoIoTStack(cdkApp, appOTADemoIoTStackName, props);
            new AppOTADemoDeviceStack(cdkApp, appOTADemoDeviceStackName, props);

            // required until https://github.com/awslabs/jsii/issues/456 is resolve
            cdkApp.synth();
        } else if ("app-ota-demo".equals(argv[0])) {
            if (argv.length == 2 && "prepare-asset".equals(argv[1])) {
                new AppOTADemoAssert().provision(appOTADemoIoTStackName);
            } else if (argv.length == 2 && "cleanup-asset".equals(argv[1])) {
                new AppOTADemoAssert().deProvision(appOTADemoIoTStackName);
            } else {
                log.error("invalid demo command");
            }
        } else {
            log.error("invalid parameter, refer document");
            System.exit(2);
        }
    }
}
