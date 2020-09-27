package com.contoso.sample;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;

import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sap.cloud.sdk.cloudplatform.security.Authorization;
import com.sap.cloud.sdk.cloudplatform.security.principal.CollectionPrincipalAttribute;
import com.sap.cloud.sdk.cloudplatform.security.principal.DefaultPrincipal;
import com.sap.cloud.sdk.cloudplatform.security.principal.DefaultPrincipalFacade;
import com.sap.cloud.sdk.cloudplatform.security.principal.Principal;
import com.sap.cloud.sdk.cloudplatform.security.principal.PrincipalAttribute;
import com.sap.cloud.sdk.cloudplatform.security.principal.StringPrincipalAttribute;
import com.sap.cloud.sdk.odatav2.connectivity.ODataQueryBuilder;
import com.sap.cloud.sdk.odatav2.connectivity.ODataQueryResult;

import io.vavr.control.Try;

@WebServlet("/hello")
@ServletSecurity(@HttpConstraint(rolesAllowed = { "Display" }))
public class HelloWorldServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;
    private static final String DESTINATION_NAME = "npl_http";
    
    @Override
    protected void doGet( final HttpServletRequest request, final HttpServletResponse response )
        throws IOException
    {        
        Try<Principal> currentUser = new DefaultPrincipalFacade().tryGetCurrentPrincipal();    
        if (currentUser.isSuccess()) {
            DefaultPrincipal defaultCurrentUser = (DefaultPrincipal)currentUser.get();            
            writeLine(response, "Hello " + defaultCurrentUser.getPrincipalId());
            Iterator<PrincipalAttribute> i = defaultCurrentUser.getAttributes().values().iterator();
            while (i.hasNext()) {
                CollectionPrincipalAttribute<StringPrincipalAttribute> attribute = (CollectionPrincipalAttribute<StringPrincipalAttribute>) i.next();                
                writeLine(response, "Attribute: " + attribute.getName() + ", Value: " + attribute.getValues().iterator().next());
            }
            Iterator<Authorization> j = ((DefaultPrincipal)currentUser.get()).getAuthorizations().iterator();
            while (j.hasNext()) {
                Authorization scope = (Authorization)j.next();
                writeLine(response, "Scope: " + scope.getName());
            }
                       
            try {
                ODataQueryResult result = ODataQueryBuilder                        
                        .withEntity("/sap/opu/odata/SAP/ZAZURESAP_PP_SRV", "SalesOrderSet")                        
                        .withoutMetadata()
                        .build()
                        .execute(DESTINATION_NAME);

                writeLine(response, "ODate Response Code: " + result.getHttpStatusCode());

                Enumeration<String> headerNames = result.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    writeLine(response, "OData Query Response Header: " + headerName);
                }
            } catch (Exception ex) {
                writeLine(response, "Exception: " + ex.getMessage());
            }
        } else {
            writeLine(response, "No authenticated user");
        }

    }

    private void writeLine(HttpServletResponse response, String string) throws IOException {
		response.getWriter().append(string);
		response.getWriter().append("\n");
    }
}