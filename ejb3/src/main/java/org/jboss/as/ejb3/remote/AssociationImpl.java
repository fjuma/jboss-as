/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.jboss.as.ejb3.remote;

import org.jboss.as.ee.component.Component;
import org.jboss.as.ee.component.ComponentIsStoppedException;
import org.jboss.as.ee.component.ComponentView;
import org.jboss.as.ee.component.interceptors.InvocationType;
import org.jboss.as.ejb3.component.EJBComponentUnavailableException;
import org.jboss.as.ejb3.component.interceptors.CancellationFlag;
import org.jboss.as.ejb3.component.session.SessionBeanComponent;
import org.jboss.as.ejb3.component.stateful.StatefulSessionComponent;
import org.jboss.as.ejb3.component.stateless.StatelessSessionComponent;
import org.jboss.as.ejb3.deployment.DeploymentModuleIdentifier;
import org.jboss.as.ejb3.deployment.DeploymentRepository;
import org.jboss.as.ejb3.deployment.DeploymentRepositoryListener;
import org.jboss.as.ejb3.deployment.EjbDeploymentInformation;
import org.jboss.as.ejb3.deployment.ModuleDeployment;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBMethodLocator;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.CancelHandle;
import org.jboss.ejb.server.ClusterTopologyListener;
import org.jboss.ejb.server.InvocationRequest;
import org.jboss.ejb.server.ListenerHandle;
import org.jboss.ejb.server.ModuleAvailabilityListener;
import org.jboss.ejb.server.Request;
import org.jboss.ejb.server.SessionOpenRequest;
import org.jboss.invocation.InterceptorContext;
import org.wildfly.common.Assert;
import org.wildfly.common.annotation.NotNull;

import javax.ejb.EJBException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */
final class AssociationImpl implements Association {

    private final DeploymentRepository deploymentRepository;

    AssociationImpl(final DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;
    }

    @Override
    public ClassLoader mapClassLoader(String appName, String moduleName) {
        return Assert.assertNotNull(findEJB(appName,moduleName)).getDeploymentClassLoader();
    }

