package com.contoso.sample;

import java.io.IOException;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;


@WebServlet("/hello")
@ServletSecurity(@HttpConstraint(rolesAllowed = { "Display" }))
public class HelloWorldServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(HelloWorldServlet.class);

    @Override
    protected void doGet( final HttpServletRequest request, final HttpServletResponse response )
        throws IOException
    {
        logger.info("I am running!");
        Try<Principal> currentUser = new DefaultPrincipalFacade().tryGetCurrentPrincipal();    
        if (currentUser.isSuccess()) {
            DefaultPrincipal defaultCurrentUser = (DefaultPrincipal)currentUser.get();            
            response.getWriter().println("Hello " + defaultCurrentUser.getPrincipalId());
            Iterator<PrincipalAttribute> i = defaultCurrentUser.getAttributes().values().iterator();
            while (i.hasNext()) {
                CollectionPrincipalAttribute<StringPrincipalAttribute> attribute = (CollectionPrincipalAttribute<StringPrincipalAttribute>) i.next();                
                response.getWriter().println("Attribute: " + attribute.getName() + ", Value: " + attribute.getValues().iterator().next());
            }
            Iterator<Authorization> j = ((DefaultPrincipal)currentUser.get()).getAuthorizations().iterator();
            while (j.hasNext()) {
                Authorization scope = (Authorization)j.next();
                response.getWriter().println("Scope: " + scope.getName());
            }
        } else {
            response.getWriter().println("No authenticated user");
        }

    }
}
