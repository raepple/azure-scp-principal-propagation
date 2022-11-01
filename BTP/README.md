# Description
These are the instructions to build and deploy the sample BTP application for the principal propagation scenario of part VI from the blog series.

# Prerequisites
- [Maven 3.x](http://maven.apache.org/download.cgi)
- JDK 8 or higher
- [Cloud Foundry CLI](https://github.com/cloudfoundry/cli)

# Deployment on Cloud Foundry
To deploy the application, the following steps are required:
- Compile the Java application
- Login to BTP
- Create the IAS service instance
- Create the destination service instance
- Deploy the application 

## Compile the Java application
Run maven to package the application
```shell
cd BTP
mvn clean package
```

## Login to BTP
```shell
cf login -a https://api.cf.<region>.hana.ondemand.com
```

## Create the identity service instance 
Use the ias service broker and create a service instance (don't forget to replace the placeholders)
```shell
cf create-service identity application ias-btpgraph
```
## Create the destination service instance
Use the destination service broker to create a service instance
```shell
cf create-service destination lite destination-btpgraph
```
## Login to BTP
Login to your BTP subaccount with the CF CLI
```shell
cf login -a <your regional API endpoint, e.g. https://api.cf.eu20.hana.ondemand.com>
```

## Deploy the application
Deploy the application using cf push. It will expect 1 GB of free memory quota.

```shell
cf push --vars-file ./vars.yml
```

## Appendix
### Enable remote debugging
```shell
cf enable-ssh btpgraph
cf restage btpgraph
cf ssh btpgraph -c "app/META-INF/.sap_java_buildpack/sapjvm/bin/jvmmon <<< 'start debugging'"
cf ssh btpgraph -N -T -L 8000:localhost:8000
```

### Connect your IDE to remote debugging endpoint
For Visual Studio Code, create a launch.json file with this content:
```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Launch Remote Debugging",
            "request": "attach",
            "port": 8000,
            "hostName": "localhost"
        }
    ]
}
```