    @Override
    public <T> CancelHandle receiveInvocationRequest(@NotNull final InvocationRequest<T> invocationRequest) {

        final EJBLocator<T> ejbLocator = invocationRequest.getEJBLocator();

        final String appName = ejbLocator.getAppName();
        final String moduleName = ejbLocator.getModuleName();
        final String distinctName = ejbLocator.getDistinctName();
        final String beanName = ejbLocator.getBeanName();

        final Map<String, Object> attachments = invocationRequest.getAttachments();

        final EjbDeploymentInformation ejbDeploymentInformation = findEJB(appName, moduleName, distinctName, beanName);

        if (ejbDeploymentInformation == null) {
            invocationRequest.writeNoSuchEJB();
            return CancelHandle.NULL;
        }

        final String viewClassName = ejbLocator.getViewType().getName();

        if (!ejbDeploymentInformation.isRemoteView(viewClassName)) {
            invocationRequest.writeNoSuchEJB();
            return CancelHandle.NULL;
        }

        final ComponentView componentView = ejbDeploymentInformation.getView(viewClassName);

        final Method invokedMethod = findMethod(componentView, invocationRequest.getMethodLocator());
        if (invokedMethod == null) {
            invocationRequest.writeNoSuchMethod();
            return CancelHandle.NULL;
        }

        final boolean isAsync = componentView.isAsynchronous(invokedMethod);

        final boolean oneWay = isAsync && invokedMethod.getReturnType() == void.class;

        if (oneWay) {
            // send immediate response
            invocationRequest.writeInvocationResult(null);
        }

        final CancellationFlag cancellationFlag = new CancellationFlag();

        Runnable runnable = () -> {
            if (cancellationFlag.get()) {
                if (! oneWay) invocationRequest.writeCancelResponse();
                return;
            }
            // invoke the method
            final Object result;
            //SecurityActions.remotingContextSetConnection(channelAssociation.getChannel().getConnection());
            try {
                result = invokeMethod(componentView, invokedMethod, invocationRequest, cancellationFlag);
            } catch (EJBComponentUnavailableException ex) {
                // if the EJB is shutting down when the invocation was done, then it's as good as the EJB not being available. The client has to know about this as
                // a "no such EJB" failure so that it can retry the invocation on a different node if possible.
                EjbLogger.EJB3_INVOCATION_LOGGER.debugf("Cannot handle method invocation: %s on bean: %s due to EJB component unavailability exception. Returning a no such EJB available message back to client", invokedMethod, beanName);
                if (! oneWay) invocationRequest.writeNoSuchEJB();
                return;
            } catch (ComponentIsStoppedException ex) {
                EjbLogger.EJB3_INVOCATION_LOGGER.debugf("Cannot handle method invocation: %s on bean: %s due to EJB component stopped exception. Returning a no such EJB available message back to client", invokedMethod, beanName);
                if (! oneWay) invocationRequest.writeNoSuchEJB();
                return;
            } catch (CancellationException ex) {
                if (! oneWay) invocationRequest.writeCancelResponse();
                return;
            } catch (Exception exception) {
                if (oneWay) return;
                // write out the failure
                final Exception exceptionToWrite;
                final Throwable cause = exception.getCause();
                if (componentView.getComponent() instanceof StatefulSessionComponent && exception instanceof EJBException && cause != null) {
                    if (!(componentView.getComponent().isRemotable(cause))) {
                        // Avoid serializing the cause of the exception in case it is not remotable
                        // Client might not be able to deserialize and throw ClassNotFoundException
                        exceptionToWrite = new EJBException(exception.getLocalizedMessage());
                    } else {
                        exceptionToWrite = exception;
                    }
                } else {
                    exceptionToWrite = exception;
                }
                invocationRequest.writeException(exceptionToWrite);
                return;
            }
            // invocation was successful
            if (! oneWay) try {
                // attach any weak affinity if available
                Affinity weakAffinity = null;
                if (ejbLocator.isStateful() && componentView.getComponent() instanceof StatefulSessionComponent) {
                    final StatefulSessionComponent statefulSessionComponent = (StatefulSessionComponent) componentView.getComponent();
                    weakAffinity = getWeakAffinity(statefulSessionComponent, ejbLocator.asStateful());
                } else if (componentView.getComponent() instanceof StatelessSessionComponent) {
                    final StatelessSessionComponent statelessSessionComponent = (StatelessSessionComponent) componentView.getComponent();
                    weakAffinity = statelessSessionComponent.getWeakAffinity();
                }
                if (weakAffinity != null && !weakAffinity.equals(Affinity.NONE)) {
                    attachments.put(Affinity.WEAK_AFFINITY_CONTEXT_KEY, weakAffinity);
                }
                invocationRequest.writeInvocationResult(result);
            } catch (Throwable ioe) {
                EjbLogger.REMOTE_LOGGER.couldNotWriteMethodInvocation(ioe, invokedMethod, beanName, appName, moduleName, distinctName);
            }
        };
        // invoke the method and write out the response, possibly on a separate thread
        execute(invocationRequest, runnable, isAsync);
        // TODO: two-stage cancellation
        return aggressive -> cancellationFlag.set(true);
    }

    private void execute(Request request, Runnable task, final boolean isAsync) {
        if (request.getProtocol().equals("local") && ! isAsync) {
            task.run();
        } else {
            request.getRequestExecutor().execute(task);
        }
    }

