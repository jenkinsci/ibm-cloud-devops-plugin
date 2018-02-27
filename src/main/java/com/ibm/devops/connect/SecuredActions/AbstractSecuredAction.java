package com.ibm.devops.connect.SecuredAction;

import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import hudson.security.SecurityRealm;
import org.acegisecurity.userdetails.UserDetails;
import com.ibm.devops.connect.DevOpsGlobalConfiguration;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;

public abstract class AbstractSecuredAction {
    protected abstract void run(ParamObj paramObj);

    public class ParamObj {

    }

    public void runAsJenkinsUser(ParamObj paramObj) {

        StandardUsernamePasswordCredentials providedCredentials = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getCredentialsObj();

        Authentication originalAuth = null;

        if(providedCredentials != null) {
            originalAuth = Jenkins.getInstance().getAuthentication();
            Authentication authenticatedAuth = authenticateCredentials(providedCredentials);
            SecurityContextHolder.getContext().setAuthentication(authenticatedAuth);
        }

        try{
            run(paramObj);
        } finally {
            if (originalAuth != null) {
                SecurityContextHolder.getContext().setAuthentication(originalAuth);
            }
        }

    }

    private Authentication authenticateCredentials(StandardUsernamePasswordCredentials providedCredentials) {
        SecurityRealm realm = Jenkins.getInstance().getSecurityRealm();
        SecurityRealm.SecurityComponents securityComponents = realm.createSecurityComponents();

        Authentication auth = getAuth(providedCredentials, realm);

        Authentication result = null;
        if(auth != null) {
            try {
                result = securityComponents.manager.authenticate(auth);
            } catch (AuthenticationException e) {
                if ( e instanceof BadCredentialsException ) {
                    System.out.println("Wrong username or password");
                } else {
                    System.out.println("Something else went wrong");
                }
            }
        }

        return result;
    }

    private Authentication getAuth(StandardUsernamePasswordCredentials providedCredentials, SecurityRealm realm) {
        UserDetails userDetails = realm.loadUserByUsername(providedCredentials.getUsername());

        userDetails.getAuthorities();

        Authentication auth = new UsernamePasswordAuthenticationToken (providedCredentials.getUsername(), providedCredentials.getPassword(), userDetails.getAuthorities());

        return auth;
    }
}