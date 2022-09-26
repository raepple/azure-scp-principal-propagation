# Description
These are the instructions to build and deploy the sample BTP application for the principal propagation scenario of part VI from the blog series.

# Deployment on Cloud Foundry
To deploy the application, the following steps are required:
- Compile the Java application
- Create the IAS service instance
- Create the destination service instance
- Deploy the application 

## Compile the Java application
Run maven to package the application
```shell
mvn clean package
```

## Create the identity service instance for obtaining the 
Use the ias service broker and create a service instance (don't forget to replace the placeholders)
```shell
cf create-service identity application ias-iasaaddemo
```

## Create the destination service instance
Use the destination service broker to create a service instance
```shell
cf create-service destination lite destination-iasaaddemo
```

## Deploy the application
Deploy the application using cf push. It will expect 1 GB of free memory quota.

```shell
cf push
```

## Appendix
### Enable remote debugging
```shell
cf enable-ssh iasaaddemo
cf restage iasaaddemo
cf ssh iasaaddemo -c "app/META-INF/.sap_java_buildpack/sapjvm/bin/jvmmon <<< 'start debugging'"
cf ssh iasaaddemo -N -T -L 8000:localhost:8000
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