    @Override
    @NotNull
    public CancelHandle receiveSessionOpenRequest(@NotNull final SessionOpenRequest sessionOpenRequest) {

        final EJBIdentifier ejbIdentifier = sessionOpenRequest.getEJBIdentifier();
        final String appName = ejbIdentifier.getAppName();
        final String moduleName = ejbIdentifier.getModuleName();
        final String beanName = ejbIdentifier.getBeanName();
        final String distinctName = ejbIdentifier.getDistinctName();

        final EjbDeploymentInformation ejbDeploymentInformation = findEJB(appName, moduleName, distinctName, beanName);
        if (ejbDeploymentInformation == null) {
            sessionOpenRequest.writeNoSuchEJB();
            return CancelHandle.NULL;
        }
        final Component component = ejbDeploymentInformation.getEjbComponent();
        if (!(component instanceof StatefulSessionComponent)) {
            sessionOpenRequest.writeNotStateful();
            return CancelHandle.NULL;
        }
        final StatefulSessionComponent statefulSessionComponent = (StatefulSessionComponent) component;
        // generate the session id and write out the response, possibly on a separate thread
        final AtomicBoolean cancelled = new AtomicBoolean();
        Runnable runnable = () -> {
            if (cancelled.get()) {
                sessionOpenRequest.writeCancelResponse();
                return;
            }
            final SessionID sessionID;
            try {
                sessionID = statefulSessionComponent.createSessionRemote();
            } catch (Exception t) {
                EjbLogger.REMOTE_LOGGER.exceptionGeneratingSessionId(t, statefulSessionComponent.getComponentName(), ejbIdentifier);
                sessionOpenRequest.writeException(t);
                return;
            }
            sessionOpenRequest.convertToStateful(sessionID);
        };
        execute(sessionOpenRequest, runnable, false);
        return ignored -> cancelled.set(true);
    }

    @Override
    public ListenerHandle registerClusterTopologyListener(@NotNull final ClusterTopologyListener clusterTopologyListener) {
        // todo
        return () -> {};
    }

    @Override
    public ListenerHandle registerModuleAvailabilityListener(@NotNull final ModuleAvailabilityListener moduleAvailabilityListener) {
        final DeploymentRepositoryListener listener = new DeploymentRepositoryListener() {
            public void listenerAdded(final DeploymentRepository repository) {
                List<ModuleAvailabilityListener.ModuleIdentifier> identifierList = new ArrayList<>();
                for (DeploymentModuleIdentifier identifier : repository.getModules().keySet()) {
                    final ModuleAvailabilityListener.ModuleIdentifier moduleIdentifier = toModuleIdentifier(identifier);
                    identifierList.add(moduleIdentifier);
                }
                moduleAvailabilityListener.moduleAvailable(identifierList);
            }

            private ModuleAvailabilityListener.ModuleIdentifier toModuleIdentifier(final DeploymentModuleIdentifier identifier) {
                return new ModuleAvailabilityListener.ModuleIdentifier(identifier.getApplicationName(), identifier.getModuleName(), identifier.getDistinctName());
            }

            public void deploymentAvailable(final DeploymentModuleIdentifier deployment, final ModuleDeployment moduleDeployment) {
                moduleAvailabilityListener.moduleAvailable(Collections.singletonList(toModuleIdentifier(deployment)));
            }

            public void deploymentStarted(final DeploymentModuleIdentifier deployment, final ModuleDeployment moduleDeployment) {
            }

            public void deploymentRemoved(final DeploymentModuleIdentifier deployment) {
            }
        };
        deploymentRepository.addListener(listener);
        return () -> deploymentRepository.removeListener(listener);
    }

    protected EjbDeploymentInformation findEJB(final String appName, final String moduleName, final String distinctName, final String beanName) {
        final DeploymentModuleIdentifier ejbModule = new DeploymentModuleIdentifier(appName, moduleName, distinctName);
        final Map<DeploymentModuleIdentifier, ModuleDeployment> modules = this.deploymentRepository.getStartedModules();
        if (modules == null || modules.isEmpty()) {
            return null;
        }
        final ModuleDeployment moduleDeployment = modules.get(ejbModule);
        if (moduleDeployment == null) {
            return null;
        }
        return moduleDeployment.getEjbs().get(beanName);
    }

    //TODO Elytron - find proper way match appName/moduleName to classloader
    protected EjbDeploymentInformation findEJB(final String appName, final String moduleName) {
        final DeploymentModuleIdentifier ejbModule = new DeploymentModuleIdentifier(appName, moduleName, "");
        final Map<DeploymentModuleIdentifier, ModuleDeployment> modules = this.deploymentRepository.getStartedModules();
        if (modules == null || modules.isEmpty()) {
            return null;
        }
        final ModuleDeployment moduleDeployment = modules.get(ejbModule);
        if (moduleDeployment == null) {
            return null;
        }
        return moduleDeployment.getEjbs().entrySet().iterator().next().getValue();
    }



