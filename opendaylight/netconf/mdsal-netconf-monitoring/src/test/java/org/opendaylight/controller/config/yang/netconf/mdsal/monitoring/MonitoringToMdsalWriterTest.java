/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.netconf.mdsal.monitoring;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfStateBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class MonitoringToMdsalWriterTest {

    private static final InstanceIdentifier<NetconfState> INSTANCE_IDENTIFIER = InstanceIdentifier.create(NetconfState.class);

    @Mock
    private NetconfMonitoringService monitoring;
    @Mock
    private BindingAwareBroker.ProviderContext context;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private WriteTransaction writeTransaction;

    private MonitoringToMdsalWriter writer;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(null).when(monitoring).registerListener(any());

        doReturn(dataBroker).when(context).getSALService(DataBroker.class);

        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();

        doNothing().when(writeTransaction).put(eq(LogicalDatastoreType.OPERATIONAL), any(), any());
        doNothing().when(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL), any());
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTransaction).submit();

        writer = new MonitoringToMdsalWriter(monitoring);
    }

    @Test
    public void testClose() throws Exception {
        writer.onSessionInitiated(context);
        writer.close();
        InOrder inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).delete(LogicalDatastoreType.OPERATIONAL, INSTANCE_IDENTIFIER);
        inOrder.verify(writeTransaction).submit();
    }

    @Test
    public void testOnStateChanged() throws Exception {
        writer.onSessionInitiated(context);
        final NetconfState state = new NetconfStateBuilder().build();
        writer.onStateChanged(state);
        InOrder inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL, INSTANCE_IDENTIFIER, state);
        inOrder.verify(writeTransaction).submit();
    }

    @Test
    public void testOnSessionInitiated() throws Exception {
        writer.onSessionInitiated(context);
        verify(monitoring).registerListener(writer);
    }
}