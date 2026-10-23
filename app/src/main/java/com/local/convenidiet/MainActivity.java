package com.local.convenidiet;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_INITIAL_CAMERA = 700;
    private static final int REQ_SCAN_NUTRITION = 701;
    private static final int REQ_SCAN_BARCODE = 702;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SecretStore secrets;
    private AppSettings settings;
    private ProductCatalog catalog;
    private FavoriteStore favorites;
    private DailyIntakeStore daily;
    private UsageStore usage;

    private EditText calories;
    private EditText protein;
    private EditText saltTarget;
    private EditText sugarTarget;
    private EditText budget;
    private EditText goalNote;
    private EditText avoidFoods;
    private EditText products;
    private Spinner store;
    private Spinner mode;
    private Spinner concern;
    private TextView keyStatus;
    private TextView usageStatus;
    private TextView catalogStatus;
    private TextView dailyStatus;
    private TextView result;
    private ProgressBar progress;
    private Button askButton;
    private Button updateDbButton;
    private String pendingBarcode = "";

    private static final String[] STORES = new String[]{
            "指定なし", "セブン-イレブン", "ファミリーマート", "ローソン", "ミニストップ", "その他"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        secrets = new SecretStore(this);
        settings = new AppSettings(this);
        catalog = new ProductCatalog(this);
        favorites = new FavoriteStore(this);
        daily = new DailyIntakeStore(this);
        usage = new UsageStore(this);
        setContentView(buildUi());
        maybeRequestInitialPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshKeyStatus();
        refreshCatalogStatus();
        refreshDailyStatus();
        refreshUsageStatus();
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

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.title(this, "コンビニダイエット"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button settingsButton = Ui.compactButton(this, "設定");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(settingsButton);
        root.addView(top);
        root.addView(Ui.spacer(this, Ui.compact(this) ? 4 : 8));
        root.addView(Ui.body(this, "同梱した公式商品DBとDeepSeek Searchで確認した各コンビニ公式サイトの商品だけを候補にし、低カロリーを基本に塩分・糖分・予算・今日の残りを見ながらAIが組み合わせます。"));
        root.addView(Ui.spacer(this, Ui.compact(this) ? 6 : 10));
        keyStatus = Ui.body(this, "");
        root.addView(keyStatus);
        usageStatus = Ui.body(this, "");
        usageStatus.setTextSize(Ui.compact(this) ? 12 : 13);
        root.addView(usageStatus);
        root.addView(Ui.spacer(this, Ui.compact(this) ? 8 : 12));

        LinearLayout card = card();
        root.addView(card);

        card.addView(Ui.heading(this, "1. どう相談する？"));
        card.addView(Ui.spacer(this, 8));
        mode = spinner(new String[]{"1食を組む", "1日分を組む", "今の食事を採点"});
        card.addView(mode);

        section(card, "2. コンビニ");
        store = spinner(STORES);
        card.addView(store);

        section(card, "3. 食事で気になること");
        card.addView(Ui.body(this, "低カロリーは基本。さらに気になるものを選んでください。"));
        card.addView(Ui.spacer(this, 8));
        String[] concernItems = {"塩分が気になる", "糖分が気になる", "塩分・糖分の両方が気になる", "特に気にしない"};
        concern = spinner(concernItems);
        select(concern, concernItems, settings.concern());
        card.addView(concern);

        section(card, "4. 1日の目標（わかる範囲だけ）");
        calories = addNumber(card, "目標カロリー 例: 1800", settings.calories());
        protein = addNumber(card, "たんぱく質目標(g) 例: 100", settings.protein());
        saltTarget = addNumber(card, "塩分の目安(g) 例: 6.0", settings.saltTarget());
        sugarTarget = addNumber(card, "糖分の目安(g) 任意", settings.sugarTarget());
        budget = addNumber(card, "今回の予算(円) 例: 700", settings.budget());

        section(card, "5. 希望・食べないもの");
        goalNote = Ui.field(this, "例: 腹持ち優先、夜遅め、温かい物がほしい");
        goalNote.setSingleLine(false);
        goalNote.setMinLines(2);
        goalNote.setText(settings.goalNote());
        Ui.match(goalNote);
        card.addView(goalNote);
        card.addView(Ui.spacer(this, 8));
        avoidFoods = Ui.field(this, "苦手・食べないもの 例: ゆで卵、ツナ、辛い物");
        avoidFoods.setSingleLine(false);
        avoidFoods.setMinLines(2);
        avoidFoods.setText(settings.avoidFoods());
        Ui.match(avoidFoods);
        card.addView(avoidFoods);

        section(card, "6. 今日あとどれくらい？");
        dailyStatus = Ui.body(this, "");
        card.addView(dailyStatus);
        card.addView(Ui.spacer(this, 8));
        LinearLayout dailyButtons = horizontalButtons();
        Button addToday = smallButton("食べた分を記録", v -> showDailyEntryDialog());
        Button resetToday = smallButton("今日をリセット", v -> confirmResetDaily());
        dailyButtons.addView(addToday, weight());
        dailyButtons.addView(resetToday, weight());
        card.addView(dailyButtons);

        section(card, "7. 商品DB");
        catalogStatus = Ui.body(this, "");
        card.addView(catalogStatus);
        card.addView(Ui.spacer(this, 8));
        updateDbButton = Ui.compactButton(this, "公式サイトから商品DBを更新");
        updateDbButton.setOnClickListener(v -> refreshOfficialDatabase());
        Ui.match(updateDbButton);
        card.addView(updateDbButton);
        card.addView(Ui.spacer(this, 8));
        Button nutritionScan = Ui.primaryButton(this, "栄養表示を撮って商品登録");
        nutritionScan.setOnClickListener(v -> openScanner(ScanActivity.MODE_NUTRITION, REQ_SCAN_NUTRITION));
        Ui.match(nutritionScan);
        card.addView(nutritionScan);
        card.addView(Ui.spacer(this, 8));
        LinearLayout scanRow = horizontalButtons();
        scanRow.addView(smallButton("バーコードを読む", v -> openScanner(ScanActivity.MODE_BARCODE, REQ_SCAN_BARCODE)), weight());
        scanRow.addView(smallButton("登録商品を見る", v -> showCatalog()), weight());
        card.addView(scanRow);
        card.addView(Ui.spacer(this, 8));
        card.addView(Ui.body(this, "初期DBには公式サイトで確認した実商品を同梱しています。更新時はDeepSeek Searchで選択したコンビニの公式サイトだけを検索し、確認できた商品を端末DBへ追加・更新します。OCR・バーコード解析の画像自体はDeepSeekへ送りません。"));

        section(card, "8. 今の食事・手入力メモ");
        products = Ui.field(this, "採点したい食事や、手入力で登録したい栄養表示を貼り付け");
        products.setSingleLine(false);
        products.setGravity(Gravity.TOP | Gravity.START);
        products.setMinLines(6);
        products.setMaxLines(12);
        products.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Ui.match(products);
        card.addView(products);
        card.addView(Ui.spacer(this, 8));
        Button manualRegister = Ui.compactButton(this, "この入力内容を商品DBへ登録");
        manualRegister.setOnClickListener(v -> registerProductDialog(products.getText().toString(), pendingBarcode));
        card.addView(manualRegister);

        root.addView(Ui.spacer(this, 16));
        askButton = Ui.primaryButton(this, "AIに考えてもらう");
        Ui.match(askButton);
        askButton.setOnClickListener(v -> ask());
        root.addView(askButton);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress);
        root.addView(Ui.spacer(this, 8));
        root.addView(Ui.body(this, "AI相談時は登録商品の文字情報と相談条件をDeepSeekへ送信します。APIキーは認証ヘッダーにだけ使用します。撮影画像そのものは送信しません。"));

        root.addView(Ui.spacer(this, 20));
        root.addView(Ui.heading(this, "AIの提案"));
        root.addView(Ui.spacer(this, 8));
        result = Ui.body(this, "ここに結果が出ます。");
        result.setTextSize(Ui.compact(this) ? 14 : 16);
        result.setTextIsSelectable(true);
        result.setBackgroundResource(R.drawable.card_bg);
        result.setPadding(Ui.responsive(this, 16, 11), Ui.responsive(this, 16, 11), Ui.responsive(this, 16, 11), Ui.responsive(this, 16, 11));
        root.addView(result, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.spacer(this, 10));
        LinearLayout resultActions = horizontalButtons();
        resultActions.addView(smallButton("これ売ってない", v -> showUnavailableDialog()), weight());
        resultActions.addView(smallButton("お気に入り保存", v -> saveFavoriteDialog()), weight());
        root.addView(resultActions);
        root.addView(Ui.spacer(this, 8));
        Button favoriteList = Ui.compactButton(this, "お気に入りを見る");
        favoriteList.setOnClickListener(v -> showFavorites());
        root.addView(favoriteList);

        root.addView(Ui.spacer(this, 14));
        root.addView(Ui.body(this, "※ 商品の栄養値はラベル表示を優先してください。糖類と糖質は同じ意味ではないため、表示がない数値をAIに推測させない設計です。"));
        return scroll;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(Ui.responsive(this, 14, 10), Ui.responsive(this, 14, 10), Ui.responsive(this, 14, 10), Ui.responsive(this, 14, 10));
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void section(LinearLayout card, String title) {
        card.addView(Ui.spacer(this, Ui.compact(this) ? 8 : 12));
        card.addView(Ui.heading(this, title));
        card.addView(Ui.spacer(this, Ui.compact(this) ? 5 : 8));
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        return s;
    }

    private void select(Spinner spinner, String[] values, String wanted) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(wanted)) spinner.setSelection(i);
    }

    private EditText addNumber(LinearLayout card, String hint, String value) {
        EditText e = Ui.numberField(this, hint);
        e.setText(value);
        Ui.match(e);
        card.addView(e);
        card.addView(Ui.spacer(this, 8));
        return e;
    }

    private LinearLayout horizontalButtons() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(Ui.responsive(this, 3, 2), 0, Ui.responsive(this, 3, 2), 0);
        return p;
    }

    private Button smallButton(String text, View.OnClickListener listener) {
        Button b = Ui.compactButton(this, text);
        b.setOnClickListener(listener);
        return b;
    }

    private void maybeRequestInitialPermissions() {
        if (settings.initialPermissionAsked()) return;
        new AlertDialog.Builder(this)
                .setTitle("初回の権限設定")
                .setMessage("栄養表示とバーコードを撮影して読み取るため、カメラ権限を使用します。画像は端末内で解析し、画像そのものをDeepSeekへ送りません。\n\n位置情報・連絡先・常時ストレージ権限は使いません。")
                .setCancelable(false)
                .setPositiveButton("カメラ権限を確認", (d, w) -> { settings.setInitialPermissionAsked(true); requestCameraPermission(); })
                .show();
    }

    private void requestCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_INITIAL_CAMERA);
        }
    }

    private void openScanner(String scanMode, int requestCode) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            boolean permanentlyDenied = settings.initialPermissionAsked() && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
            if (permanentlyDenied) {
                showCameraSettingsDialog();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("カメラ権限が必要です")
                        .setMessage("撮影読み取りを使うときだけカメラを使用します。")
                        .setNegativeButton("キャンセル", null)
                        .setPositiveButton("許可する", (d, w) -> requestCameraPermission())
                        .show();
            }
            return;
        }
        Intent i = new Intent(this, ScanActivity.class);
        i.putExtra(ScanActivity.EXTRA_MODE, scanMode);
        startActivityForResult(i, requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_INITIAL_CAMERA) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) return;
        if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) showCameraSettingsDialog();
    }

    private void showCameraSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("カメラ権限がオフです")
                .setMessage("栄養表示・バーコード読み取りを使う場合は、Androidのアプリ設定からカメラを許可してください。AI提案など他の機能はカメラ権限なしでも使えます。")
                .setNegativeButton("閉じる", null)
                .setPositiveButton("アプリ設定を開く", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        String value = data.getStringExtra(ScanActivity.EXTRA_RESULT);
        if (TextUtil.isBlank(value)) return;
        if (requestCode == REQ_SCAN_NUTRITION) {
            products.setText(value);
            registerProductDialog(value, pendingBarcode);
        } else if (requestCode == REQ_SCAN_BARCODE) {
            pendingBarcode = value.trim();
            ProductCatalog.Product found = catalog.findBarcode(pendingBarcode);
            if (found != null) {
                products.setText(found.name + "\n" + found.nutritionText);
                Toast.makeText(this, "登録済み商品を見つけました: " + found.name, Toast.LENGTH_LONG).show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("未登録のバーコードです")
                        .setMessage("バーコード: " + pendingBarcode + "\n続けて栄養表示を撮影すると、この商品を端末内DBへ登録できます。")
                        .setNegativeButton("あとで", null)
                        .setPositiveButton("栄養表示を撮る", (d, w) -> openScanner(ScanActivity.MODE_NUTRITION, REQ_SCAN_NUTRITION))
                        .show();
            }
        }
    }

    private void registerProductDialog(String nutrition, String barcode) {
        LinearLayout box = dialogBox();
        EditText name = Ui.field(this, "商品名");
        box.addView(name);
        box.addView(Ui.spacer(this, 8));
        Spinner storeChoice = spinner(STORES);
        storeChoice.setSelection(store.getSelectedItemPosition());
        box.addView(storeChoice);
        box.addView(Ui.spacer(this, 8));
        EditText code = Ui.field(this, "バーコード（任意）");
        code.setText(barcode == null ? "" : barcode);
        box.addView(code);
        box.addView(Ui.spacer(this, 8));
        EditText nutritionEdit = Ui.field(this, "栄養表示");
        nutritionEdit.setSingleLine(false);
        nutritionEdit.setMinLines(5);
        nutritionEdit.setMaxLines(12);
        nutritionEdit.setText(nutrition == null ? "" : nutrition);
        box.addView(nutritionEdit);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("商品を端末内DBへ登録")
                .setView(box)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("登録", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String productName = name.getText().toString().trim();
            if (TextUtil.isBlank(productName)) {
                name.setError("商品名を入力してください");
                return;
            }
            catalog.addManual(String.valueOf(storeChoice.getSelectedItem()), productName, code.getText().toString(), nutritionEdit.getText().toString());
            pendingBarcode = "";
            refreshCatalogStatus();
            Toast.makeText(this, "端末内の商品DBへ登録しました", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showCatalog() {
        List<ProductCatalog.Product> items = catalog.all();
        if (items.isEmpty()) {
            Toast.makeText(this, "登録商品はまだありません", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = items.get(i).store + "｜" + items.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("登録商品 " + items.size() + "件")
                .setItems(labels, (d, which) -> showProductDetail(items.get(which)))
                .setNegativeButton("閉じる", null)
                .show();
    }

    private void showProductDetail(ProductCatalog.Product product) {
        StringBuilder detail = new StringBuilder();
        detail.append("コンビニ: ").append(product.store);
        if (!TextUtil.isBlank(product.barcode)) detail.append("\nバーコード: ").append(product.barcode);
        detail.append("\n価格: ").append(product.priceYen == null ? "不明" : NutritionParser.format(product.priceYen) + "円");
        detail.append("\n").append(NutritionParser.summarize(product));
        if (!TextUtil.isBlank(product.region)) detail.append("\n販売地域: ").append(product.region);
        if (!TextUtil.isBlank(product.verifiedAt)) detail.append("\n確認日: ").append(product.verifiedAt);
        if (!TextUtil.isBlank(product.sourceUrl)) detail.append("\n情報元: ").append(product.sourceUrl);
        new AlertDialog.Builder(this)
                .setTitle(product.name)
                .setMessage(detail.toString())
                .setNeutralButton("今日食べたに追加", (d, w) -> {
                    daily.addProduct(product);
                    refreshDailyStatus();
                    Toast.makeText(this, "今日の記録へ追加しました。不明な栄養値は不明のまま管理します。", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("閉じる", null)
                .setPositiveButton("削除", (d, w) -> {
                    catalog.remove(product.id);
                    refreshCatalogStatus();
                })
                .show();
    }

    private void showDailyEntryDialog() {
        LinearLayout box = dialogBox();
        EditText cal = dialogNumber(box, "食べた分のカロリー(kcal)");
        EditText pro = dialogNumber(box, "たんぱく質(g)");
        EditText salt = dialogNumber(box, "塩分・食塩相当量(g)");
        EditText sugar = dialogNumber(box, "糖分(g) 分かる場合だけ");
        new AlertDialog.Builder(this)
                .setTitle("今日食べた分を追加")
                .setView(box)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("記録", (d, w) -> {
                    daily.add(numOrNull(cal), numOrNull(pro), numOrNull(salt), numOrNull(sugar));
                    refreshDailyStatus();
                })
                .show();
    }

    private EditText dialogNumber(LinearLayout box, String hint) {
        EditText e = Ui.numberField(this, hint);
        box.addView(e);
        box.addView(Ui.spacer(this, 6));
        return e;
    }

    private void confirmResetDaily() {
        new AlertDialog.Builder(this)
                .setTitle("今日の記録をリセット？")
                .setMessage("今日入力したカロリー・たんぱく質・塩分・糖分を0に戻します。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("リセット", (d, w) -> {
                    daily.reset();
                    refreshDailyStatus();
                }).show();
    }

    private void saveFavoriteDialog() {
        String answer = result.getText().toString().trim();
        if (TextUtil.isBlank(answer) || "ここに結果が出ます。".equals(answer) || "考えています…".equals(answer)) {
            Toast.makeText(this, "先にAIの提案を出してください", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText name = Ui.field(this, "例: ローソン昼A");
        name.setText(String.valueOf(store.getSelectedItem()) + " お気に入り");
        new AlertDialog.Builder(this)
                .setTitle("お気に入りセット保存")
                .setView(name)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("保存", (d, w) -> {
                    String selectedStore = String.valueOf(store.getSelectedItem());
                    ArrayList<String> matchedIds = new ArrayList<>();
                    for (ProductCatalog.Product p : catalog.forStore(selectedStore)) {
                        if (!TextUtil.isBlank(p.name) && answer.contains(p.name)) matchedIds.add(p.id);
                    }
                    favorites.add(name.getText().toString(), selectedStore, answer, matchedIds);
                    Toast.makeText(this, matchedIds.isEmpty() ? "お気に入りに保存しました（商品紐付けなし）" : "お気に入りに保存しました", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void showFavorites() {
        List<FavoriteStore.Favorite> items = favorites.all();
        if (items.isEmpty()) {
            Toast.makeText(this, "お気に入りはまだありません", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = items.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("お気に入り")
                .setItems(labels, (d, which) -> showFavoriteDetail(items.get(which)))
                .setNegativeButton("閉じる", null)
                .show();
    }

    private void showFavoriteDetail(FavoriteStore.Favorite item) {
        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setMessage(item.text)
                .setNeutralButton("今日食べたに追加", (d, w) -> {
                    if (item.productIds.isEmpty()) {
                        Toast.makeText(this, "旧形式または商品名を紐付けできないお気に入りです", Toast.LENGTH_LONG).show();
                        return;
                    }
                    int added = 0;
                    for (String id : item.productIds) {
                        ProductCatalog.Product p = catalog.findById(id);
                        if (p != null) { daily.addProduct(p); added++; }
                    }
                    refreshDailyStatus();
                    Toast.makeText(this, added + "商品を今日の記録へ追加しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("閉じる", null)
                .setPositiveButton("削除", (d, w) -> favorites.remove(item.id))
                .show();
    }

    private void showUnavailableDialog() {
        if (!secrets.hasApiKey()) {
            showNeedKey();
            return;
        }
        EditText item = Ui.field(this, "店頭になかった商品名");
        new AlertDialog.Builder(this)
                .setTitle("これ売ってない")
                .setMessage("店頭になかった商品を入力すると、登録済み商品の中だけで代用品を探します。")
                .setView(item)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("代わりを探す", (d, w) -> requestReplacement(item.getText().toString().trim()))
                .show();
    }

    private void requestReplacement(String unavailable) {
        if (TextUtil.isBlank(unavailable)) return;
        saveSettings();
        final String selectedStore = String.valueOf(store.getSelectedItem());
        final String selectedConcern = String.valueOf(concern.getSelectedItem());
        final String avoid = avoidFoods.getText().toString();
        final String remaining = daily.promptRemaining(calories.getText().toString(), protein.getText().toString(), saltTarget.getText().toString(), sugarTarget.getText().toString());
        final String previous = result.getText().toString();
        setBusy(true);
        result.setText("商品DBを確認して代用品を探しています…");
        executor.execute(() -> {
            String refreshNote = "";
            try {
                String apiKey = secrets.readApiKey();
                if (apiKey == null) throw new IllegalStateException("APIキーがありません");
                if (supportsOfficialSearch(selectedStore)) {
                    try {
                        ProductDiscoveryService.RefreshResult rr = new ProductDiscoveryService(MainActivity.this).refresh(apiKey, settings.model(), selectedStore, catalog);
                        refreshNote = rr.accepted > 0 ? "（公式サイトから" + rr.accepted + "件を更新）\n" : "";
                    } catch (Exception searchError) {
                        refreshNote = "（公式サイト更新に失敗したため保存済みDBを使用）\n";
                    }
                }
                String cat = catalog.promptCatalog(selectedStore);
                if ("登録商品なし".equals(cat)) throw new IllegalStateException("このコンビニの商品DBがありません");
                String prompt = DietPromptBuilder.replacementPrompt(
                        selectedStore, selectedConcern, unavailable, avoid, remaining, cat, previous);
                String answer = new DeepSeekClient(MainActivity.this).ask(apiKey, settings.model(), settings.thinking(), DietPromptBuilder.systemPrompt(), prompt);
                final String shown = refreshNote + answer;
                runOnUiThread(() -> { result.setText(shown); refreshCatalogStatus(); });
            } catch (DeepSeekClient.ApiException e) {
                runOnUiThread(() -> showApiError(e));
            } catch (Exception e) {
                runOnUiThread(() -> result.setText("エラー: " + safe(e)));
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private void ask() {
        if (!secrets.hasApiKey()) { showNeedKey(); return; }
        if (products.getText().length() > 45000 || goalNote.getText().length() > 4000) {
            Toast.makeText(this, "入力が長すぎます。少し短くしてください。", Toast.LENGTH_LONG).show();
            return;
        }
        saveSettings();
        final String selectedMode = String.valueOf(mode.getSelectedItem());
        final String selectedStore = String.valueOf(store.getSelectedItem());
        final String selectedConcern = String.valueOf(concern.getSelectedItem());
        final String cal = calories.getText().toString().trim();
        final String pro = protein.getText().toString().trim();
        final String salt = saltTarget.getText().toString().trim();
        final String sugar = sugarTarget.getText().toString().trim();
        final String budgetText = budget.getText().toString().trim();
        final String note = goalNote.getText().toString().trim();
        final String avoid = avoidFoods.getText().toString().trim();
        final String meal = products.getText().toString().trim();

        setBusy(true);
        result.setText(supportsOfficialSearch(selectedStore) && !"今の食事を採点".equals(selectedMode)
                ? "公式商品情報を確認してから考えています…" : "考えています…");
        executor.execute(() -> {
            String refreshNote = "";
            try {
                String apiKey = secrets.readApiKey();
                if (apiKey == null) throw new IllegalStateException("APIキーがありません");
                if (!"今の食事を採点".equals(selectedMode) && supportsOfficialSearch(selectedStore)) {
                    try {
                        ProductDiscoveryService.RefreshResult rr = new ProductDiscoveryService(MainActivity.this).refresh(apiKey, settings.model(), selectedStore, catalog);
                        if (rr.accepted > 0) refreshNote = "（公式サイトから商品DBを" + rr.accepted + "件更新）\n";
                    } catch (Exception searchError) {
                        refreshNote = "（公式サイト更新に失敗したため保存済みDBを使用）\n";
                    }
                }
                String cat = catalog.promptCatalog(selectedStore);
                if (!"今の食事を採点".equals(selectedMode) && "登録商品なし".equals(cat)) {
                    throw new IllegalStateException("このコンビニの商品DBがありません。商品DB更新または手登録をしてください。");
                }
                String prompt = DietPromptBuilder.userPrompt(
                        selectedMode, selectedStore, selectedConcern, cal, pro, salt, sugar, budgetText, note, avoid,
                        daily.promptRemaining(cal, pro, salt, sugar), cat, meal);
                String answer = new DeepSeekClient(MainActivity.this).ask(apiKey, settings.model(), settings.thinking(), DietPromptBuilder.systemPrompt(), prompt);
                final String shown = refreshNote + answer;
                runOnUiThread(() -> { result.setText(shown); refreshCatalogStatus(); });
            } catch (DeepSeekClient.ApiException e) {
                runOnUiThread(() -> showApiError(e));
            } catch (Exception e) {
                runOnUiThread(() -> result.setText("エラー: " + safe(e) + "\n\n設定画面の『接続をテスト』も確認してください。"));
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private boolean supportsOfficialSearch(String storeName) {
        return "セブン-イレブン".equals(storeName) || "ファミリーマート".equals(storeName) ||
                "ローソン".equals(storeName) || "ミニストップ".equals(storeName);
    }

    private void refreshOfficialDatabase() {
        if (!secrets.hasApiKey()) { showNeedKey(); return; }
        final String selectedStore = String.valueOf(store.getSelectedItem());
        final String[] targets = supportsOfficialSearch(selectedStore)
                ? new String[]{selectedStore}
                : new String[]{"セブン-イレブン", "ファミリーマート", "ローソン", "ミニストップ"};
        setBusy(true);
        result.setText("公式サイトから商品DBを更新しています…");
        executor.execute(() -> {
            int accepted = 0;
            int failed = 0;
            try {
                String apiKey = secrets.readApiKey();
                if (apiKey == null) throw new IllegalStateException("APIキーがありません");
                ProductDiscoveryService service = new ProductDiscoveryService(MainActivity.this);
                for (String target : targets) {
                    try { accepted += service.refresh(apiKey, settings.model(), target, catalog).accepted; }
                    catch (Exception e) { failed++; }
                }
                final int ok = accepted, ng = failed;
                runOnUiThread(() -> {
                    refreshCatalogStatus();
                    result.setText("商品DB更新完了: " + ok + "件を追加・更新" + (ng > 0 ? " / " + ng + "社は更新できませんでした" : "") +
                            "\n\n検索結果は公式ドメインだけを受け付け、栄養値が確認できない項目は不明のまま保存します。");
                });
            } catch (Exception e) {
                runOnUiThread(() -> result.setText("商品DBを更新できませんでした: " + safe(e)));
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private void saveSettings() {
        settings.setCalories(calories.getText().toString().trim());
        settings.setProtein(protein.getText().toString().trim());
        settings.setSaltTarget(saltTarget.getText().toString().trim());
        settings.setSugarTarget(sugarTarget.getText().toString().trim());
        settings.setBudget(budget.getText().toString().trim());
        settings.setGoalNote(goalNote.getText().toString().trim());
        settings.setAvoidFoods(avoidFoods.getText().toString().trim());
        settings.setConcern(String.valueOf(concern.getSelectedItem()));
        refreshDailyStatus();
    }

    private void refreshUsageStatus() {
        if (usageStatus == null || usage == null) return;
        UsageStore.Snapshot m = usage.month();
        double rate = 150.0;
        try { rate = Double.parseDouble(settings.usdJpyRate()); } catch (Exception ignored) {}
        usageStatus.setText("今月のDeepSeek: " + UsageStore.formatTokens(m.totalTokens) + " tokens / " +
                UsageStore.formatUsdRange(m) + "（" + UsageStore.formatYenRange(m, rate) + "）");
    }

    private void refreshKeyStatus() {
        if (keyStatus == null) return;
        if (secrets.hasApiKey()) {
            keyStatus.setText("🔒 APIキー: 端末内に暗号化保存済み");
            keyStatus.setTextColor(Color.rgb(47, 107, 69));
        } else {
            keyStatus.setText("⚠ APIキー未設定。右上の「設定」から入力してください。");
            keyStatus.setTextColor(Color.rgb(159, 86, 30));
        }
    }

    private void refreshCatalogStatus() {
        if (catalogStatus != null) catalogStatus.setText("商品DB: " + catalog.size() + "件　（初期公式データ＋検索更新＋手登録）");
    }

    private void refreshDailyStatus() {
        if (dailyStatus == null || calories == null) return;
        dailyStatus.setText(daily.summary(calories.getText().toString(), protein.getText().toString(), saltTarget.getText().toString(), sugarTarget.getText().toString()));
    }

    private void showNeedKey() {
        new AlertDialog.Builder(this)
                .setTitle("APIキーが必要です")
                .setMessage("DeepSeek APIキーを設定画面で暗号化保存してください。")
                .setNegativeButton("あとで", null)
                .setPositiveButton("設定を開く", (d, w) -> startActivity(new Intent(this, SettingsActivity.class)))
                .show();
    }

    private void showApiError(DeepSeekClient.ApiException e) {
        String extra;
        if (e.status == 401) extra = "APIキーが正しいか確認してください。";
        else if (e.status == 402) extra = "API残高・課金状態を確認してください。";
        else if (e.status == 429) extra = "リクエストが混み合っています。少ししてから再実行してください。";
        else extra = "DeepSeek APIの状態または設定を確認してください。";
        result.setText("APIエラー (HTTP " + e.status + ")\n" + safe(e) + "\n\n" + extra);
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        askButton.setEnabled(!busy);
        if (updateDbButton != null) updateDbButton.setEnabled(!busy);
        if (!busy) refreshUsageStatus();
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), 0);
        return box;
    }

    private Double numOrNull(EditText e) {
        String raw = e.getText().toString().trim();
        if (TextUtil.isBlank(raw)) return null;
        try { return Double.valueOf(raw); }
        catch (Exception ignored) { return null; }
    }

    private String safe(Exception e) {
        String m = e.getMessage();
        if (TextUtil.isBlank(m)) return e.getClass().getSimpleName();
        m = m.replaceAll("[\\r\\n]+", " ");
        return m.length() > 220 ? m.substring(0, 220) : m;
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
