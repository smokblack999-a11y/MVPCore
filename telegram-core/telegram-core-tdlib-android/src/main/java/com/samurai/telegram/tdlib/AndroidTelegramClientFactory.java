package com.samurai.telegram.tdlib;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.util.Locale;

import telegram.core.api.TelegramClient;
import telegram.core.tdlib.TdLibClientFactory;
import telegram.core.tdlib.TdLibTransportConfig;

/** Android composition root for one persistent Telegram user-account session. */
public final class AndroidTelegramClientFactory {
    private AndroidTelegramClientFactory() { }

    public static TelegramClient create(Context context, int apiId, String apiHash) {
        return create(context, "default", apiId, apiHash);
    }

    public static TelegramClient create(Context context, String accountId, int apiId, String apiHash) {
        if (context == null) throw new IllegalArgumentException("context required");
        String safeAccountId = safeAccountId(accountId);
        if (apiId <= 0) throw new IllegalArgumentException("apiId must be positive");
        if (apiHash == null || apiHash.trim().isEmpty()) throw new IllegalArgumentException("apiHash required");

        Context app = context.getApplicationContext();
        File root = new File(new File(app.getNoBackupFilesDir(), "telegram-core/accounts"), safeAccountId);
        File database = new File(root, "database");
        File files = new File(root, "files");
        if (!database.mkdirs() && !database.isDirectory()) {
            throw new IllegalStateException("Unable to create Telegram database directory");
        }
        if (!files.mkdirs() && !files.isDirectory()) {
            throw new IllegalStateException("Unable to create Telegram files directory");
        }

        byte[] databaseKey = new AndroidDatabaseKeyStore(app).getOrCreate(safeAccountId);
        String deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
        String systemVersion = Build.VERSION.RELEASE == null ? "Android" : Build.VERSION.RELEASE;
        String language = Locale.getDefault().toLanguageTag();
        String appVersion = readVersionName(app);

        TdLibTransportConfig config = new TdLibTransportConfig(
                apiId, apiHash,
                database.getAbsolutePath(), files.getAbsolutePath(),
                databaseKey, language, deviceModel, systemVersion, appVersion
        );
        return TdLibClientFactory.create(config);
    }

    private static String readVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null || info.versionName.trim().isEmpty() ? "0.1.0" : info.versionName;
        } catch (Exception ignored) {
            return "0.1.0";
        }
    }

    private static String safeAccountId(String accountId) {
        if (accountId == null || accountId.trim().isEmpty()) {
            throw new IllegalArgumentException("accountId required");
        }
        String value = accountId.trim();
        if (!value.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException(
                    "accountId must contain only A-Z, a-z, 0-9, '.', '_' or '-'");
        }
        return value;
    }
}
