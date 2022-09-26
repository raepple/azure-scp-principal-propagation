package com.microsoft.samples;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.sap.cloud.sdk.cloudplatform.ScpCfCloudPlatform;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationHeaderProvider;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationRequestContext;
import com.sap.cloud.sdk.cloudplatform.connectivity.Header;

import io.vavr.control.Option;

public class IASTokenHeaderProvider implements DestinationHeaderProvider {

    private static final Logger logger = LoggerFactory.getLogger(IASTokenHeaderProvider.class);

    @Nonnull
    @Override
    public List<Header> getHeaders( @Nonnull final DestinationRequestContext requestContext )
    {
        List<Header> headers = new ArrayList<>();
        Option<String> destinatioName = requestContext.getDestination().get("name", String.class);

        if (destinatioName.contains(new String("iasTokenEndpoint")) ||
            destinatioName.contains(new String("iasTokenExchange"))) {
            logger.debug("Adding authz header for IAS request");
            Header header = new Header("Authorization", obtainIASCredentials());    
            headers.add(header);
        }

        return headers;
    }   
    
    private String obtainIASCredentials()
    {
        JsonObject iasCredentials = ScpCfCloudPlatform.getInstanceOrThrow().getServiceCredentials("identity");
        String iasclientid = iasCredentials.get("clientid").getAsString();
        String iasclientSecret = iasCredentials.get("clientsecret").getAsString();       
        String basicCredentials = String.format("%s:%s", iasclientid, iasclientSecret);
        return String.format("Basic %s", Base64.getEncoder().encodeToString(basicCredentials.getBytes()));
    }
}
