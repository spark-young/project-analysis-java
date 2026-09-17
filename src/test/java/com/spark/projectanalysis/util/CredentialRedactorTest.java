package com.spark.projectanalysis.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** OPT-10：凭证脱敏单测 */
class CredentialRedactorTest {

    @Test
    void redactsSchemeAuthUrl() {
        assertEquals("http://***@github.com/x.git",
                CredentialRedactor.redact("http://alice:s3cret@github.com/x.git"));
    }

    @Test
    void redactsBareUserinfo() {
        assertEquals("***@host", CredentialRedactor.redact("user:pass@host"));
    }

    @Test
    void redactsTokenContainingColon() {
        assertEquals("https://***@host",
                CredentialRedactor.redact("https://user:pa:ss@host"));
    }

    @Test
    void leavesPlainUrlUnchanged() {
        assertEquals("https://github.com/x.git",
                CredentialRedactor.redact("https://github.com/x.git"));
    }

    @Test
    void leavesPlainTextUnchanged() {
        assertEquals("no credential here",
                CredentialRedactor.redact("no credential here"));
    }

    @Test
    void nullSafe() {
        assertNull(CredentialRedactor.redact(null));
    }
}
