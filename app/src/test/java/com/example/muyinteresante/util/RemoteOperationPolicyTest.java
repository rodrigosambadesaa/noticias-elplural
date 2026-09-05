package com.example.muyinteresante.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.ConnectException;
import java.net.UnknownHostException;

import org.junit.Test;

public class RemoteOperationPolicyTest {

    @Test
    public void offlineGuardSkipsRequest() {
        assertFalse(RemoteOperationPolicy.canStartRequest(false));
        assertTrue(RemoteOperationPolicy.canStartRequest(true));
    }

    @Test
    public void successfulHttpResponseDoesNotNeedDiagnosis() {
        assertEquals(
                RemoteOperationPolicy.FailureDisposition.SERVICE_UNAVAILABLE,
                RemoteOperationPolicy.classifyFailure(200, null, false));
        assertTrue(RemoteOperationPolicy.hasHttpResponse(503));
    }

    @Test
    public void feedFailureWithGeneralInternetIsServiceSpecific() {
        assertEquals(
                RemoteOperationPolicy.FailureDisposition.SERVICE_UNAVAILABLE,
                RemoteOperationPolicy.classifyFailure(
                        null, new UnknownHostException("feed.example"), true));
    }

    @Test
    public void ambiguousFailureWithoutGeneralInternetIsConnectivityProblem() {
        assertEquals(
                RemoteOperationPolicy.FailureDisposition.CONNECTIVITY_PROBLEM,
                RemoteOperationPolicy.classifyFailure(
                        null, new ConnectException("connection refused"), false));
    }
}
