package com.amazonaws.rp.nightswatch.builder.appota;

import com.amazonaws.rp.nightswatch.builder.utils.StackOutputQuerier;
import com.amazonaws.services.iot.AWSIot;
import com.amazonaws.services.iot.AWSIotClientBuilder;
import com.amazonaws.services.iot.model.DescribeEndpointRequest;
import com.amazonaws.services.iot.model.DescribeEndpointResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AppOTADemoService {
    private final Logger log = LoggerFactory.getLogger("nightswatch-app-ota-demo-app");
    private final StackOutputQuerier outputQuerier = new StackOutputQuerier();

    public void queryEndpoint(final String appOTADemoIoTStackName) throws IOException {
        String thingName = this.outputQuerier.query(this.log, appOTADemoIoTStackName, "thingname");
        if (thingName == null)
            throw new IllegalArgumentException(String.format(
                    "the name of IoT device not found, is the NW app OTA demo stack %s invalid?",
                    appOTADemoIoTStackName));

        DescribeEndpointRequest req = new DescribeEndpointRequest();
        req.setEndpointType("iot:Data-ATS");

        AWSIot client = AWSIotClientBuilder.defaultClient();
        DescribeEndpointResult result = client.describeEndpoint(req);
        String endpoint = result.getEndpointAddress();

        System.out.println();
        System.out.println("Outputs:");
        System.out.println(String.format("MQTT service endpoint:\n\t%s", endpoint));
    }
}
