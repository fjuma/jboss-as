/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.ejb3.subsystem;

import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.ejb3.subsystem.EJB3SubsystemModel.SECURITY_DOMAIN;
import static org.jboss.as.ejb3.subsystem.EJB3SubsystemModel.SECURITY_DOMAINS;

import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Parser for ejb3:5.0 namespace.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class EJB3Subsystem50Parser extends EJB3Subsystem40Parser {

    public static final EJB3Subsystem50Parser INSTANCE = new EJB3Subsystem50Parser();

    protected EJB3Subsystem50Parser() {
    }

    @Override
    protected EJB3SubsystemNamespace getExpectedNamespace() {
        return EJB3SubsystemNamespace.EJB3_5_0;
    }

    @Override
    protected void readElement(final XMLExtendedStreamReader reader, final EJB3SubsystemXMLElement element, final List<ModelNode> operations, final ModelNode ejb3SubsystemAddOperation) throws XMLStreamException {
        switch (element) {
            case SECURITY_DOMAIN: {
                parseSecurityDomain(reader, ejb3SubsystemAddOperation);
                break;
            }
            default: {
                super.readElement(reader, element, operations, ejb3SubsystemAddOperation);
            }
        }
    }

    private void parseSecurityDomain(final XMLExtendedStreamReader reader, final ModelNode ejb3SubsystemAddOperation) throws XMLStreamException {
        // FJ
        System.out.println("*** PARSING SECURITY DOMAIN");
        String securityDomainName = null;
        ModelNode securityDomain = new ModelNode();
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final String attributeValue = reader.getAttributeValue(i);
                final EJB3SubsystemXMLAttribute attribute = EJB3SubsystemXMLAttribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME:
                        // FJ
                        System.out.println("****DOMAIN NAME " + attributeValue);
                        securityDomainName = attributeValue;
                        EJB3SubsystemRootResourceDefinition.SECURITY_DOMAIN_NAME.parseAndSetParameter(attributeValue, securityDomain, reader);
                        // FJ
                        System.out.println("***** MODEL NODE FIRST " + securityDomain);
                        break;
                    case ALIAS:
                        // FJ
                        System.out.println("****ALIAS " + attributeValue);
                        EJB3SubsystemRootResourceDefinition.SECURITY_DOMAIN_ALIAS.parseAndSetParameter(attributeValue, securityDomain, reader);
                        // FJ
                        System.out.println("***** MODEL NODE SECOND " + securityDomain);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
        if (securityDomainName == null) {
            throw missingRequired(reader, Collections.singleton(EJB3SubsystemXMLAttribute.NAME.getLocalName()));
        }
        requireNoContent(reader);
        // FJ
        //System.out.println("*** MODEL NODE CREATED IS " + securityDomain.asObject() + " NAME " + securityDomain.asObject().get("name") + " ALIAS " + securityDomain.asObject().get("alias"));
        ejb3SubsystemAddOperation.get(SECURITY_DOMAINS).add(securityDomain);
    }
}
