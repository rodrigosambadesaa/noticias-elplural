package com.example.muyinteresante.util;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

import javax.net.ssl.SSLException;

/** Shared policy for cheap network guards and post-failure diagnostics. */
public final class RemoteOperationPolicy {

    public enum FailureDisposition {
        SERVICE_UNAVAILABLE,
        CONNECTIVITY_PROBLEM
    }

    private RemoteOperationPolicy() {
    }

    public static boolean canStartRequest(boolean connected) {
        return connected;
    }

    public static FailureDisposition classifyFailure(
            Integer httpStatus,
            Throwable failure,
            boolean generalInternetReachable) {
        if (hasHttpResponse(httpStatus)) {
            return FailureDisposition.SERVICE_UNAVAILABLE;
        }

        if (isAmbiguousConnectivityFailure(failure) && generalInternetReachable) {
            return FailureDisposition.SERVICE_UNAVAILABLE;
        }

        return FailureDisposition.CONNECTIVITY_PROBLEM;
    }

    public static boolean hasHttpResponse(Integer httpStatus) {
        return httpStatus != null && httpStatus >= 100 && httpStatus <= 599;
    }

    public static boolean isAmbiguousConnectivityFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof SSLException) {
                return true;
            }
            if (current instanceof IOException) {
                String message = current.getMessage();
                if (message != null) {
                    String normalized = message.toLowerCase(Locale.ROOT);
                    if (normalized.contains("connect")
                            || normalized.contains("timeout")
                            || normalized.contains("timed out")
                            || normalized.contains("reset")
                            || normalized.contains("route")
                            || normalized.contains("network")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
