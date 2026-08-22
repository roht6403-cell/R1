package com.example.offlinemailmerge;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    Uri csvUri; ArrayList<String> questions = new ArrayList<>();
    TextView status; EditText perPage;
    static final int PICK = 10;

    public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        status=findViewById(R.id.status); perPage=findViewById(R.id.questionsPerPage);
        findViewById(R.id.selectFile).setOnClickListener(v -> {
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i,PICK);
        });
        findViewById(R.id.generate).setOnClickListener(v -> generate());
    }
    protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d); if(r==PICK && c==RESULT_OK && d!=null){
            csvUri=d.getData(); readCsv(); }
    }
    void readCsv(){
        questions.clear();
        try{
            BufferedReader br=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(csvUri),"UTF-8"));
            String line; boolean first=true;
            while((line=br.readLine())!=null){
                if(first){first=false; continue;}
                if(!line.trim().isEmpty()) questions.add(line.split(",",-1)[0].trim());
            }
            br.close(); status.setText("Loaded "+questions.size()+" questions.");
        }catch(Exception e){ status.setText("CSV error: "+e.getMessage()); }
    }
    void generate(){
        if(questions.isEmpty()){Toast.makeText(this,"Please select a CSV first",Toast.LENGTH_SHORT).show();return;}
        int n=10; try{n=Math.max(1,Integer.parseInt(perPage.getText().toString()));}catch(Exception ignored){}
        PdfDocument doc=new PdfDocument(); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setTextSize(15);
        int pages=(questions.size()+n-1)/n;
        for(int pg=0;pg<pages;pg++){
            PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,pg+1).create();
            PdfDocument.Page page=doc.startPage(info); Canvas c=page.getCanvas();
            float y=55; p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText("Practice Questions",40,y,p); y+=35; p.setTypeface(Typeface.DEFAULT);
            int start=pg*n,end=Math.min(start+n,questions.size());
            for(int i=start;i<end;i++){ c.drawText((i+1)+". "+questions.get(i),40,y,p); y+=65; }
            doc.finishPage(page);
        }
        try{
            File out=new File(getExternalFilesDir(null),"questions_"+System.currentTimeMillis()+".pdf");
            FileOutputStream fos=new FileOutputStream(out); doc.writeTo(fos); fos.close(); doc.close();
            status.setText("PDF created:\n"+out.getAbsolutePath());
            Intent share=new Intent(Intent.ACTION_SEND); share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM,Uri.fromFile(out));
            Toast.makeText(this,"PDF तयार झाला: "+pages+" pages",Toast.LENGTH_LONG).show();
        }catch(Exception e){status.setText("PDF error: "+e.getMessage());}
    }
}
