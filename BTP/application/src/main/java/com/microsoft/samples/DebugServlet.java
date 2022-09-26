package com.microsoft.samples;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cloud.sdk.cloudplatform.security.AuthToken;
import com.sap.cloud.sdk.cloudplatform.security.AuthTokenAccessor;

@WebServlet("/debug")
public class DebugServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(DebugServlet.class);
   
    private static final String AAD_TOKEN_ENDPOINT_DESTINATION = "aadTokenEndpoint";
    private static final String IAS_TOKEN_ENDPOINT_DESTINATION = "iasTokenEndpoint";
    private static final String IAS_TOKEN_EXCHANGE_DESTINATION = "iasTokenExchange";
    
    private static final String AAD_DEMOAPP_CLIENT_ID = "7da45caf-51ea-412b-acf7-cc600e11e193";
    private static final String AAD_IASAPP_CLIENT_ID = "ae6efb1b-2eac-4fc0-8028-cf1a25bd43e2";
    private static final String AAD_DEMOAPP_DEFAULT_SCOPE = "api://" + AAD_DEMOAPP_CLIENT_ID + "/.default";
    private static final String MSFT_GRAPH_DEFAULT_SCOPE = "https://graph.microsoft.com/.default";

    @Override
    protected void doGet( final HttpServletRequest request, final HttpServletResponse response )
        throws IOException
    {
        logger.info("Invoking Debug servlet");
   
        response.setContentType("text/plain");       
        AuthToken iasToken = AuthTokenAccessor.getCurrentToken();
        
        response.getWriter().append("Hello " + iasToken.getJwt().getClaim("email").asString());
        response.getWriter().append("\nYour IAS Token: " + iasToken.getJwt().getToken());

        String clientAssertion = TokenRequestHelper.requestAADFederatedClientCredentialViaOIDCProxy(IAS_TOKEN_ENDPOINT_DESTINATION);
        response.getWriter().append("\n\nClient Assertion from Azure AD:\n" + clientAssertion);
   
        String aadIASAccessToken = TokenRequestHelper.exchangeIASTokenViaOIDCProxy(IAS_TOKEN_EXCHANGE_DESTINATION, iasToken);
        response.getWriter().append("\n\nAzure AD access token for IAS application:\n" + aadIASAccessToken);

        String aadDemoAppAccessToken = TokenRequestHelper.exchangeAADTokenWithFederatedClientCredential(AAD_TOKEN_ENDPOINT_DESTINATION, aadIASAccessToken, clientAssertion, AAD_IASAPP_CLIENT_ID, AAD_DEMOAPP_DEFAULT_SCOPE);
        response.getWriter().append("\n\nAzure AD access token for Demo application:\n" + aadDemoAppAccessToken);

        String graphTokenAccessToken = TokenRequestHelper.exchangeAADTokenWithFederatedClientCredential(AAD_TOKEN_ENDPOINT_DESTINATION, aadDemoAppAccessToken, clientAssertion, AAD_DEMOAPP_CLIENT_ID, MSFT_GRAPH_DEFAULT_SCOPE);
        response.getWriter().append("\n\nGraph access token for Demo application:\n" + graphTokenAccessToken);       
    }
}