package com.local.convenidiet;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.ProviderException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecretStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "conveni_diet_deepseek_aes_v1";
    private static final String FILE_NAME = "deepseek_secret_v1";
    private static final byte[] AAD = "conveni-diet-api-key-v1".getBytes(StandardCharsets.UTF_8);

    private final Context context;
    private final AtomicFile file;

    SecretStore(Context context) {
        this.context = context.getApplicationContext();
        this.file = new AtomicFile(new File(this.context.getNoBackupFilesDir(), FILE_NAME));
    }

    boolean hasApiKey() {
        return file.getBaseFile().isFile() && file.getBaseFile().length() > 0;
    }

    void saveApiKey(String apiKey) throws Exception {
        if (apiKey == null) throw new IllegalArgumentException("APIキーが空です");
        String trimmed = apiKey.trim();
        if (trimmed.length() < 8 || trimmed.length() > 512) {
            throw new IllegalArgumentException("APIキーの長さを確認してください");
        }

        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(AAD);
        byte[] encrypted = cipher.doFinal(trimmed.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();

        String payload = "1\n" + Base64.encodeToString(iv, Base64.NO_WRAP) + "\n" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP) + "\n";

        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
            file.finishWrite(out);
        } catch (Exception e) {
            if (out != null) file.failWrite(out);
            throw e;
        }
    }

    String readApiKey() throws Exception {
        if (!hasApiKey()) return null;

        String version;
        String ivB64;
        String cipherB64;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(file.openRead(), StandardCharsets.UTF_8))) {
            version = r.readLine();
            ivB64 = r.readLine();
            cipherB64 = r.readLine();
        }
        if (!"1".equals(version) || ivB64 == null || cipherB64 == null) {
            throw new IllegalStateException("保存データが壊れています");
        }

        byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(cipherB64, Base64.NO_WRAP);
        if (iv.length < 12 || iv.length > 16) throw new IllegalStateException("IVが不正です");

        SecretKey key = getExistingKey();
        if (key == null) throw new IllegalStateException("暗号鍵が見つかりません");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        byte[] plain = cipher.doFinal(encrypted);
        return new String(plain, StandardCharsets.UTF_8);
    }

    void clear() throws Exception {
        file.delete();
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
    }

    private SecretKey getExistingKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        KeyStore.Entry entry = ks.getEntry(ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        return null;
    }

    private SecretKey getOrCreateKey() throws Exception {
        SecretKey existing = getExistingKey();
        if (existing != null) return existing;

        boolean canTryStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE);

        if (canTryStrongBox) {
            try {
                return generateKey(true);
            } catch (ProviderException ignored) {
                // Fall back to the regular Android Keystore. Never fall back to a plaintext key.
                deleteAliasIfPresent();
            }
        }
        return generateKey(false);
    }

    private void deleteAliasIfPresent() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
    }

    private SecretKey generateKey(boolean strongBox) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && strongBox) {
            b.setIsStrongBoxBacked(true);
        }
        // Android 12–14 have documented bugs with unlocked-device-required keys.
        // Use it only on Android 15+; older versions still keep the key inside Android Keystore.
        if (Build.VERSION.SDK_INT >= 35) {
            b.setUnlockedDeviceRequired(true);
        }
        keyGenerator.init(b.build());
        return keyGenerator.generateKey();
    }
}
