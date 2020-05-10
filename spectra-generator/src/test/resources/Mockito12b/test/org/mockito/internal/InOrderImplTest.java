/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.InvocationBuilder;
import org.mockitousage.IMethods;
import org.mockitoutil.TestBase;

import java.util.List;

import static java.util.Arrays.asList;

@SuppressWarnings("unchecked")
public class InOrderImplTest extends TestBase {

    @Mock
    IMethods mock;

    @Test
    public void shouldMarkVerifiedInOrder() throws Exception {
        //given
        InOrderImpl impl = new InOrderImpl((List) asList(mock));
        Invocation i = new InvocationBuilder().toInvocation();
        assertFalse(impl.isVerified(i));

        //when
        impl.markVerified(i);

        //then
        assertTrue(impl.isVerified(i));
    }
}
