package com.exemplo.minhaescala;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Interface nativa. Nenhum arquivo e enviado a servidores. */
public class MainActivity extends Activity {
    private static final int REQ_IMAGES = 101;
    private static final int REQ_EXPORT = 102;
    private static final int BG=Color.rgb(9,22,39), PANEL=Color.rgb(18,39,59), BORDER=Color.rgb(41,68,84);
    private static final int WHITE=Color.rgb(237,246,249), MUTED=Color.rgb(155,181,191), ACCENT=Color.rgb(53,221,193);
    private static final int ORANGE=Color.rgb(255,190,102);
    private LocalStore store;
    private TextRecognizer recognizer;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ArrayDeque<Uri> importQueue=new ArrayDeque<>();
    private LinearLayout screen, resultArea, photosArea, favoritesArea;
    private EditText searchInput;
    private TextView summaryText, statusText, resultsTitle;
    private Button todayButton, favoriteButton;
    private boolean todayOnly=false, onlyFavorites=false, processing=false;
    private String exportContent="";
    private int importIndex=0;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        store=new LocalStore(this);
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        buildUi();renderAll();
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private void addGap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private TextView text(String title,int size,int color,boolean strong){
        TextView v=new TextView(this);v.setText(title);v.setTextColor(color);v.setTextSize(size);
        if(strong)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setLineSpacing(dp(2),1.04f);return v;
    }
    private Button button(String title,boolean solid){
        Button b=new Button(this);b.setText(title);b.setTextColor(solid?BG:WHITE);
        b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(bg(solid?ACCENT:PANEL,solid?0:BORDER,13));
        b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setPadding(dp(11),dp(4),dp(11),dp(4));return b;
    }
    private LinearLayout card(LinearLayout into) {
        LinearLayout box=col();box.setBackground(bg(PANEL,BORDER,17));pad(box,15,15);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);
        into.addView(box,p);return box;
    }
    private void pairButtons(LinearLayout target,Button left,Button right){
        LinearLayout r=row();LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1);
        a.rightMargin=dp(7);r.addView(left,a);r.addView(right,new LinearLayout.LayoutParams(0,dp(48),1));target.addView(r);
    }
    private void section(LinearLayout into,String title){TextView label=text(title,19,WHITE,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);p.bottomMargin=dp(10);into.addView(label,p);}
    private void buildUi() {
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);
        screen=col();pad(screen,17,19);sc.addView(screen);setContentView(sc);
        TextView tag=text("●  LEITURA LOCAL  •  SEM INTERNET",10,ACCENT,true);screen.addView(tag);
        addGap(screen,7);screen.addView(text("Minha Escala",30,WHITE,true));
        TextView sub=text("Encontre seu turno e treinamentos nas fotos da escala.",13,MUTED,false);screen.addView(sub);addGap(screen,14);
        LinearLayout start=card(screen);
        summaryText=text("",14,WHITE,true);start.addView(summaryText);addGap(start,5);
        start.addView(text("Suas fotos ficam neste celular. O reconhecimento de texto funciona offline.",12,MUTED,false));addGap(start,12);
        Button importBtn=button("＋  Importar fotos",true);
        Button manualBtn=button("＋  Registrar horário",false);
        pairButtons(start,importBtn,manualBtn);
        importBtn.setOnClickListener(v->pickImages());manualBtn.setOnClickListener(v->editShift(null,null));
        addGap(start,11);
        statusText=text("Pronto para importar imagens.",12,ACCENT,false);start.addView(statusText);
        section(screen,"Pesquisar por pessoa");
        searchInput=new EditText(this);
        searchInput.setSingleLine(true);searchInput.setTextColor(WHITE);
        searchInput.setHintTextColor(MUTED);searchInput.setHint("Digite nome ou sobrenome...");
        searchInput.setBackground(bg(PANEL,BORDER,14));pad(searchInput,13,12);
        searchInput.setTextSize(16);screen.addView(searchInput,new LinearLayout.LayoutParams(-1,dp(53)));
        searchInput.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){showResults();}
            public void afterTextChanged(Editable s){}
        });addGap(screen,9);
        LinearLayout filter=row();todayButton=button("Todos os dias",true);
        favoriteButton=button("☆ Favoritos",false);pairButtons(filter,todayButton,favoriteButton);
        screen.addView(filter);
        todayButton.setOnClickListener(v->{todayOnly=!todayOnly;updateFilterButtons();showResults();});
        favoriteButton.setOnClickListener(v->{onlyFavorites=!onlyFavorites;updateFilterButtons();showResults();});
        Button saveFavorite=button("☆  Favoritar nome pesquisado",false);
        LinearLayout.LayoutParams sf=new LinearLayout.LayoutParams(-1,dp(44));sf.topMargin=dp(9);
        screen.addView(saveFavorite,sf);saveFavorite.setOnClickListener(v->favoriteQuery());
        favoritesArea=col();screen.addView(favoritesArea);
        section(screen,"Resultados");
        resultsTitle=text("",12,MUTED,false);screen.addView(resultsTitle);addGap(screen,7);
        resultArea=col();screen.addView(resultArea);
        section(screen,"Fotos importadas");
        photosArea=col();screen.addView(photosArea);
        LinearLayout tools=card(screen);tools.addView(text("Ferramentas",15,WHITE,true));addGap(tools,9);
        Button export=button("⇩  Exportar pesquisa em CSV",false);tools.addView(export);
        export.setOnClickListener(v->exportResults());addGap(tools,8);
        Button privacy=button("ⓘ  Como funciona / privacidade",false);tools.addView(privacy);
        privacy.setOnClickListener(v->showInfo());addGap(tools,8);
        Button clear=button("Apagar todos os dados deste aplicativo",false);tools.addView(clear);
        clear.setOnClickListener(v->clearEverything());
        addGap(screen,18);screen.addView(text("Versão 1.0 • Leitura OCR sujeita a erros; confira sempre a foto original.",11,MUTED,false));
    }
    private void renderAll(){
        summaryText.setText(store.photos.size()+" foto(s)  •  "+store.shifts.size()+" horário(s) confirmado(s)");
        updateFilterButtons();showFavorites();showPhotos();showResults();
    }
    private void updateFilterButtons(){
        todayButton.setText(todayOnly?"📅  Só hoje":"📅  Todas as datas");
        todayButton.setBackground(bg(todayOnly?ACCENT:PANEL,todayOnly?0:BORDER,12));
        todayButton.setTextColor(todayOnly?BG:WHITE);
        favoriteButton.setText(onlyFavorites?"★  Favoritos":"☆  Favoritos");
        favoriteButton.setBackground(bg(onlyFavorites?ACCENT:PANEL,onlyFavorites?0:BORDER,12));
        favoriteButton.setTextColor(onlyFavorites?BG:WHITE);
    }
    private void favoriteQuery(){
        String q=searchInput.getText().toString().trim();
        if(q.length()<2){toast("Digite um nome para favoritar.");return;}
        if(store.favorites.contains(q))store.favorites.remove(q);else store.favorites.add(q);
        store.save();showFavorites();showResults();
    }
    private void showFavorites(){
        favoritesArea.removeAllViews();
        if(store.favorites.isEmpty())return;
        addGap(favoritesArea,9);favoritesArea.addView(text("Toque num favorito para pesquisar:",12,MUTED,false));
        for(String name:store.favorites){
            Button b=button("★  "+name,false);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));p.topMargin=dp(5);
            favoritesArea.addView(b,p);b.setOnClickListener(v->{searchInput.setText(name);searchInput.setSelection(searchInput.length());onlyFavorites=false;updateFilterButtons();});
            b.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Remover favorito?")
                .setMessage(name).setNegativeButton("Cancelar",null)
                .setPositiveButton("Remover",(d,w)->{store.favorites.remove(name);store.save();showFavorites();showResults();}).show();return true;});
        }
    }
    private boolean isVisibleDate(String date){return !todayOnly||ScheduleParser.today().equals(date);}
    private boolean queryMatchesFavorite(String text){
        if(!onlyFavorites)return true;
        for(String f:store.favorites)if(ScheduleParser.matches(text,f))return true;
        return false;
    }
    private void showResults(){
        if(resultArea==null||searchInput==null)return;
        resultArea.removeAllViews();int total=0;String q=searchInput.getText().toString().trim();
        ArrayList<LocalStore.Shift> shifts=new ArrayList<>(store.shifts);
        Collections.reverse(shifts);
        for(LocalStore.Shift s:shifts){
            if(!isVisibleDate(s.date)||!queryMatchesFavorite(s.person))continue;
            if(!q.isEmpty()&&!ScheduleParser.matches(s.person+" "+s.section+" "+s.notes,q))continue;
            showManualResult(s);total++;if(total>=80)break;
        }
        if(total<80){
            for(int i=store.photos.size()-1;i>=0;i--){
                LocalStore.Photo p=store.photos.get(i);
                if(!isVisibleDate(p.date))continue;
                ArrayList<ScheduleParser.Hit> hits=new ArrayList<>();
                if(!q.isEmpty())hits.addAll(ScheduleParser.search(p,q));
                else if(onlyFavorites)for(String favorite:store.favorites)hits.addAll(ScheduleParser.search(p,favorite));
                for(ScheduleParser.Hit h:hits){
                    if(!queryMatchesFavorite(h.matched))continue;
                    showHit(h);total++;if(total>=80)break;
                }
                if(total>=80)break;
            }
        }
        if(q.isEmpty()&&!onlyFavorites){
            resultsTitle.setText("Digite um nome para localizar nas fotos. Horários confirmados manualmente aparecem abaixo.");
        }else resultsTitle.setText(total+" resultado(s)"+(todayOnly?" • "+ScheduleParser.today():"")+(total>=80?" • limite de 80":""));
        if(total==0){LinearLayout p=card(resultArea);
            p.addView(text(q.isEmpty()?"Importe uma foto ou registre um horário.":"Nenhum registro encontrado.",14,WHITE,true));
            addGap(p,5);p.addView(text("Tente parte do sobrenome, desative o filtro 'Só hoje' ou corrija a leitura OCR.",12,MUTED,false));
        }
    }
    private void showManualResult(LocalStore.Shift s){
        LinearLayout c=card(resultArea);
        c.addView(text("✓ HORÁRIO CONFIRMADO",11,ACCENT,true));addGap(c,5);
        c.addView(text(s.person,18,WHITE,true));
        c.addView(text((s.date.isEmpty()?"Data não informada":s.date)+"   •   "+s.start+(s.end.isEmpty()?"":" — "+s.end),16,ORANGE,true));
        if(!s.section.isEmpty())c.addView(text(s.section,13,WHITE,false));
        if(!s.notes.isEmpty())c.addView(text(s.notes,12,MUTED,false));
        addGap(c,8);Button edit=button("✎  Editar / excluir",false);c.addView(edit);
        edit.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Horário salvo")
            .setItems(new String[]{"Editar","Excluir"},(d,which)->{
                if(which==0)editShift(s,null);else new AlertDialog.Builder(this)
                    .setTitle("Excluir horário?").setMessage(s.person).setNegativeButton("Cancelar",null)
                    .setPositiveButton("Excluir",(di,w)->{store.shifts.remove(s);store.save();renderAll();}).show();
            }).show());
    }
    private void showHit(ScheduleParser.Hit hit){
        LocalStore.Photo p=hit.photo;
        LinearLayout c=card(resultArea);
        c.addView(text("✦  OCR • "+p.kind,11,ACCENT,true));addGap(c,5);
        c.addView(text(hit.matched,17,WHITE,true));
        c.addView(text(p.date.isEmpty()?"Data da foto não confirmada":("Data da escala: "+p.date),12,ORANGE,true));
        c.addView(text(hit.times,13,WHITE,true));
        if(!hit.section.isEmpty())c.addView(text("Setor próximo: "+hit.section,12,MUTED,false));
        c.addView(text("Na linha: "+hit.context,11,MUTED,false));
        addGap(c,5);c.addView(text("⚠ Sugestão OCR; confira os números e a coluna na foto.",11,ORANGE,false));
        addGap(c,9);Button img=button("▧  Ver foto",false),confirm=button("✓  Corrigir / confirmar",true);
        pairButtons(c,img,confirm);
        img.setOnClickListener(v->viewPhoto(p));
        confirm.setOnClickListener(v->{
            LocalStore.Shift preliminary=new LocalStore.Shift();preliminary.person=hit.matched;
            preliminary.date=p.date;preliminary.section=hit.section;
            if(!"TREINAMENTO".equals(p.kind)){
                ArrayList<String> times=ScheduleParser.times(hit.context);
                if(times.size()>0)preliminary.start=times.get(0);
                if(times.size()>1)preliminary.end=times.get(1);
            }else preliminary.notes="Treinamento (não é turno de trabalho). "+hit.times;
            preliminary.source=p.title;
            editShift(null,preliminary);
        });
    }
    private void showPhotos(){
        photosArea.removeAllViews();
        if(store.photos.isEmpty()){
            LinearLayout card=card(photosArea);card.addView(text("Nenhuma foto importada ainda.",14,MUTED,false));return;
        }
        for(int i=store.photos.size()-1;i>=0;i--){
            LocalStore.Photo photo=store.photos.get(i);
            LinearLayout c=card(photosArea);
            c.addView(text("▧  "+photo.title,15,WHITE,true));
            c.addView(text(photo.kind+"  •  "+(photo.date.isEmpty()?"Data a confirmar":photo.date)+"  •  "+photo.lines.size()+" linhas OCR",12,MUTED,false));
            addGap(c,9);Button open=button("Ver foto",false),editDate=button("Definir data",false);
            pairButtons(c,open,editDate);
            open.setOnClickListener(v->viewPhoto(photo));editDate.setOnClickListener(v->setPhotoDate(photo));
            addGap(c,7);Button organized=button("▤  Ver escala organizada (estimativa)",true);c.addView(organized);
            organized.setOnClickListener(v->showOrganizedPhoto(photo));
            addGap(c,7);Button manage=button("Texto OCR / corrigir / excluir",false);c.addView(manage);
            manage.setOnClickListener(v->managePhoto(photo));
        }
    }
    private void showOrganizedPhoto(LocalStore.Photo p) {
        LinearLayout list=col();pad(list,12,12);
        list.addView(text(p.kind+"  •  "+(p.date.isEmpty()?"Data nao identificada":p.date),17,Color.BLACK,true));
        list.addView(text("Tabela estimada pelo OCR. Confirme os nomes e as colunas na foto original antes de usar o horario.",12,Color.DKGRAY,false));
        addGap(list,8);
        ArrayList<LocalStore.Line> lines=new ArrayList<>(p.lines);
        Collections.sort(lines,(a,b)->a.cy()==b.cy()?Integer.compare(a.left,b.left):Integer.compare(a.cy(),b.cy()));
        ArrayList<ArrayList<LocalStore.Line>> groups=new ArrayList<>();
        for(LocalStore.Line l:lines) {
            ArrayList<LocalStore.Line> group=null;
            for(int k=groups.size()-1;k>=0&&k>=groups.size()-3;k--){
                ArrayList<LocalStore.Line> g=groups.get(k);
                if(Math.abs(g.get(0).cy()-l.cy())<=12){group=g;break;}
            }
            if(group==null){group=new ArrayList<>();groups.add(group);}group.add(l);
        }
        int shown=0;
        for(ArrayList<LocalStore.Line> group:groups){
            Collections.sort(group,(a,b)->Integer.compare(a.left,b.left));
            StringBuilder b=new StringBuilder();
            for(LocalStore.Line l:group){if(b.length()>0)b.append("     |     ");b.append(l.text);}
            String rowText=b.toString().trim();
            if(rowText.isEmpty())continue;
            // Mostra as linhas, sem fingir que reconstruiu exatamente as colunas.
            TextView line=text(rowText,13,Color.rgb(19,47,62),false);
            line.setBackground(bg(Color.rgb(238,247,249),Color.rgb(203,224,226),9));pad(line,9,8);
            LinearLayout.LayoutParams par=new LinearLayout.LayoutParams(-1,-2);par.bottomMargin=dp(5);
            list.addView(line,par);shown++;if(shown>=180)break;
        }
        if(shown==0)list.addView(text("Nenhuma linha identificada. Tente foto mais nítida.",13,Color.DKGRAY,false));
        ScrollView sc=new ScrollView(this);sc.addView(list);
        new AlertDialog.Builder(this).setTitle("Escala reorganizada · OCR")
            .setView(sc).setPositiveButton("Fechar",null).show();
    }
    private void setPhotoDate(LocalStore.Photo p){
        EditText e=new EditText(this);e.setSingleLine(true);e.setText(p.date);
        e.setHint("dd/MM/aaaa (vazio se desconhecida)");
        new AlertDialog.Builder(this).setTitle("Data da escala")
            .setMessage("Informe a data que aparece no cabeçalho, não a data de embarque.")
            .setView(e).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{
                String raw=e.getText().toString().trim();String parsed=raw.isEmpty()?"":ScheduleParser.normalizeDate(raw);
                if(!raw.isEmpty()&&parsed.isEmpty()){toast("Data inválida. Use dd/MM/aaaa.");return;}
                p.date=parsed;store.save();renderAll();
            }).show();
    }
    private void managePhoto(LocalStore.Photo p){
        new AlertDialog.Builder(this).setTitle(p.title)
            .setItems(new String[]{"Ver OCR completo","Corrigir texto para pesquisa","Analisar foto novamente","Excluir foto do aplicativo"},(d,which)->{
                if(which==0)showLongText("Texto reconhecido",p.raw);
                else if(which==1)editCorrection(p);
                else if(which==2){status("Reanalisando "+p.title+"...");recognize(p,null);}
                else new AlertDialog.Builder(this).setTitle("Excluir foto e OCR?")
                    .setMessage("A foto original da galeria NÃO será apagada. Apenas a cópia local do app.")
                    .setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(di,w)->{
                        new File(getFilesDir(),p.file).delete();store.photos.remove(p);store.save();renderAll();
                    }).show();
            }).show();
    }
    private void editCorrection(LocalStore.Photo p){
        EditText e=new EditText(this);e.setMinLines(6);e.setMaxLines(14);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setText(p.corrected.isEmpty()?p.raw:p.corrected);
        ScrollView sc=new ScrollView(this);sc.addView(e);
        new AlertDialog.Builder(this).setTitle("Texto corrigido para pesquisa")
            .setMessage("Não substitui a foto. Corrija nomes e linhas mal reconhecidos; esta versão não reconstrói automaticamente as colunas.")
            .setView(sc).setNegativeButton("Cancelar",null)
            .setPositiveButton("Salvar",(d,w)->{p.corrected=e.getText().toString();store.save();renderAll();}).show();
    }
    private void showLongText(String title,String content){
        TextView tv=text(content.isEmpty()?"Nenhum texto reconhecido.":content,13,Color.BLACK,false);
        tv.setTextIsSelectable(true);pad(tv,16,15);
        ScrollView s=new ScrollView(this);s.addView(tv);
        new AlertDialog.Builder(this).setTitle(title).setView(s).setPositiveButton("Fechar",null).show();
    }
    private void viewPhoto(LocalStore.Photo p){
        try {
            File f=new File(getFilesDir(),p.file);if(!f.isFile()){toast("Foto não encontrada.");return;}
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
            int sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>2300)sample*=2;
            o.inJustDecodeBounds=false;o.inSampleSize=sample;
            Bitmap bmp=BitmapFactory.decodeFile(f.getPath(),o);
            if(bmp==null){toast("Não foi possível abrir esta imagem.");return;}
            ImageView img=new ImageView(this);img.setImageBitmap(bmp);img.setAdjustViewBounds(true);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);img.setBackgroundColor(Color.rgb(12,19,29));
            ScrollView s=new ScrollView(this);s.addView(img,new ScrollView.LayoutParams(-1,-2));
            Dialog d=new AlertDialog.Builder(this).setTitle(p.title).setView(s)
                .setPositiveButton("Fechar",(dia,w)->bmp.recycle()).create();
            d.setOnCancelListener(di->bmp.recycle());d.show();
        }catch(Exception e){toast("Erro ao visualizar: "+e.getMessage());}
    }
    private EditText input(String hint,String initial){
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setHint(hint);
        e.setText(initial==null?"":initial);return e;
    }
    private void editShift(LocalStore.Shift editing,LocalStore.Shift initial){
        LocalStore.Shift ref=editing!=null?editing:(initial!=null?initial:new LocalStore.Shift());
        LinearLayout fields=col();pad(fields,18,6);
        EditText name=input("Nome completo",ref.person),date=input("Data dd/MM/aaaa",ref.date);
        EditText start=input("Entrada 00:00",ref.start),end=input("Saída 00:00",ref.end);
        EditText section=input("Setor / local",ref.section),note=input("Observações",ref.notes);
        fields.addView(name);fields.addView(date);fields.addView(start);fields.addView(end);fields.addView(section);fields.addView(note);
        ScrollView sc=new ScrollView(this);sc.addView(fields);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(editing!=null?"Editar horário":"Confirmar / registrar horário")
            .setView(sc).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
            String person=name.getText().toString().trim(),rawDate=date.getText().toString().trim();
            String parsed=rawDate.isEmpty()?"":ScheduleParser.normalizeDate(rawDate);
            String st=start.getText().toString().trim(),en=end.getText().toString().trim();
            if(person.length()<2){name.setError("Informe um nome.");return;}
            if(!rawDate.isEmpty()&&parsed.isEmpty()){date.setError("Use dd/MM/aaaa.");return;}
            if(!st.isEmpty()&&!st.matches("([01]\\d|2[0-3]):[0-5]\\d")){start.setError("Use 00:00");return;}
            if(!en.isEmpty()&&!en.matches("([01]\\d|2[0-3]):[0-5]\\d")){end.setError("Use 00:00");return;}
            LocalStore.Shift out=editing==null?new LocalStore.Shift():editing;
            if(editing==null)out.id="s"+System.nanoTime();
            out.person=person;out.date=parsed;out.start=st;out.end=en;
            out.section=section.getText().toString().trim();out.notes=note.getText().toString().trim();
            out.source=ref.source;
            if(editing==null)store.shifts.add(out);
            if(!store.save())toast("Falha ao salvar dados; verifique armazenamento.");
            dialog.dismiss();renderAll();
        }));
        dialog.show();
    }
    private void pickImages(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        try{startActivityForResult(i,REQ_IMAGES);}catch(Exception ex){toast("Seletor de imagens não disponível.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(request==REQ_IMAGES){
            if(data.getClipData()!=null){
                for(int j=0;j<data.getClipData().getItemCount();j++)importQueue.add(data.getClipData().getItemAt(j).getUri());
            }else if(data.getData()!=null)importQueue.add(data.getData());
            if(!processing)importNext();
        }else if(request==REQ_EXPORT&&data.getData()!=null){
            Uri uri=data.getData();String payload=exportContent;
            executor.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null)throw new Exception("Destino inválido");out.write(payload.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(()->toast("Relatório CSV salvo."));
            }catch(Exception ex){runOnUiThread(()->toast("Falha ao exportar: "+ex.getMessage()));}});
        }
    }
    private String displayName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}return "foto.jpg";
    }
    private void importNext(){
        Uri uri=importQueue.poll();if(uri==null){processing=false;status("Fotos processadas. Pesquise um nome!");renderAll();return;}
        processing=true;int idx=++importIndex;
        status("Importando foto "+idx+" ("+importQueue.size()+" restantes)...");
        String title=displayName(uri);
        executor.execute(()->{
            File saved=null;
            try{
                String ext="jpg";int dot=title.lastIndexOf('.');if(dot>=0){
                    String e=title.substring(dot+1).toLowerCase(Locale.ROOT);
                    if(e.matches("jpeg|jpg|png|webp|heic|bmp"))ext=e;
                }
                String filename="escala_"+System.nanoTime()+"."+ext;
                saved=new File(getFilesDir(),filename);
                long total=0;try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(saved)){
                    if(in==null)throw new Exception("Foto não disponível");
                    byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){
                        total+=n;if(total>30_000_000)throw new Exception("Foto acima de 30 MB");out.write(b,0,n);
                    }
                }
                final File file=saved;LocalStore.Photo p=new LocalStore.Photo();
                p.id="p"+System.nanoTime();p.file=file.getName();p.title=title;
                runOnUiThread(()->recognize(p,()->importNext()));
            }catch(Exception ex){if(saved!=null)saved.delete();
                runOnUiThread(()->{toast("Erro em "+title+": "+ex.getMessage());importNext();});}
        });
    }
    private void recognize(LocalStore.Photo p,Runnable done){
        try {
            File file=new File(getFilesDir(),p.file);
            BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(file.getAbsolutePath(),size);p.width=size.outWidth;p.height=size.outHeight;
            InputImage image=InputImage.fromFilePath(this,Uri.fromFile(file));
            status("Lendo texto de "+p.title+" (offline)...");
            recognizer.process(image)
                .addOnSuccessListener(result->{
                    p.raw=result.getText();p.lines.clear();
                    for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){
                        LocalStore.Line l=new LocalStore.Line();l.text=line.getText();
                        Rect r=line.getBoundingBox();if(r!=null){l.left=r.left;l.top=r.top;l.right=r.right;l.bottom=r.bottom;}
                        p.lines.add(l);
                    }
                    p.kind=ScheduleParser.kind(p.raw);
                    if(p.date.isEmpty())p.date=ScheduleParser.getDate(p);
                    if(!store.photos.contains(p))store.photos.add(p);
                    if(!store.save())toast("Não foi possível gravar o índice local.");
                    renderAll();status("OCR concluído: "+p.title+" • "+p.lines.size()+" linhas.");
                    if(done!=null)done.run();
                })
                .addOnFailureListener(ex->{
                    if(!store.photos.contains(p))store.photos.add(p);
                    store.save();renderAll();status("Não foi possível ler: "+p.title);
                    toast("Você pode importar outra foto com mais nitidez.");if(done!=null)done.run();
                });
        }catch(Exception ex){status("Erro OCR: "+ex.getMessage());if(done!=null)done.run();}
    }
    private void exportResults(){
        String query=searchInput.getText().toString().trim();
        if(query.isEmpty()&&!onlyFavorites){toast("Pesquise um nome ou ative Favoritos primeiro.");return;}
        StringBuilder sb=new StringBuilder("\uFEFFTipo;Data;Nome/Linha;Horário sugerido;Setor;Fonte;Observação\r\n");
        for(LocalStore.Shift s:store.shifts)if((query.isEmpty()||ScheduleParser.matches(s.person,query))&&isVisibleDate(s.date)&&queryMatchesFavorite(s.person)){
            csvLine(sb,"CONFIRMADO",s.date,s.person,s.start+" - "+s.end,s.section,s.source,s.notes);
        }
        for(LocalStore.Photo p:store.photos)if(isVisibleDate(p.date)){
            ArrayList<ScheduleParser.Hit> list=new ArrayList<>();
            if(!query.isEmpty())list.addAll(ScheduleParser.search(p,query));
            else for(String f:store.favorites)list.addAll(ScheduleParser.search(p,f));
            for(ScheduleParser.Hit h:list)if(queryMatchesFavorite(h.matched)){
                csvLine(sb,"OCR - CONFIRMAR",p.date,h.matched,h.times,h.section,p.title,h.context);
            }
        }
        exportContent=sb.toString();
        new AlertDialog.Builder(this).setTitle("Exportar nomes e horários")
            .setMessage("O CSV pode conter dados pessoais de colegas. Guarde e compartilhe somente com autorização.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Exportar",(di,w)->{
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/csv");intent.putExtra(Intent.EXTRA_TITLE,"minha_escala_pesquisa.csv");
                startActivityForResult(intent,REQ_EXPORT);
            }).show();
    }
    private static void csvLine(StringBuilder s,String... columns){
        for(int i=0;i<columns.length;i++){if(i>0)s.append(';');
            String v=columns[i]==null?"":columns[i].replace("\"","\"\"");
            s.append('"').append(v).append('"');
        }s.append("\r\n");
    }
    private void clearEverything(){
        new AlertDialog.Builder(this).setTitle("Apagar dados locais?")
            .setMessage("Esta ação apagará cópias das fotos, OCR, favoritos e horários confirmados DENTRO do aplicativo. A galeria original fica intacta.")
            .setNegativeButton("Cancelar",null).setPositiveButton("APAGAR",(d,w)->{
                for(LocalStore.Photo p:store.photos)new File(getFilesDir(),p.file).delete();
                store.photos.clear();store.shifts.clear();store.favorites.clear();store.save();renderAll();
            }).show();
    }
    private void showInfo(){
        showLongText("Como funciona e privacidade",
            "1. Toque em Importar fotos e selecione várias imagens da escala.\n\n"+
            "2. O aplicativo usa OCR do ML Kit com modelo embutido. Não requer internet depois de instalado.\n\n"+
            "3. Pesquise parte do nome ou sobrenome. Na busca, aparecem linhas próximas e POSSÍVEIS horários.\n\n"+
            "4. Confira a foto original antes de confirmar horários, principalmente fotos inclinadas, borradas e tabelas com colunas apertadas.\n\n"+
            "5. Para salvar um turno verificado, use Corrigir/confirmar. O registro manual aparece como HORÁRIO CONFIRMADO.\n\n"+
            "6. Fotos com anúncio de treinamento são mostradas como TREINAMENTO. A hora do treinamento não significa a hora do turno.\n\n"+
            "7. Se a data não foi identificada, abra Fotos importadas > Definir data.\n\n"+
            "8. Favoritos salvam textos de nomes pesquisados. Toque neles para pesquisar de novo.\n\n"+
            "PRIVACIDADE: nomes, IDs e escalas são dados pessoais. Não publique fotos em repositórios GitHub públicos. O projeto de código NÃO contém as fotos fornecidas no chat.\n\n"+
            "LIMITES: o app não reconstrói perfeitamente todas as células; reconhecimentos OCR, horários e datas devem ser conferidos. Não gera alertas automáticos, não integra com sistemas do empregador e não fornece a versão oficial da escala.");
    }
    private void status(String message){if(statusText!=null)statusText.setText(message);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){
        super.onDestroy();if(recognizer!=null)recognizer.close();executor.shutdown();
    }
}
