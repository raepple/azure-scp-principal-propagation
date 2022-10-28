package com.microsoft.samples;

import java.io.IOException;
import java.time.LocalDateTime;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.graph.models.Event;
import com.microsoft.graph.requests.EventCollectionPage;
import com.microsoft.graph.requests.EventCollectionRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import com.sap.cloud.sdk.cloudplatform.security.AuthToken;
import com.sap.cloud.sdk.cloudplatform.security.AuthTokenAccessor;

import okhttp3.Request;

@WebServlet("/calendar")
public class CalendarServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(CalendarServlet.class);

    private static final String AAD_TOKEN_ENDPOINT_DESTINATION = "aadTokenEndpoint";
    private static final String IAS_TOKEN_ENDPOINT_DESTINATION = "iasTokenEndpoint";
    private static final String IAS_TOKEN_EXCHANGE_DESTINATION = "iasTokenExchange";
    
    private static final String AAD_DEMOAPP_DEFAULT_SCOPE = "api://" + Constants.AAD_DEMOAPP_CLIENT_ID + "/.default";
    
    @Override
    protected void doGet( final HttpServletRequest request, final HttpServletResponse response )
        throws IOException
    {    
        logger.debug("Calendar servlet called");

        AuthToken iasToken = AuthTokenAccessor.getCurrentToken();

        String clientAssertion = TokenRequestHelper.requestAADFederatedClientCredentialViaOIDCProxy(IAS_TOKEN_ENDPOINT_DESTINATION);   
        String aadIASAccessToken = TokenRequestHelper.exchangeIASTokenViaOIDCProxy(IAS_TOKEN_EXCHANGE_DESTINATION, iasToken);
        String aadDemoAppAccessToken = TokenRequestHelper.exchangeAADTokenWithFederatedClientCredential(AAD_TOKEN_ENDPOINT_DESTINATION, aadIASAccessToken, clientAssertion, Constants.AAD_IASAPP_CLIENT_ID, AAD_DEMOAPP_DEFAULT_SCOPE);
        String graphTokenAccessToken = TokenRequestHelper.exchangeAADTokenWithFederatedClientCredential(AAD_TOKEN_ENDPOINT_DESTINATION, aadDemoAppAccessToken, clientAssertion, Constants.AAD_DEMOAPP_CLIENT_ID, Constants.MSFT_GRAPH_DEFAULT_SCOPE);
        GraphServiceClient<Request> graphClient = TokenRequestHelper.getGraphClient(graphTokenAccessToken);
        EventCollectionPage eventsPage = graphClient.me().calendar().events().buildRequest().get();
        JSONObject events = new JSONObject();
        events.put("name", "events");
        JSONArray eventsArray = new JSONArray();
        while(eventsPage != null) {
            for (Event event: eventsPage.getCurrentPage()) {
                JSONObject eventJSON = new JSONObject();
                eventJSON.put("subject", event.subject);
                eventJSON.put("organizer", event.organizer.emailAddress.address);
                eventJSON.put("start", String.format("%s %s", LocalDateTime.parse(event.start.dateTime).toLocalDate().toString(), LocalDateTime.parse(event.start.dateTime).toLocalTime().toString()));
                eventJSON.put("end", String.format("%s %s", LocalDateTime.parse(event.end.dateTime).toLocalDate().toString(), LocalDateTime.parse(event.end.dateTime).toLocalTime().toString()));
                eventsArray.put(eventJSON);
            }
            final EventCollectionRequestBuilder nextPage = eventsPage.getNextPage();
            if (nextPage == null) {
              break;
            }
        }
        response.getWriter().append(eventsArray.toString());
    }
}
