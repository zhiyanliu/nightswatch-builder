package com.amazonaws.rp.nightswatch.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class NWBuilderApp {
    private static Logger log = LoggerFactory.getLogger("nightswatch-builder");

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
            new AppOTADemoIoTStack(cdkApp, "nightswatch-app-ota-demo-iot", props);
            new AppOTADemoDeviceStack(cdkApp, "nightswatch-app-ota-demo-dev", props);

            // required until https://github.com/awslabs/jsii/issues/456 is resolve
            cdkApp.synth();
        } else if ("app-ota-demo".equals(argv[0])) {
            if (argv.length >= 4 && "prepare-asset".equals(argv[1])) {
                String rootCAFileName = null;
                if (argv.length >= 5)
                    rootCAFileName = argv[4];
                new AppOTADemoAssert().provision(argv[2], argv[3], rootCAFileName);
            } else if (argv.length >= 5 && "cleanup-asset".equals(argv[1])) {
                new AppOTADemoAssert().deProvision(argv[2], argv[3], argv[4]);
            } else {
                log.error("invalid demo command");
            }
        } else {
            log.error("invalid parameter, refer document");
            System.exit(2);
        }
    }
}
