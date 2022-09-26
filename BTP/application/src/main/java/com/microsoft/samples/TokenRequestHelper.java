package com.microsoft.samples;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.requests.GraphServiceClient;
import com.sap.cloud.sdk.cloudplatform.ScpCfCloudPlatform;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpClientAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import com.sap.cloud.sdk.cloudplatform.security.AuthToken;

import okhttp3.Request;

public class TokenRequestHelper {
    private static final Logger logger = LoggerFactory.getLogger(TokenRequestHelper.class);

    private static final String CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final String JWT_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final String CLIENT_CREDENTIALS_GRANT_TYPE = "client_credentials";
    private static final String TOKEN_USE_OBO = "on_behalf_of";
    private static final String RESPONSE_TYPE_TOKEN = "token";

    public TokenRequestHelper() {
        ServiceLoader.load(IASTokenHeaderProvider.class);
    }

    public static String exchangeIASTokenViaOIDCProxy(String iasTokenEndpointDestinationName,
            AuthToken encodedBTPIASIDToken) throws ClientProtocolException, IOException {
        HttpDestination iasTokenExchangeDest = DestinationAccessor.getDestination(iasTokenEndpointDestinationName)
                .asHttp();
        HttpClient client = HttpClientAccessor.getHttpClient(iasTokenExchangeDest);
        URI iasTokenExchangeUri = iasTokenExchangeDest.getUri();
        HttpPost aadIASTokenRequest = new HttpPost(iasTokenExchangeUri);
        List<NameValuePair> params = new ArrayList<NameValuePair>();

        JsonObject iasCredentials = ScpCfCloudPlatform.getInstanceOrThrow().getServiceCredentials("identity");
        String iasClientId = iasCredentials.get("clientid").getAsString();
        
        params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("assertion", encodedBTPIASIDToken.getJwt().getToken()));
        params.add(new BasicNameValuePair("client_id", iasClientId));
        params.add(new BasicNameValuePair("response_type", RESPONSE_TYPE_TOKEN));
        params.add(new BasicNameValuePair("scope", "api://7da45caf-51ea-412b-acf7-cc600e11e193/tokenexchange"));
        aadIASTokenRequest.setEntity(new UrlEncodedFormEntity(params));
        HttpResponse aadIASTokenResponse = client.execute(aadIASTokenRequest);
        logger.debug("Response code from AAD IAS (app) Token Exchange request: {}",
                aadIASTokenResponse.getStatusLine().getStatusCode());
        String body = IOUtils.toString(aadIASTokenResponse.getEntity().getContent(), "UTF-8");
        logger.debug("Response body: {}", body);
        String aadIASAccessToken = getAccessToken(body);
        return aadIASAccessToken;
    }

    public static String exchangeAADTokenWithFederatedClientCredential(String aadTokenEndpointDestinationName,
            String aadUserAccessToken, String federatedClientCredential, String clientId, String scope)
            throws ClientProtocolException, IOException {
        HttpDestination aadTokenEndpointDest = DestinationAccessor.getDestination(aadTokenEndpointDestinationName)
                .asHttp();
        HttpClient client = HttpClientAccessor.getHttpClient(aadTokenEndpointDest);
        URI aadTokenEndpointUri = aadTokenEndpointDest.getUri();
        HttpPost aadTokenRequest = new HttpPost(aadTokenEndpointUri);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("assertion", aadUserAccessToken));
        params.add(new BasicNameValuePair("client_assertion", federatedClientCredential));
        params.add(new BasicNameValuePair("client_assertion_type", CLIENT_ASSERTION_TYPE));
        params.add(new BasicNameValuePair("grant_type", JWT_GRANT_TYPE));
        params.add(new BasicNameValuePair("client_id", clientId));
        params.add(new BasicNameValuePair("scope", scope));
        params.add(new BasicNameValuePair("requested_token_use", TOKEN_USE_OBO));

        aadTokenRequest.setEntity(new UrlEncodedFormEntity(params));
        HttpResponse aadTokenResponse = client.execute(aadTokenRequest);
        logger.debug("Response code from AAD token exchange request: {}",
                aadTokenResponse.getStatusLine().getStatusCode());
        String body = IOUtils.toString(aadTokenResponse.getEntity().getContent(), "UTF-8");
        logger.debug("Response body: {}", body);
        String newAADAccessToken = getAccessToken(body);
        return newAADAccessToken;
    }

    public static String requestAADFederatedClientCredentialViaOIDCProxy(String iasTokenEndpointDestinationName)
            throws ClientProtocolException, IOException {
        HttpDestination aadTokenEndpointDest = DestinationAccessor.getDestination(iasTokenEndpointDestinationName)
                .asHttp();
        HttpClient client = HttpClientAccessor.getHttpClient(aadTokenEndpointDest);
        URI aadTokenEndpointUri = aadTokenEndpointDest.getUri();
        HttpPost aadTokenRequest = new HttpPost(aadTokenEndpointUri);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("grant_type", CLIENT_CREDENTIALS_GRANT_TYPE));
        params.add(new BasicNameValuePair("resource", "urn:sap:identity:corporateidp"));
        aadTokenRequest.setEntity(new UrlEncodedFormEntity(params));
        HttpResponse iasClientAssertionResponse = client.execute(aadTokenRequest);
        logger.debug("Response code from Client Assertion request: {}",
                iasClientAssertionResponse.getStatusLine().getStatusCode());
        String body = IOUtils.toString(iasClientAssertionResponse.getEntity().getContent(), "UTF-8");
        logger.debug("Response body: {}", body);
        String clientAssertion = getAccessToken(body);
        return clientAssertion;
    }

    public static String decodePayload(String encodedToken) throws UnsupportedEncodingException {
        return new String(Base64.getUrlDecoder().decode(encodedToken.split("\\.")[1]), "UTF-8");
    }

    public static GraphServiceClient<Request> getGraphClient(String graphAccessToken) {
        IAuthenticationProvider authProvider = new IAuthenticationProvider() {
            @Override
            public CompletableFuture<String> getAuthorizationTokenAsync(@NotNull URL requestUrl) {
                CompletableFuture<String> alreadyThere = new CompletableFuture<>();
                alreadyThere.complete(graphAccessToken);
                return alreadyThere;
            }
        };
        
        GraphServiceClient<Request> graphClient = GraphServiceClient
                .builder()
                .authenticationProvider(authProvider)
                .buildClient();

        return graphClient;
    }

    private static String getAccessToken(String jsonResponse) {
        JsonElement clientAssertionJson = JsonParser.parseString(jsonResponse);
        String accessToken = clientAssertionJson.getAsJsonObject().get("access_token").getAsString();
        return accessToken;
    }

}
