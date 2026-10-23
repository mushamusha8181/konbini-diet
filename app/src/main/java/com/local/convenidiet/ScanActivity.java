package com.local.convenidiet;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;

import java.io.File;
import java.util.List;

public class ScanActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    static final String MODE_NUTRITION = "nutrition";
    static final String MODE_BARCODE = "barcode";
    static final String EXTRA_RESULT = "scan_result";

    private static final int REQ_CAMERA = 41;
    private static final int REQ_CAPTURE = 42;
    private String mode;
    private Uri photoUri;
    private File photoFile;
    private ProgressBar progress;
    private TextView status;
    private boolean waitingForSettings;
    private boolean analyzing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (!MODE_BARCODE.equals(mode)) mode = MODE_NUTRITION;
        if (savedInstanceState != null) {
            waitingForSettings = savedInstanceState.getBoolean("waiting_settings", false);
            analyzing = savedInstanceState.getBoolean("analyzing", false);
            String uri = savedInstanceState.getString("photo_uri", "");
            String path = savedInstanceState.getString("photo_path", "");
            if (!TextUtil.isBlank(uri)) photoUri = Uri.parse(uri);
            if (!TextUtil.isBlank(path)) photoFile = new File(path);
        }
        setContentView(buildUi());
        if (analyzing && photoUri != null && photoFile != null && photoFile.exists()) analyze();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.responsive(this, 14, 10), Ui.responsive(this, 18, 12), Ui.responsive(this, 14, 10), Ui.responsive(this, 28, 20));
        Ui.applySystemInsets(root);
        root.setBackgroundColor(Color.rgb(246, 247, 243));

        root.addView(Ui.title(this, MODE_BARCODE.equals(mode) ? "バーコードを読む" : "栄養表示を読む"));
        root.addView(Ui.spacer(this, Ui.compact(this) ? 6 : 10));
        root.addView(Ui.body(this, MODE_BARCODE.equals(mode)
                ? "商品のバーコードが大きく、ピントが合うように撮影してください。解析は端末内で行います。"
                : "栄養成分表示が正面から大きく写るように撮影してください。文字認識は端末内で行い、画像そのものはDeepSeekへ送りません。"));
        root.addView(Ui.spacer(this, Ui.compact(this) ? 10 : 14));
        android.widget.Button shoot = Ui.primaryButton(this, "カメラで撮影");
        shoot.setOnClickListener(v -> ensurePermissionAndCapture());
        Ui.match(shoot);
        root.addView(shoot);
        root.addView(Ui.spacer(this, Ui.compact(this) ? 8 : 12));
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress);
        status = Ui.body(this, "");
        root.addView(status);
        return root;
    }

    private void ensurePermissionAndCapture() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        cleanupPhoto();
        try {
            File dir = new File(getCacheDir(), "scan_images");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("一時フォルダを作れません");
            File image = new File(dir, "scan_" + System.currentTimeMillis() + ".jpg");
            photoFile = image;
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", image);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) == null) {
                cleanupPhoto();
                Toast.makeText(this, "利用できるカメラアプリがありません", Toast.LENGTH_LONG).show();
                return;
            }
            startActivityForResult(intent, REQ_CAPTURE);
        } catch (Exception e) {
            Toast.makeText(this, "カメラを開けません: " + safe(e), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else if (requestCode == REQ_CAMERA) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("カメラ権限がオフです")
                        .setMessage("Androidのアプリ設定からカメラを許可すると読み取りを使えます。")
                        .setNegativeButton("閉じる", null)
                        .setPositiveButton("アプリ設定を開く", (d, w) -> {
                            waitingForSettings = true;
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                        })
                        .show();
            } else {
                Toast.makeText(this, "カメラ権限がないため読み取りを使えません", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || photoUri == null) {
            cleanupPhoto();
            status.setText("撮影をキャンセルしました");
            return;
        }
        analyze();
    }

    private void analyze() {
        analyzing = true;
        progress.setVisibility(View.VISIBLE);
        status.setText("端末内で解析中…");
        try {
            InputImage image = InputImage.fromFilePath(this, photoUri);
            if (MODE_BARCODE.equals(mode)) analyzeBarcode(image);
            else analyzeText(image);
        } catch (Exception e) {
            fail("画像を読み込めません: " + safe(e));
        }
    }

    private void analyzeBarcode(InputImage image) {
        BarcodeScanner scanner = BarcodeScanning.getClient();
        scanner.process(image)
                .addOnSuccessListener(codes -> {
                    String value = pickBarcode(codes);
                    scanner.close();
                    if (value == null) fail("バーコードを認識できませんでした。近づいて撮り直してください。");
                    else done(value);
                })
                .addOnFailureListener(e -> {
                    scanner.close();
                    fail("バーコード解析に失敗しました: " + safe(e));
                });
    }

    private String pickBarcode(List<Barcode> codes) {
        if (codes == null) return null;
        for (Barcode code : codes) {
            String raw = code.getRawValue();
            if (TextUtil.isBlank(raw)) continue;
            String value = raw.trim();
            if (isValidGtin(value)) return value;
        }
        return null;
    }

    private boolean isValidGtin(String value) {
        if (!value.matches("\\d{8}|\\d{12}|\\d{13}|\\d{14}")) return false;
        int sum = 0;
        boolean timesThree = true;
        for (int i = value.length() - 2; i >= 0; i--) {
            int digit = value.charAt(i) - '0';
            sum += digit * (timesThree ? 3 : 1);
            timesThree = !timesThree;
        }
        int check = (10 - (sum % 10)) % 10;
        return check == value.charAt(value.length() - 1) - '0';
    }

    private void analyzeText(InputImage image) {
        TextRecognizer recognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    String value = text == null ? "" : text.getText().trim();
                    recognizer.close();
                    if (TextUtil.isBlank(value)) fail("文字を認識できませんでした。栄養表示を大きく撮り直してください。");
                    else done(value);
                })
                .addOnFailureListener(e -> {
                    recognizer.close();
                    fail("文字認識に失敗しました: " + safe(e));
                });
    }

    private void done(String value) {
        if (isFinishing() || isDestroyed()) return;
        analyzing = false;
        progress.setVisibility(View.GONE);
        cleanupPhoto();
        Intent out = new Intent();
        out.putExtra(EXTRA_RESULT, value);
        setResult(RESULT_OK, out);
        finish();
    }

    private void fail(String message) {
        if (isFinishing() || isDestroyed()) return;
        analyzing = false;
        progress.setVisibility(View.GONE);
        cleanupPhoto();
        status.setText(message);
    }

    private void cleanupPhoto() {
        if (photoFile != null && photoFile.exists()) {
            try { photoFile.delete(); } catch (Exception ignored) {}
        }
        photoFile = null;
        photoUri = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForSettings && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            waitingForSettings = false;
            launchCamera();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("waiting_settings", waitingForSettings);
        outState.putBoolean("analyzing", analyzing);
        if (photoUri != null) outState.putString("photo_uri", photoUri.toString());
        if (photoFile != null) outState.putString("photo_path", photoFile.getAbsolutePath());
    }

    @Override
    protected void onDestroy() {
        if (!isChangingConfigurations()) cleanupPhoto();
        super.onDestroy();
    }

    private String safe(Exception e) {
        String m = e.getMessage();
        return TextUtil.isBlank(m) ? e.getClass().getSimpleName() : m.replaceAll("[\\r\\n]+", " ");
    }
}
