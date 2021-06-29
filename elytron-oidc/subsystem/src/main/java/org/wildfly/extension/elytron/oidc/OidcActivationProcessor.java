/*
 * Copyright 2021 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron.oidc;

import java.util.List;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.server.security.VirtualDomainMarkerUtility;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.metadata.web.spec.LoginConfigMetaData;

/**
 * A {@link DeploymentUnitProcessor} to detect if Elytron OIDC should be activated.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
class OidcActivationProcessor implements DeploymentUnitProcessor {

    private static final String AUTH_METHOD = "authMethod";
    private static final String OIDC_AUTH_METHOD = "OIDC";
    private static final String KEYCLOAK_AUTH_METHOD = "KEYCLOAK"; // continue to allow KEYCLOAK to be specified directly

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        WarMetaData warMetaData = deploymentUnit.getAttachment(WarMetaData.ATTACHMENT_KEY);
        if (warMetaData == null) {
            return;
        }

        JBossWebMetaData mergedMetaData = warMetaData.getMergedJBossWebMetaData();
        LoginConfigMetaData loginConfig = mergedMetaData != null ? mergedMetaData.getLoginConfig() : null;
        if (loginConfig != null) {
            String authMethod = loginConfig.getAuthMethod();
            if (! (authMethod.equals(OIDC_AUTH_METHOD) || authMethod.equals(KEYCLOAK_AUTH_METHOD))) {
                // An auth-method other than OIDC or KEYCLOAK has been configured, no activation required
                return;
            }
        }

        if (loginConfig == null) {
            final CompositeIndex index = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
            List<AnnotationInstance> annotations = index.getAnnotations(LOGIN_CONFIG_DOT_NAME);
            for (AnnotationInstance annotation : annotations) {
                // First we must be sure the annotation is on an Application class.
                AnnotationTarget target = annotation.target();
                if (target.kind() == Kind.CLASS) {
                    if (extendsApplication(target.asClass(), index)) {
                        loginConfig = new LoginConfigMetaData();
                        AnnotationValue authMethodValue = annotation.value(AUTH_METHOD);
                        if (authMethodValue == null) {
                            throw ROOT_LOGGER.noAuthMethodSpecified();
                        }
                        loginConfig.setAuthMethod(authMethodValue.asString());
                        AnnotationValue realmNameValue = annotation.value(REALM_NAME);
                        if (realmNameValue != null) {
                            loginConfig.setRealmName(realmNameValue.asString());
                        }

                        mergedMetaData.setLoginConfig(loginConfig);

                        break;
                    }
                }
                ROOT_LOGGER.loginConfigInvalidTarget(target.toString());
            }
        }

        if (loginConfig != null && JWT_AUTH_METHOD.equals(loginConfig.getAuthMethod())) {
            ROOT_LOGGER.tracef("Activating JWT for deployment %s.", deploymentUnit.getName());
            JwtDeploymentMarker.mark(deploymentUnit);
            VirtualDomainMarkerUtility.virtualDomainRequired(deploymentUnit);
        }

    }

    @Override
    public void undeploy(DeploymentUnit context) {}

}