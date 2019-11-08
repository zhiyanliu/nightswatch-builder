package com.amazonaws.rp.nightswatch.builder.utils;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.*;

import java.util.List;

public class StackOutputQuerier {
    private String queryStackOutput(String stackName, String outputKey) {
        AmazonCloudFormation client = AmazonCloudFormationClientBuilder.defaultClient();

        DescribeStacksRequest req = new DescribeStacksRequest();
        req.setStackName(stackName);

        try {
            DescribeStacksResult result = client.describeStacks(req);
            List<Stack> stacks = result.getStacks();

            List<Output> outputs = stacks.get(0).getOutputs();

            for (Output output : outputs) {
                if (output.getOutputKey().equals(outputKey))
                    return output.getOutputValue();
            }
        } catch (AmazonCloudFormationException e) {
            throw new IllegalArgumentException(
                    String.format("stack %s not found, deploy it first", stackName));
        }

        return null;
    }

    public String query(final String stackName, final String keyName) {
        return this.queryStackOutput(stackName, keyName);
    }
}
