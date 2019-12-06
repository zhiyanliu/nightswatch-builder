package com.amazonaws.rp.nightswatch.builder.utils;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.*;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;

public class StackOutputQuerier {
    public String query(final Logger log, String stackName, String outputKey) throws AmazonCloudFormationException {
        AmazonCloudFormation client = AmazonCloudFormationClientBuilder.defaultClient();

        log.debug("connected to AWS CloudFormation service");

        ListStacksRequest listStacksReq = new ListStacksRequest();
        listStacksReq.setStackStatusFilters(Arrays.asList("CREATE_COMPLETE", "UPDATE_COMPLETE"));

        ListStacksResult listStacksResult = client.listStacks(listStacksReq);
        for (StackSummary stackSummary : listStacksResult.getStackSummaries()) {
            if (stackSummary.getStackName().equals(stackName)) {
                DescribeStacksRequest req = new DescribeStacksRequest();
                req.setStackName(stackName);

                DescribeStacksResult result = client.describeStacks(req);
                List<Stack> stacks = result.getStacks();

                List<Output> outputs = stacks.get(0).getOutputs();

                for (Output output : outputs) {
                    if (output.getOutputKey().equals(outputKey))
                        return output.getOutputValue();
                }
            }
        }

        return null;
    }
}
