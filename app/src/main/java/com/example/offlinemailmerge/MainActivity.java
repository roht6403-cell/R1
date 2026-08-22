package com.example.offlinemailmerge;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {

    private Uri csvUri;
    private final ArrayList<String> questions = new ArrayList<>();
    private TextView status;
    private EditText perPage;
    private Button shareButton;
    private File lastPdf;
    private static final int PICK_FILE = 100;

    // A4 at 72dpi
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN_LEFT = 40;
    private static final int MARGIN_RIGHT = 40;
    private static final int MARGIN_TOP = 50;
    private static final int MARGIN_BOTTOM = 50;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        perPage = findViewById(R.id.questionsPerPage);
        shareButton = findViewById(R.id.share);

        findViewById(R.id.selectFile).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("text/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, PICK_FILE);
        });

        findViewById(R.id.generate).setOnClickListener(v -> generatePdf());

        shareButton.setOnClickListener(v -> sharePdf());
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && resultCode == RESULT_OK && data != null) {
            csvUri = data.getData();
            readCsv();
        }
    }

    private void readCsv() {
        questions.clear();
        shareButton.setVisibility(View.GONE);
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(getContentResolver().openInputStream(csvUri), "UTF-8"))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; } // skip header row
                String cell = firstCsvColumn(line).trim();
                if (!cell.isEmpty()) {
                    questions.add(cell);
                }
            }
            status.setText("Loaded " + questions.size() + " questions.");
        } catch (Exception e) {
            status.setText("CSV error: " + e.getMessage());
        }
    }

    /**
     * Extracts the first column of a CSV line, honoring simple double-quote
     * escaping so questions containing commas or quoted text don't break.
     * Falls back to the whole trimmed line if there is only one column.
     */
    private String firstCsvColumn(String line) {
        if (line.isEmpty()) return "";
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                break;
            } else {
                cur.append(c);
            }
        }
        return cur.toString();
    }

    private void generatePdf() {
        if (questions.isEmpty()) {
            Toast.makeText(this, "Please select a CSV first", Toast.LENGTH_SHORT).show();
            return;
        }

        int n = 10;
        try { n = Math.max(1, Integer.parseInt(perPage.getText().toString())); }
        catch (Exception ignored) {}

        int usableWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(18);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

        TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setTextSize(15);
        bodyPaint.setTypeface(Typeface.DEFAULT); // system font fallback covers Devanagari/Marathi

        PdfDocument doc = new PdfDocument();
        int groupCount = (questions.size() + n - 1) / n;
        int pdfPageNumber = 0; // real, always-unique counter for actual PDF pages emitted

        for (int grp = 0; grp < groupCount; grp++) {
            pdfPageNumber++;
            PdfDocument.PageInfo info =
                    new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdfPageNumber).create();
            PdfDocument.Page page = doc.startPage(info);
            Canvas c = page.getCanvas();
            float y = MARGIN_TOP;

            c.drawText("Practice Questions - Group " + (grp + 1) + " of " + groupCount,
                    MARGIN_LEFT, y, titlePaint);
            y += 30;

            int start = grp * n;
            int end = Math.min(start + n, questions.size());

            for (int i = start; i < end; i++) {
                String text = (i + 1) + ". " + questions.get(i);

                StaticLayout layout = StaticLayout.Builder
                        .obtain(text, 0, text.length(), bodyPaint, usableWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(1.15f, 1.0f)
                        .build();

                // If a question would overflow the bottom margin, spill onto a
                // continuation page (rare: only for unusually long questions).
                if (y + layout.getHeight() > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    doc.finishPage(page);
                    pdfPageNumber++;
                    PdfDocument.PageInfo overflowInfo =
                            new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdfPageNumber).create();
                    page = doc.startPage(overflowInfo);
                    c = page.getCanvas();
                    y = MARGIN_TOP;
                }

                c.save();
                c.translate(MARGIN_LEFT, y);
                layout.draw(c);
                c.restore();

                y += layout.getHeight() + 18; // gap between questions
            }

            doc.finishPage(page);
        }

        try {
            File dir = getExternalFilesDir("pdfs");
            if (dir != null && !dir.exists()) dir.mkdirs();
            File out = new File(dir, "questions_" + System.currentTimeMillis() + ".pdf");
            FileOutputStream fos = new FileOutputStream(out);
            doc.writeTo(fos);
            fos.close();
            doc.close();

            lastPdf = out;
            shareButton.setVisibility(View.VISIBLE);
            status.setText("PDF saved: " + out.getAbsolutePath());
            Toast.makeText(this, "PDF तयार झाला (" + pdfPageNumber + " pages)", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            status.setText("PDF error: " + e.getMessage());
        }
    }

    private void sharePdf() {
        if (lastPdf == null || !lastPdf.exists()) {
            Toast.makeText(this, "No PDF to share yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(
                this, "com.example.offlinemailmerge.fileprovider", lastPdf);

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share / Save PDF"));
    }
          }
