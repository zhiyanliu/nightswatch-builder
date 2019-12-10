This document means to give you a guide to produce an easy-to-show demonstration about application OTA based on Night's Watch project.

## 0. Pre-condition

1. Match the requirements listed in README [limit](http://git.awsrun.com/rp/nightswatch-builder#limit) section.
2. You need a local laptop/PC as the client to run AWS CLI command with your AWS credentials as well as certain rights.
3. You need a local laptop/PC as the client to run Night's Watch - Build program with your AWS credentials as well as certain rights.

>>**Note:**
>>
>> Night's Watch - Build does not require user input any AWS credentials, instead, the default configuration and credentials will be loaded from ``~/.aws/config`` and ``~/.aws/credentials`` automatically, , you can configure them by command ``aws configure``.

## 1. Deploy IoT core stack

>>**Note:**
>>
>> All `cdk` and `java` command listed in this guide need you to change current working directory to Night's Watch - Builder code repository directory first.

- Cleanup last context of CDK

    - ``cdk context --clear``

- Provision IoT core stack

    - ``cdk deploy nightswatch-app-ota-demo-iot``

- Prepare Night's Watch - Ranger package

    - ``java -jar target/nightswatch-builder-1.0-SNAPSHOT-jar-with-dependencies.jar app-ota-demo service-endpoint``
    - You will get `IoT service endpoint` output by above step, update `AWS_IOT_MQTT_HOST` value in [`aws_iot_config.h`](http://git.awsrun.com/rp/nightswatch-ranger/blob/master/aws_iot_config.h#L15) in Night's Watch - Ranger code repository follow [this step](http://git.awsrun.com/rp/nightswatch-ranger#device-client-parameter-configuration).
    - Build Night's Watch - Ranger follow [this step](http://git.awsrun.com/rp/nightswatch-ranger#basic) on a x64 architecture host.
    - Organize Night's Watch - Ranger deployment directory by [this step](http://git.awsrun.com/rp/nightswatch-ranger#deployment-directory-structure). Note, you need to name the deployment directory `<NIGHTS_WATCH_RANGER_HOME_DIR>` to `nightswatch-ranger`.
    - Package Night's Watch - Ranger deployment by command ``tar czf nightswatch-ranger.tar.gz nightswatch-ranger``.
    - Save the tar ball to `src/main/resources/nightswatch-ranger_x64` directory.

- Re-build Night's Watch - Builder to update inline resource

    - ``mvn package``

- Prepare demo asset

    - ``java -jar target/nightswatch-builder-1.0-SNAPSHOT-jar-with-dependencies.jar app-ota-demo prepare-asset``

## 2. Create fake IoT device for demo if you have no an own device (optional)

>>**Note:**
>>
>> You need an IoT thing to act the device to deploy the application and demo the OTA operation via Night's Watch - Ranger daemon program.
>>
>> Skip this step if you have a real one, you can get certificates and credentials in the S3 bucket (the bucket name is provided by output `nightswatch-app-ota-demo-iot.devfilesbucketname` after the stack deployment), then deploy and run Night's Watch - Ranger by yourself.
>>
>> If you do not have a x64 architecture device (current built-in demo application and containerization facility is x64 architecture), you can follow this step to deploy an EC2 instance to act the IoT device easily, Night's Watch - Builder will automatically deploy and configure Ranger for you.

- ``cdk deploy nightswatch-app-ota-demo-dev [-c ec2-key-name=<key-pair-name>] [-c ec2-image-id=<ec2-image-id>]``

    - Use `ec2-image-id` optional parameter in above command to provide AMI ID to provision EC2 instance using an Ubuntu 18.04lts x64 operation system in your region, e.g. ID  `ami-0cd744adeca97abb1` can be used for region `ap-northeast-1`. CDK will lookup an Amazon official AMI contains Ubuntu 18.04lts x64 for your by default.
    - Use `key-pair-name` optional parameter in above command to provide SSH key pair name to inject the public key to the EC2 instance, if you would like to use `ssh` login it, to debug or check log for example.

## 3. Execute Application deployment and update job

- For containerized application deployment and update:

    - ``java -jar target/nightswatch-builder-1.0-SNAPSHOT-jar-with-dependencies.jar app-ota-demo prepare-app-v1``
    - Execute ``aws iot create-job`` command provided by output `application deployment command line` from above step. This job is used to deploy application version 1.
    - ``java -jar target/nightswatch-builder-1.0-SNAPSHOT-jar-with-dependencies.jar app-ota-demo prepare-app-v2``
    - Execute ``aws iot create-job`` command provided by output `application deployment command line` from above step. This job is used to deploy application version 2.

- For non-containerized application deployment and update:

    - ``java -jar target/nightswatch-builder-1.0-SNAPSHOT-jar-with-dependencies.jar app-ota-demo prepare-native-app-v1``
    - Execute ``aws iot create-job`` command provided by output `application deployment command line` from above step. This job is used to deploy application version 1.
    - ``java -jar target/nightswatch-builder-1.0-SNAPSHOT-jar-with-dependencies.jar app-ota-demo prepare-native-app-v2``
    - Execute ``aws iot create-job`` command provided by output `application deployment command line` from above step. This job is used to deploy application version 2.

>>**The different between application version 1 and 2:**
>>
>> Application version 1 outputs data `*,*,red` to the MQTT topic `/qbr/demo/lcd` periodically.
>>
>> Application version 2 outputs data `*,*,yello` to the MQTT topic `/qbr/demo/lcd` periodically.
>>
>> Additional, you can subscribe the MQTT topic `nw/apps/app_xxx/event` and `nw/apps/app_xxx/log` to monitor application's common resource usage indicators and `stdout` `stderr` outputs.
>
>>**Application source code:**
>>
>> As the attachment for you reference, the source code have been updated to [here](http://git.awsrun.com/rp/nightswatch-builder/blob/master/demo/app_xxx_src).
>> The `rootfs` of the container is not included.
>
>>**Note:**
>>
>> You can use follow commands to describe application deployment and update progress in detail:
>> - ``aws iot describe-job-execution --job-id nw-app-ota-demo-deploy-app-v1 --thing-name nw-app-ota-demo-dev``
>> - ``aws iot describe-job-execution --job-id nw-app-ota-demo-deploy-app-v2 --thing-name nw-app-ota-demo-dev``

## -3. Clean demo asset up

- ``java -jar target/nightswatch-builder-1.0-SNAPSHOT-jar-with-dependencies.jar app-ota-demo cleanup-asset``

## -2. Delete demo IoT device if you created in step \#2

- ``cdk destroy nightswatch-app-ota-demo-dev``

## -1. Destroy IoT core stack

- ``cdk destroy nightswatch-app-ota-demo-iot``
