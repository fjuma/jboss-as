/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.ejb.remote.security;

//import org.jboss.as.test.shared.integration.ejb.security.Util;
//import java.util.concurrent.Callable;


import java.util.concurrent.Callable;

import org.jboss.as.test.shared.integration.ejb.security.Util;
//import org.wildfly.security.auth.server.SecurityDomain;
//import org.wildfly.security.auth.server.SecurityIdentity;
//import org.wildfly.security.evidence.PasswordGuessEvidence;
//import org.wildfly.security.auth.client.AuthenticationConfiguration;
//import org.wildfly.security.auth.client.AuthenticationContext;
//import org.wildfly.security.auth.client.MatchRule;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBContext;
import javax.ejb.Remote;
import javax.ejb.Stateless;
//import javax.security.auth.login.LoginContext;
//import javax.security.auth.login.LoginException;
//import javax.security.auth.login.LoginContext;
//import javax.security.auth.login.LoginException;

/**
 * An unsecured EJB used to test switching the identity before calling a secured EJB.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@Stateless
@Remote(IntermediateAccess.class)
public class EntryBean implements IntermediateAccess {

    @Resource
    private EJBContext context;

    @EJB
    private SecurityInformation ejb;

    @Override
    public String getPrincipalName() {
        return context.getCallerPrincipal().getName();
    }

    @Override
    public String getPrincipalName(String username, String password) {
        /*try {
            LoginContext lc = null;
            try {
                if (username != null && password != null) {
                    lc = Util.getCLMLoginContext(username, password);
                    lc.login();
                }
                return ejb.getPrincipalName();
            } finally {
                if (lc != null) {
                    lc.logout();
                }
            }
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }*/
        /*final AuthenticationConfiguration authenticationConfiguration = AuthenticationConfiguration.EMPTY
                .useName(username)
                .usePassword(password)
                .useProvidersFromClassLoader(EntryBean.class.getClassLoader());
        final AuthenticationContext authenticationContext = AuthenticationContext.empty().with(MatchRule.ALL, authenticationConfiguration);
        try {
            return authenticationContext.runCallable(() -> ejb.getPrincipalName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }*/
        /*if (SecurityDomain.getCurrent() != null) {
            // Elytron is enabled, use SecurityDomain.authenticate() to obtain the SecurityIdentity you want to switch to
            try {
                if (username != null && password != null) {
                    final SecurityIdentity securityIdentity = SecurityDomain.getCurrent().authenticate(username, new PasswordGuessEvidence(password.toCharArray()));
                    return securityIdentity.runAs((Callable<String>) () -> ejb.getPrincipalName());
                }
                return ejb.getPrincipalName();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            // Legacy security is enabled, use the old ClientLoginModule approach
            try {
                LoginContext lc = null;
                try {
                    if (username != null && password != null) {
                        lc = Util.getCLMLoginContext(username, password);
                        lc.login();
                    }
                    return ejb.getPrincipalName();
                } finally {
                    if (lc != null) {
                        lc.logout();
                    }
                }
            } catch (LoginException e) {
                throw new RuntimeException(e);
            }
        }*/
        try {
            final Callable<String> callable = () -> ejb.getPrincipalName();
            return Util.switchIdentity(username, password, callable);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
