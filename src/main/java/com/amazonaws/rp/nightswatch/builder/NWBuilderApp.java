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
            new AppOTADemoStack(cdkApp, "nightswatch-app-ota-demo", props);

            // required until https://github.com/awslabs/jsii/issues/456 is resolve
            cdkApp.synth();
        } else if (argv.length == 2) {
            if ("app-ota-demo".equals(argv[0])) {

            } else {
                log.error("invalid demo name");
            }
        } else {
            log.error("invalid parameter, refer document");
            System.exit(2);
        }
    }
}
