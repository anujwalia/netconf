/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class GetConfig extends AbstractGet {

    private static final Logger LOG = LoggerFactory.getLogger(GetConfig.class);

    private static final String OPERATION_NAME = "get-config";
    private final TransactionProvider transactionProvider;

    public GetConfig(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext, final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting, schemaContext);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(Document document, XmlElement operationElement) throws DocumentedException {
        GetConfigExecution getConfigExecution = null;
        try {
            getConfigExecution = GetConfigExecution.fromXml(operationElement, OPERATION_NAME);

        } catch (final DocumentedException e) {
            LOG.warn("Get request processing failed on session: {}", getNetconfSessionIdForReporting(), e);
            throw e;
        }

        final Optional<YangInstanceIdentifier> dataRootOptional = getDataRootFromFilter(operationElement);
        if (!dataRootOptional.isPresent()) {
            return XmlUtil.createElement(document, XmlNetconfConstants.DATA_KEY, Optional.<String>absent());
        }

        final YangInstanceIdentifier dataRoot = dataRootOptional.get();

        // Proper exception should be thrown
        Preconditions.checkState(getConfigExecution.getDatastore().isPresent(), "Source element missing from request");

        DOMDataReadWriteTransaction rwTx = getTransaction(getConfigExecution.getDatastore().get());
        try {
            final Optional<NormalizedNode<?, ?>> normalizedNodeOptional = rwTx.read(LogicalDatastoreType.CONFIGURATION, dataRoot).checkedGet();
            if (getConfigExecution.getDatastore().get() == Datastore.running) {
                transactionProvider.abortRunningTransaction(rwTx);
            }

            if (!normalizedNodeOptional.isPresent()) {
                return XmlUtil.createElement(document, XmlNetconfConstants.DATA_KEY, Optional.<String>absent());
            }

            return serializeNodeWithParentStructure(document, dataRoot, normalizedNodeOptional.get());
        } catch (ReadFailedException e) {
            LOG.warn("Unable to read data: {}", dataRoot, e);
            throw new IllegalStateException("Unable to read data " + dataRoot, e);
        }
    }

    private DOMDataReadWriteTransaction getTransaction(Datastore datastore) throws DocumentedException{
        if (datastore == Datastore.candidate) {
            return transactionProvider.getOrCreateTransaction();
        } else if (datastore == Datastore.running) {
            return transactionProvider.createRunningTransaction();
        }
        throw new DocumentedException("Incorrect Datastore: ", ErrorType.protocol, ErrorTag.bad_element, ErrorSeverity.error);
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

}
