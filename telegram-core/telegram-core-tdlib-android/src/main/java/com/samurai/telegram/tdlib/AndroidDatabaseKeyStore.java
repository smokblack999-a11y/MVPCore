package com.samurai.telegram.tdlib;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Persists a 32-byte TDLib database key encrypted by an Android Keystore AES key.
 * The encrypted record is stored below noBackupFilesDir so an Android backup cannot
 * restore a database without its corresponding device-bound Keystore key.
 */
public final class AndroidDatabaseKeyStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_PREFIX = "samurai.tdlib.dbkey.";
    private static final String FILE_PREFIX = "db-key-";
    private static final String FILE_SUFFIX = ".v1";
    private static final int KEY_SIZE_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_SIZE_BYTES = 12;

    private final Context appContext;

    public AndroidDatabaseKeyStore(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        this.appContext = context.getApplicationContext();
    }

    public synchronized byte[] getOrCreate(String accountId) {
        String safeId = safeAccountId(accountId);
        File file = keyFile(safeId);
        try {
            SecretKey master = loadOrCreateMasterKey(safeId);
            if (file.isFile()) return decrypt(master, read(file));

            byte[] databaseKey = new byte[32];
            new SecureRandom().nextBytes(databaseKey);
            writeAtomic(file, encrypt(master, databaseKey));
            return databaseKey.clone();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load Telegram database key", e);
        }
    }

    public synchronized void clear(String accountId) {
        String safeId = safeAccountId(accountId);
        File file = keyFile(safeId);
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Unable to delete Telegram database key file");
        }
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            keyStore.deleteEntry(KEY_PREFIX + safeId);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to delete Telegram Keystore key", e);
        }
    }

    private SecretKey loadOrCreateMasterKey(String safeId) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        String alias = KEY_PREFIX + safeId;
        if (keyStore.containsAlias(alias)) {
            java.security.Key key = keyStore.getKey(alias, null);
            if (!(key instanceof SecretKey)) throw new IllegalStateException("Invalid Android Keystore key");
            return (SecretKey) key;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static byte[] encrypt(SecretKey master, byte[] plaintext) throws Exception {
        byte[] iv = new byte[IV_SIZE_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, master, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);
        String record = Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        return record.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decrypt(SecretKey master, byte[] recordBytes) throws Exception {
        String record = new String(recordBytes, StandardCharsets.UTF_8);
        int separator = record.indexOf(':');
        if (separator <= 0 || separator == record.length() - 1) {
            throw new IllegalStateException("Invalid encrypted database-key record");
        }
        byte[] iv = Base64.decode(record.substring(0, separator), Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(record.substring(separator + 1), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, master, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        if (plaintext.length != 32) throw new IllegalStateException("Invalid TDLib key length");
        return plaintext;
    }

    private void writeAtomic(File target, byte[] value) throws Exception {
        File parent = target.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Unable to create key directory");
        File temp = new File(parent, target.getName() + ".tmp");
        FileOutputStream output = new FileOutputStream(temp, false);
        try {
            output.write(value);
            output.flush();
            output.getFD().sync();
        } finally {
            output.close();
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IllegalStateException("Unable to atomically publish database key");
        }
    }

    private static byte[] read(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] value = new byte[(int) Math.min(Integer.MAX_VALUE, file.length())];
            int offset = 0;
            while (offset < value.length) {
                int read = input.read(value, offset, value.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != value.length) throw new IllegalStateException("Truncated encrypted database-key record");
            return value;
        } finally {
            input.close();
        }
    }

    private File keyFile(String safeId) {
        return new File(new File(appContext.getNoBackupFilesDir(), "telegram-core/keys"),
                FILE_PREFIX + safeId + FILE_SUFFIX);
    }

    private static String safeAccountId(String accountId) {
        if (accountId == null || accountId.trim().isEmpty()) throw new IllegalArgumentException("accountId required");
        String value = accountId.trim();
        if (!value.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("accountId must contain only A-Z, a-z, 0-9, '.', '_' or '-'");
        }
        return value;
    }
}