    private <T> Object invokeMethod(final ComponentView componentView, final Method method, final InvocationRequest<T> incomingInvocation, final CancellationFlag cancellationFlag) throws Exception {
        final InterceptorContext interceptorContext = new InterceptorContext();
        interceptorContext.setParameters(incomingInvocation.getParameters());
        interceptorContext.setMethod(method);
        interceptorContext.putPrivateData(Component.class, componentView.getComponent());
        interceptorContext.putPrivateData(ComponentView.class, componentView);
        interceptorContext.putPrivateData(InvocationType.class, InvocationType.REMOTE);
        interceptorContext.setBlockingCaller(false);
        // setup the contextData on the (spec specified) InvocationContext
        final Map<String, Object> invocationContextData = new HashMap<String, Object>();
        interceptorContext.setContextData(invocationContextData);
        if (incomingInvocation.getAttachments() != null) {
            // attach the attachments which were passed from the remote client
            for (final Map.Entry<String, Object> attachment : incomingInvocation.getAttachments().entrySet()){
                if (attachment == null) {
                    continue;
                }
                final String key = attachment.getKey();
                final Object value = attachment.getValue();
                // these are private to JBoss EJB implementation and not meant to be visible to the
                // application, so add these attachments to the privateData of the InterceptorContext
                if (EJBClientInvocationContext.PRIVATE_ATTACHMENTS_KEY.equals(key)) {
                    final Map<?, ?> privateAttachments = (Map<?, ?>) value;
                    for (final Map.Entry<?, ?> privateAttachment : privateAttachments.entrySet()) {
                        interceptorContext.putPrivateData(privateAttachment.getKey(), privateAttachment.getValue());
                    }
                } else {
                    // add it to the InvocationContext which will be visible to the target bean and the
                    // application specific interceptors
                    invocationContextData.put(key, value);
                }
            }
        }
        // add the session id to the interceptor context, if it's a stateful ejb locator
        final EJBLocator<T> ejbLocator = incomingInvocation.getEJBLocator();
        if (ejbLocator.isStateful()) {
            interceptorContext.putPrivateData(SessionID.class, ejbLocator.asStateful().getSessionId());
        }
        // add transaction
        if (incomingInvocation.hasTransaction()) {
            interceptorContext.setTransactionSupplier(incomingInvocation::getTransaction);
        }
        final boolean isAsync = componentView.isAsynchronous(method);
        final boolean oneWay = isAsync && method.getReturnType() == void.class;
        final boolean isSessionBean = componentView.getComponent() instanceof SessionBeanComponent;
        if (isAsync && isSessionBean) {
            if (! oneWay) {
                interceptorContext.putPrivateData(CancellationFlag.class, cancellationFlag);
            }
            final Object result = componentView.invoke(interceptorContext);
            return result == null ? null : ((Future<?>) result).get();
        } else {
            return componentView.invoke(interceptorContext);
        }
    }

    private Method findMethod(final ComponentView componentView, final EJBMethodLocator ejbMethodLocator) {
        final Set<Method> viewMethods = componentView.getViewMethods();
        for (final Method method : viewMethods) {
            if (method.getName().equals(ejbMethodLocator.getMethodName())) {
                final Class<?>[] methodParamTypes = method.getParameterTypes();
                if (methodParamTypes.length != ejbMethodLocator.getParameterCount()) {
                    continue;
                }
                boolean found = true;
                for (int i = 0; i < methodParamTypes.length; i++) {
                    if (!methodParamTypes[i].getName().equals(ejbMethodLocator.getParameterTypeName(i))) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    return method;
                }
            }
        }
        return null;
    }

    private Affinity getWeakAffinity(final StatefulSessionComponent statefulSessionComponent, final StatefulEJBLocator<?> statefulEJBLocator) {
        final SessionID sessionID = statefulEJBLocator.getSessionId();
        return statefulSessionComponent.getCache().getWeakAffinity(sessionID);
    }
}