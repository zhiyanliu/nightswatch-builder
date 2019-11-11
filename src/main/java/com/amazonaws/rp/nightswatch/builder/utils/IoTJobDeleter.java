package com.amazonaws.rp.nightswatch.builder.utils;

import com.amazonaws.services.iot.AWSIot;
import com.amazonaws.services.iot.AWSIotClientBuilder;
import com.amazonaws.services.iot.model.DeleteJobRequest;
import com.amazonaws.services.iot.model.DescribeJobRequest;
import com.amazonaws.services.iot.model.ResourceNotFoundException;
import org.slf4j.Logger;

public class IoTJobDeleter {
    public void delete(final Logger log, final String... JobIDs) {
        AWSIot client = AWSIotClientBuilder.defaultClient();

        for (String jobID : JobIDs) {
            try {
                // delete existing job
                DeleteJobRequest req1 = new DeleteJobRequest();
                req1.setForce(true);
                req1.setJobId(jobID);

                client.deleteJob(req1);

                log.debug(String.format("waiting the thing job %s is deleted ...", jobID));

                try {
                    DescribeJobRequest req2 = new DescribeJobRequest();
                    while (true) {
                        req2.setJobId(jobID);
                        client.describeJob(req2);
                    }
                } catch (ResourceNotFoundException e) {
                    // job is completed deleted
                }
            } catch (ResourceNotFoundException e) {
                // job is not existing
            }
        }
    }
}
