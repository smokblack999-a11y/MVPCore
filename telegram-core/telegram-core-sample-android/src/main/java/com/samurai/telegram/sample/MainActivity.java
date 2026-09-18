package com.samurai.telegram.sample;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.samurai.telegram.tdlib.AndroidTelegramClientFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import telegram.core.api.AuthorizationState;
import telegram.core.api.Chat;
import telegram.core.api.MediaSpec;
import telegram.core.api.Message;
import telegram.core.api.Page;
import telegram.core.api.SendOptions;
import telegram.core.api.TelegramClient;
import telegram.core.api.TelegramError;
import telegram.core.api.TransferProgress;

public final class MainActivity extends Activity {
    private static final int PICK_MEDIA = 42;
    private EditText apiId, apiHash, accountId, phone, code, password, chatId, text;
    private TextView state, log;
    private LinearLayout chats;
    private TelegramClient client;
    private Uri pendingMedia;
    private String pendingMime;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        apiId = field("API ID");
        apiHash = field("API hash");
        accountId = field("Account ID", "personal");
        phone = field("Phone + international format");
        code = field("Telegram code");
        password = field("2FA password");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        chatId = field("Selected chat ID");
        text = field("Text");

        root.addView(title("Telegram Core — real user-account acceptance"));
        root.addView(apiId);
        root.addView(apiHash);
        root.addView(accountId);
        root.addView(phone);
        root.addView(button("CONNECT", v -> connect()));
        root.addView(button("SUBMIT PHONE", v -> submitPhone()));
        root.addView(code);
        root.addView(button("SUBMIT CODE", v -> call(client == null ? null : client.submitCode(code.getText().toString()))));
        root.addView(password);
        root.addView(button("SUBMIT 2FA PASSWORD", v -> call(client == null ? null : client.submitPassword(password.getText().toString()))));
        root.addView(button("LOAD CHATS", v -> loadChats()));
        chats = new LinearLayout(this);
        chats.setOrientation(LinearLayout.VERTICAL);
        root.addView(chats);
        root.addView(chatId);
        root.addView(text);
        root.addView(button("SEND TEXT", v -> sendText()));
        root.addView(button("PICK PHOTO / VIDEO", v -> pickMedia()));
        root.addView(button("SEND MEDIA", v -> sendMedia()));
        state = title("State: UNKNOWN");
        root.addView(state);
        log = title("");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private EditText field(String hint) { return field(hint, ""); }
    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        return e;
    }

    private TextView title(String value) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(18);
        v.setPadding(0, 10, 0, 10);
        return v;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(listener);
        return b;
    }

    private void connect() {
        try {
            int id = Integer.parseInt(apiId.getText().toString().trim());
            client = AndroidTelegramClientFactory.create(this, accountId.getText().toString().trim(), id, apiHash.getText().toString().trim());
            client.addListener(new TelegramClient.EventListener() {
                @Override public void onAuthStateChanged(AuthorizationState value) {
                    runOnUiThread(() -> state.setText("State: " + value.type));
                    append("AUTH " + value.type);
                }
                @Override public void onMessage(Message message) { append("MESSAGE " + message.id); }
                @Override public void onTransferProgress(TransferProgress progress) {
                    append("MEDIA " + progress.fileName + " " + progress.completedBytes + "/" + progress.totalBytes);
                }
                @Override public void onError(TelegramError error) { append("ERROR " + error.code + ": " + error.getMessage()); }
            });
            client.getAuthState().thenAccept(s -> append("INITIAL " + s.type));
        } catch (Exception e) { append("CONNECT ERROR: " + e.getMessage()); }
    }

    private void submitPhone() {
        if (client == null) { append("Connect first"); return; }
        call(client.submitPhoneNumber(phone.getText().toString().trim()));
    }

    private void loadChats() {
        if (client == null) { append("Connect first"); return; }
        client.getChats(50, 0).thenAccept(page -> runOnUiThread(() -> renderChats(page)))
                .exceptionally(error -> { append("CHATS ERROR: " + error.getMessage()); return null; });
    }

    private void renderChats(Page<Chat> page) {
        chats.removeAllViews();
        append("CHATS " + page.items.size() + " hasMore=" + page.hasMore);
        for (Chat chat : page.items) {
            Button b = button(chat.title + " [" + chat.id + "]", v -> chatId.setText(String.valueOf(chat.id)));
            chats.addView(b);
        }
    }

    private void sendText() {
        if (client == null) { append("Connect first"); return; }
        try {
            long id = Long.parseLong(chatId.getText().toString().trim());
            call(client.sendText(id, text.getText().toString(), new SendOptions("", false)));
        } catch (Exception e) { append("SEND TEXT ERROR: " + e.getMessage()); }
    }

    private void pickMedia() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_MEDIA);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MEDIA || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        pendingMedia = data.getData();
        pendingMime = getContentResolver().getType(pendingMedia);
        append("MEDIA SELECTED " + pendingMime);
    }

    private void sendMedia() {
        if (client == null) { append("Connect first"); return; }
        if (pendingMedia == null) { append("Pick photo/video first"); return; }
        try {
            long id = Long.parseLong(chatId.getText().toString().trim());
            File local = copyToCache(pendingMedia);
            String mime = pendingMime == null ? "application/octet-stream" : pendingMime;
            call(client.sendMedia(id, new MediaSpec(local.getAbsolutePath(), mime, local.getName(), local.length()), new SendOptions("", false)));
        } catch (Exception e) { append("SEND MEDIA ERROR: " + e.getMessage()); }
    }

    private File copyToCache(Uri uri) throws Exception {
        String name = displayName(uri);
        if (name == null || name.trim().isEmpty()) name = "media.bin";
        File file = new File(getCacheDir(), "telegram-acceptance-" + System.currentTimeMillis() + "-" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IllegalStateException("Unable to open media");
        FileOutputStream output = new FileOutputStream(file);
        byte[] buffer = new byte[64 * 1024];
        try {
            int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
        } finally {
            input.close();
            output.close();
        }
        return file;
    }

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private void call(CompletableFuture<?> future) {
        if (future == null) { append("Client not connected"); return; }
        future.whenComplete((value, error) -> {
            if (error != null) append("CALL ERROR: " + error.getMessage());
            else append("CALL OK");
        });
    }

    private void append(String value) {
        runOnUiThread(() -> log.append(value + "\n"));
    }

    @Override protected void onDestroy() {
        if (client != null) client.close();
        super.onDestroy();
    }
}
