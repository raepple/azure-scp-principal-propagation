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
      
    private static final String AAD_DEMOAPP_DEFAULT_SCOPE = "api://" + Constants.AAD_DEMOAPP_CLIENT_ID + "/.default";
    
    @Override
    protected void doGet( final HttpServletRequest request, final HttpServletResponse response )
        throws IOException
    {
        logger.info("Invoking Debug servlet");
   
        response.setContentType("text/plain");       
        AuthToken iasToken = AuthTokenAccessor.getCurrentToken();
        
        response.getWriter().append("Hello " + iasToken.getJwt().getClaim("email").asString());
        
        response.getWriter().append("\nBTP app Client ID: " + Constants.AAD_DEMOAPP_CLIENT_ID );
        response.getWriter().append("\nIAS app Client ID: " + Constants.AAD_IASAPP_CLIENT_ID);
        
        response.getWriter().append("\n\nUser IAS Token: " + iasToken.getJwt().getToken());

        String clientAssertion = TokenRequestHelper.requestAADFederatedClientCredentialViaOIDCProxy(Constants.IAS_TOKEN_ENDPOINT_DESTINATION);
        response.getWriter().append("\n\nClient Assertion from Azure AD:\n" + clientAssertion);
   
        String aadIASAccessToken = TokenRequestHelper.exchangeIASTokenViaOIDCProxy(Constants.IAS_TOKEN_EXCHANGE_DESTINATION, iasToken);
        response.getWriter().append("\n\nAzure AD access token for IAS application:\n" + aadIASAccessToken);

        String aadDemoAppAccessToken = TokenRequestHelper.exchangeAADTokenWithFederatedClientCredential(Constants.AAD_TOKEN_ENDPOINT_DESTINATION, aadIASAccessToken, clientAssertion, Constants.AAD_IASAPP_CLIENT_ID, AAD_DEMOAPP_DEFAULT_SCOPE);
        response.getWriter().append("\n\nAzure AD access token for Demo application:\n" + aadDemoAppAccessToken);

        String graphTokenAccessToken = TokenRequestHelper.exchangeAADTokenWithFederatedClientCredential(Constants.AAD_TOKEN_ENDPOINT_DESTINATION, aadDemoAppAccessToken, clientAssertion, Constants.AAD_DEMOAPP_CLIENT_ID, Constants.MSFT_GRAPH_DEFAULT_SCOPE);
        response.getWriter().append("\n\nGraph access token for Demo application:\n" + graphTokenAccessToken);       
    }
}