package com.samurai.telegram.tdlib;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

public final class AndroidDatabaseKeyStoreTest {
    @Test
    public void keyPersistsAndClearRotatesIt() {
        Context context = ApplicationProvider.getApplicationContext();
        AndroidDatabaseKeyStore store = new AndroidDatabaseKeyStore(context);
        String account = "instrumentation-key-test";

        byte[] first = store.getOrCreate(account);
        byte[] second = store.getOrCreate(account);
        assertArrayEquals(first, second);

        store.clear(account);
        byte[] third = store.getOrCreate(account);
        assertFalse(Arrays.equals(first, third));
        store.clear(account);
    }
}
