## What is this

This is the code repository of the AWS RP IoT device [OTA](https://en.wikipedia.org/wiki/Over-the-air_programming) automatic stack deployment and demonstration control plane, developed by AWS Rapid Prototyping team. The code name of this component is [Night's Watch](https://gameofthrones.fandom.com/wiki/Night%27s_Watch) - [Builder](https://gameofthrones.fandom.com/wiki/Builder), which is the part of Night's Watch project. 

Mainly, and currently, Night's Watch - Builder provides two functions:

1. Automatize the IoT resource and demonstration stack deployment, powered by AWS CDK and CloudFormation service.
2. As a control plane to maximize the automation of the OTA demonstration. Supported demo listed as following:
    - Containerized and non-containerized application OTA

## Why [we](mailto:awscn-sa-prototyping@amazon.com) develop it

As the pair project of [Night's Watch - Range](http://git.awsrun.com/rp/nightswatch-ranger), we would like to automatize the IoT resources creation and configuration, more over, to easy the demonstration of Ranger supported OTA cases. We hope this project can assist SA to understand and use our reusable asset quickly and correctly.

>> **Note:**
>>
>> This project is truly under continuative develop stage, we'd like to collect the feedback and include the enhancement in follow-up release to share them with all users. 
>>
>> **DISCLAIMER: This project is NOT intended for a production environment, and USE AT OWN RISK!**  

## Limit

If you would like to try automatic IoT resource stack deployment, this project does not work for you if:

* Your device will not manged by AWS IoT Core service.
* You do not have a credential to access WW (non-China) AWS region.
* Your AWS account user has not right to fully access AWS IoT Core, CloudFormation or S3 service.
* You do not have a local laptop/PC runs MacOS, Ubuntu or Windows system, to install and run Apache Maven, Java, node, npm and AWS CDK.

Additional, if you would like to try demonstration, it will not work if:

* Your AWS account user has not right to fully access AWS EC2 and VPC service in WW and China AWS region.

>>**Preferred software version:**
>>
>> - Apache Maven: 3.5.4, or above
>> - Java: 13.0.1 2019-10-15 (build 13.0.1+9), or above
>> - node: v12.12.0, or above but less than 13.2.0 before the [issue](https://github.com/aws/aws-cdk/issues/5187) is fixed.
>> - npm: 6.13.0, or above
>> - CDK: 1.16.1 (build bdbe3aa), or above

## How to build

1. ``git clone git@git.awsrun.com:rp/nightswatch-builder.git`` or ``git clone http://git.awsrun.com/rp/nightswatch-builder.git``
2. ``cd nightswatch-builder``
3. ``mvn package``

## How to play demonstration

- Containerized and non-containerized application OTA play guide: [here](http://git.awsrun.com/rp/nightswatch-builder/blob/master/demo/app-ota.md)

## Key TODO plan:

- [ ] Add device certification OTA demo.
- [ ] Add device agent, Night's Watch - Ranger, OTA demo.

## Contributor

* Zhi Yan Liu, AWS Rapid Prototyping team,  [liuzhiya@amazon.com](mailto:liuzhiya@amazon.com)
* You. Welcome any feedback and issue report, further more, idea and code contribution are highly encouraged.
