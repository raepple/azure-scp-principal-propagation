package com.microsoft.samples;

import com.sap.cloud.sdk.cloudplatform.ScpCfCloudPlatform;

public abstract class Constants {
    public static final String AAD_DEMOAPP_CLIENT_ID = ScpCfCloudPlatform.getInstanceOrThrow().getEnvironmentVariable("AAD_DEMOAPP_CLIENT_ID").getOrElse("");
    public static final String AAD_IASAPP_CLIENT_ID = ScpCfCloudPlatform.getInstanceOrThrow().getEnvironmentVariable("AAD_IASAPP_CLIENT_ID").getOrElse("");
    public static final String CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    public static final String JWT_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    public static final String CLIENT_CREDENTIALS_GRANT_TYPE = "client_credentials";
    public static final String TOKEN_USE_OBO = "on_behalf_of";
    public static final String MSFT_GRAPH_DEFAULT_SCOPE = "https://graph.microsoft.com/.default";
}
