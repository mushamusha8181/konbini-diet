package com.local.convenidiet;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SecretStore secrets;
    private AppSettings settings;
    private UsageStore usage;
    private EditText keyField;
    private TextView status;
    private Spinner modelSpinner;
    private CheckBox thinkingCheck;
    private ProgressBar progress;
    private Button testButton;
    private TextView usageText;
    private EditText usdJpyField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        secrets = new SecretStore(this);
        settings = new AppSettings(this);
        usage = new UsageStore(this);
        setContentView(buildUi());
        refreshStatus();
        refreshUsage();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        Ui.applySystemInsets(scroll);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 247, 243));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.responsive(this, 14, 10), Ui.responsive(this, 16, 10), Ui.responsive(this, 14, 10), Ui.responsive(this, 28, 20));
        scroll.addView(root);

        TextView back = Ui.body(this, "← 戻る");
        back.setTextSize(14);
        back.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(Ui.title(this, "設定・セキュリティ"));
        root.addView(Ui.spacer(this, 8));
        root.addView(Ui.body(this, "APIキーはAndroid Keystoreで暗号化し、端末のバックアップ対象外へ保存します。画面のスクリーンショットもこの設定画面では禁止しています。"));
        root.addView(Ui.spacer(this, Ui.compact(this) ? 14 : 20));

        root.addView(Ui.heading(this, "DeepSeek APIキー"));
        root.addView(Ui.spacer(this, 8));
        keyField = Ui.field(this, "変更するときだけ入力");
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setTransformationMethod(PasswordTransformationMethod.getInstance());
        keyField.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING | EditorInfo.IME_ACTION_DONE);
        keyField.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        keyField.setAutofillHints((String[]) null);
        Ui.match(keyField);
        root.addView(keyField);

        CheckBox showKey = new CheckBox(this);
        showKey.setText("入力中だけ表示する");
        showKey.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int pos = keyField.getSelectionStart();
            keyField.setTransformationMethod(isChecked ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
            keyField.setSelection(Math.max(0, Math.min(pos, keyField.length())));
        });
        root.addView(showKey);

        status = Ui.body(this, "");
        root.addView(status);
        root.addView(Ui.spacer(this, 8));

        Button save = Ui.primaryButton(this, "APIキーを暗号化して保存 / 更新");
        Ui.match(save);
        save.setOnClickListener(v -> saveKey());
        root.addView(save);
        root.addView(Ui.spacer(this, 10));

        Button delete = Ui.compactButton(this, "保存済みAPIキーを削除");
        delete.setOnClickListener(v -> confirmDelete());
        root.addView(delete);

        root.addView(Ui.spacer(this, Ui.compact(this) ? 12 : 16));
        root.addView(Ui.heading(this, "AIモデル"));
        root.addView(Ui.spacer(this, 8));
        modelSpinner = new Spinner(this);
        String[] modelLabels = {"DeepSeek V4.1 Flash（普段使い）", "DeepSeek V4 Pro（高性能）"};
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modelLabels);
        modelSpinner.setAdapter(modelAdapter);
        modelSpinner.setSelection("deepseek-v4-pro".equals(settings.model()) ? 1 : 0);
        root.addView(modelSpinner);

        thinkingCheck = new CheckBox(this);
        thinkingCheck.setText("深く考える（精度優先・トークン消費増）");
        thinkingCheck.setChecked(settings.thinking());
        root.addView(thinkingCheck);

        Button saveAi = Ui.compactButton(this, "AI設定を保存");
        saveAi.setOnClickListener(v -> {
            settings.setModel(modelSpinner.getSelectedItemPosition() == 1 ? "deepseek-v4-pro" : "deepseek-flash");
            settings.setThinking(thinkingCheck.isChecked());
            Toast.makeText(this, "AI設定を保存しました", Toast.LENGTH_SHORT).show();
        });
        root.addView(saveAi);

        root.addView(Ui.spacer(this, 14));
        testButton = Ui.primaryButton(this, "DeepSeekとの接続をテスト");
        testButton.setOnClickListener(v -> testConnection());
        Ui.match(testButton);
        root.addView(testButton);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress);

        root.addView(Ui.spacer(this, Ui.compact(this) ? 12 : 18));
        root.addView(Ui.heading(this, "DeepSeek使用量・料金"));
        root.addView(Ui.spacer(this, 6));
        root.addView(Ui.body(this, "この端末の『コンビニダイエット』から発生したAPI使用量だけを集計します。料金はDeepSeek公式価格（" + UsageStore.PRICING_AS_OF + "確認）からピーク/オフピークの範囲で推定し、実請求額そのものではありません。"));
        root.addView(Ui.spacer(this, 8));
        usageText = Ui.body(this, "");
        root.addView(usageText);
        root.addView(Ui.spacer(this, 8));

        usdJpyField = Ui.field(this, "例: 150");
        usdJpyField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        usdJpyField.setText(settings.usdJpyRate());
        usdJpyField.setSingleLine(true);
        root.addView(Ui.body(this, "円換算レート（1 USD = 何円）"));
        root.addView(usdJpyField);
        Button saveRate = Ui.compactButton(this, "円換算レートを保存");
        saveRate.setOnClickListener(v -> saveExchangeRate());
        root.addView(saveRate);
        root.addView(Ui.spacer(this, 8));

        Button resetMonth = Ui.compactButton(this, "今月の使用量をリセット");
        resetMonth.setOnClickListener(v -> confirmResetUsage(true));
        root.addView(resetMonth);
        root.addView(Ui.spacer(this, 6));
        Button resetAll = Ui.compactButton(this, "累計使用量をリセット");
        resetAll.setOnClickListener(v -> confirmResetUsage(false));
        root.addView(resetAll);
        root.addView(Ui.spacer(this, 6));
        root.addView(Ui.body(this, "※リセットされるのはこのアプリ内の表示カウンターだけです。DeepSeekアカウントの請求・残高・利用履歴は変わりません。"));

        root.addView(Ui.spacer(this, Ui.compact(this) ? 12 : 18));
        root.addView(Ui.heading(this, "権限"));
        root.addView(Ui.spacer(this, 8));
        root.addView(Ui.body(this,
                "・カメラ: 栄養表示とバーコードの撮影読み取りに使用（初回起動時に確認）\n" +
                "・インターネット: DeepSeekの会話APIとSearch APIに使用（Androidの通常権限のため許可ダイアログなし）\n" +
                "・位置情報、連絡先、マイク、常時ストレージアクセスは使用しません\n" +
                "・撮影画像のOCR/バーコード解析は端末内で実行し、画像そのものをDeepSeekへ送信しません"));
        root.addView(Ui.spacer(this, Ui.compact(this) ? 12 : 18));

        root.addView(Ui.heading(this, "このアプリの通信ルール"));
        root.addView(Ui.spacer(this, 8));
        root.addView(Ui.body(this,
                "・アプリ自身がAPIキーを送る通信先は https://api.deepseek.com に固定\n" +
                "・DeepSeek Searchは各コンビニ公式ドメインだけを検索対象にし、検索結果のURLも公式ドメインか再検証\n" +
                "・HTTP平文通信は禁止、リダイレクトは禁止\n" +
                "・APIキーは認証ヘッダーだけに使用し、AIへの会話本文や商品DBには入れない\n" +
                "・広告SDK、独自のアクセス解析SDK、クラッシュ送信SDKなし\n" +
                "・APIキーをログ出力しない\n" +
                "・保存データのクラウドバックアップを無効化"));
        root.addView(Ui.spacer(this, 12));
        root.addView(Ui.body(this,
                "注意: DeepSeek API利用時はAPIキーと相談内容、商品DB更新時は検索クエリと公式ページ本文がDeepSeekへ送信されます。" +
                "OCR/バーコードにはバンドル版Google ML Kitを使用します。画像と認識結果をアプリからGoogleへ送る処理はありませんが、ML Kit SDKは端末・アプリ情報や性能指標などの診断/利用統計を収集する場合があります。APIキーをML Kitへ渡す処理はありません。"));
        return scroll;
    }

    private void refreshStatus() {
        status.setText(secrets.hasApiKey() ? "状態: APIキー保存済み（値は再表示しません）" : "状態: APIキー未設定");
    }

    private void saveKey() {
        String key = keyField.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(this, "保存するAPIキーを入力してください", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            secrets.saveApiKey(key);
            keyField.setText("");
            keyField.clearFocus();
            refreshStatus();
            Toast.makeText(this, "暗号化して保存しました", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存できませんでした: " + safe(e), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("APIキーを削除しますか？")
                .setMessage("暗号化データとKeystoreの暗号鍵を削除します。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除", (d, w) -> {
                    try {
                        secrets.clear();
                        keyField.setText("");
                        refreshStatus();
                        Toast.makeText(this, "削除しました", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "削除できませんでした", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void testConnection() {
        settings.setModel(modelSpinner.getSelectedItemPosition() == 1 ? "deepseek-v4-pro" : "deepseek-flash");
        settings.setThinking(thinkingCheck.isChecked());
        progress.setVisibility(View.VISIBLE);
        testButton.setEnabled(false);
        executor.execute(() -> {
            try {
                String key = secrets.readApiKey();
                if (key == null) throw new IllegalStateException("APIキーが未設定です");
                String result = new DeepSeekClient(SettingsActivity.this).test(key, settings.model());
                runOnUiThread(() -> { Toast.makeText(this, "接続成功: " + result, Toast.LENGTH_LONG).show(); refreshUsage(); });
            } catch (DeepSeekClient.ApiException e) {
                runOnUiThread(() -> Toast.makeText(this, "APIエラー (" + e.status + "): " + safe(e), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "接続失敗: " + safe(e), Toast.LENGTH_LONG).show());
            } finally {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    testButton.setEnabled(true);
                });
            }
        });
    }

    private void refreshUsage() {
        if (usageText == null) return;
        UsageStore.Snapshot month = usage.month();
        UsageStore.Snapshot all = usage.cumulative();
        double rate = parseExchangeRate(settings.usdJpyRate(), 150.0);
        usageText.setText(formatUsageBlock("今月（" + month.month + "）", month, rate) + "\n\n" +
                formatUsageBlock("累計（最後の累計リセット以降）", all, rate));
    }

    private String formatUsageBlock(String title, UsageStore.Snapshot s, double rate) {
        return title + "\n" +
                "入力: " + UsageStore.formatTokens(s.inputTokens()) + " tokens" +
                "（キャッシュ命中 " + UsageStore.formatTokens(s.cacheHitTokens) +
                " / 非命中 " + UsageStore.formatTokens(s.cacheMissTokens) + "）\n" +
                "出力: " + UsageStore.formatTokens(s.outputTokens) + " tokens" +
                (s.reasoningTokens > 0 ? "（推論 " + UsageStore.formatTokens(s.reasoningTokens) + "）" : "") + "\n" +
                "合計: " + UsageStore.formatTokens(s.totalTokens) + " tokens\n" +
                "AI呼び出し: " + s.chatCalls + "回 / Search: " + s.searchCalls + "回 / Fetch: " + s.fetchCalls + "回\n" +
                "推定料金: " + UsageStore.formatUsdRange(s) + "（" + UsageStore.formatYenRange(s, rate) + "）";
    }

    private void saveExchangeRate() {
        String raw = usdJpyField == null ? "" : usdJpyField.getText().toString().trim();
        double value = parseExchangeRate(raw, -1);
        if (value < 50 || value > 300) {
            Toast.makeText(this, "50〜300円/USDの範囲で入力してください", Toast.LENGTH_SHORT).show();
            return;
        }
        settings.setUsdJpyRate(String.valueOf(value));
        refreshUsage();
        Toast.makeText(this, "円換算レートを保存しました", Toast.LENGTH_SHORT).show();
    }

    private double parseExchangeRate(String raw, double fallback) {
        try {
            double v = Double.parseDouble(raw);
            return Double.isFinite(v) ? v : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private void confirmResetUsage(boolean monthOnly) {
        String title = monthOnly ? "今月の使用量をリセットしますか？" : "累計使用量をリセットしますか？";
        String message = monthOnly
                ? "今月のトークン数・API回数・推定料金の表示を0にします。累計は残ります。DeepSeek側の請求履歴は変わりません。"
                : "累計のトークン数・API回数・推定料金の表示を0にします。今月の表示は残ります。DeepSeek側の請求履歴は変わりません。";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("リセット", (d, w) -> {
                    if (monthOnly) usage.resetMonth(); else usage.resetCumulative();
                    refreshUsage();
                    Toast.makeText(this, "表示カウンターをリセットしました", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private String safe(Exception e) {
        String m = e.getMessage();
        if (TextUtil.isBlank(m)) return e.getClass().getSimpleName();
        m = m.replaceAll("[\\r\\n]+", " ");
        return m.length() > 180 ? m.substring(0, 180) : m;
    }


    @Override
    protected void onResume() {
        super.onResume();
        refreshUsage();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (keyField != null) keyField.setText("");
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
