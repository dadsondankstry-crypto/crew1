package com.exemplo.minhaescala;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Heuristicas conservadoras: nunca apresentam horas OCR como turnos confirmados. */
public final class ScheduleParser {
    private ScheduleParser() {}
    private static final Pattern DATE=Pattern.compile("(?<!\\d)([0-3]?\\d)[/\\-.]([01]?\\d)[/\\-.]((?:20)?\\d{2})(?!\\d)");
    private static final Pattern TIME=Pattern.compile("(?<!\\d)([01]?\\d|2[0-3])[:h.]([0-5]\\d)(?!\\d)");
    public static String normalize(String s) {
        if(s==null)return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{Nd}]+"," ").trim().replaceAll("\\s+"," ");
    }
    public static boolean matches(String text, String query) {
        String t=normalize(text),q=normalize(query);
        if(q.isEmpty())return true;
        if(t.contains(q))return true;
        for(String x:q.split(" "))if(!t.contains(x))return false;
        return true;
    }
    public static String normalizeDate(String raw) {
        Matcher m=DATE.matcher(raw==null?"":raw);
        if(!m.find())return "";
        try {
            int d=Integer.parseInt(m.group(1)), mo=Integer.parseInt(m.group(2));
            int y=Integer.parseInt(m.group(3));if(y<100)y+=2000;
            if(y<2000||y>2100||mo<1||mo>12||d<1||d>31)return "";
            SimpleDateFormat df=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT);df.setLenient(false);
            Date date=df.parse(String.format(Locale.ROOT,"%02d/%02d/%04d",d,mo,y));
            return df.format(date);
        }catch(Exception ex){return "";}
    }
    public static String getDate(LocalStore.Photo p) {
        // Data em cabecalho da escala. Nao confundir com DATE EMBARK na tabela de treinamento.
        if("TREINAMENTO".equals(p.kind))return "";
        ArrayList<LocalStore.Line> top=new ArrayList<>(p.lines);
        Collections.sort(top,Comparator.comparingInt(a->a.top));
        for(LocalStore.Line l:top){
            if(p.height>0 && l.top>p.height*0.40)break;
            if(normalize(l.text).contains("embark"))continue;
            String d=normalizeDate(l.text);if(!d.isEmpty())return d;
        }
        return "";
    }
    public static String kind(String text) {
        String s=normalize(text);
        if(s.contains("training")||s.contains("treinamento")||s.contains("attendance is mandatory"))return "TREINAMENTO";
        return "ESCALA";
    }
    public static ArrayList<String> times(String text) {
        ArrayList<String> r=new ArrayList<>();Matcher m=TIME.matcher(text==null?"":text);
        while(m.find() && r.size()<8){String t=String.format(Locale.ROOT,"%02d:%s",Integer.parseInt(m.group(1)),m.group(2));
            if(!r.contains(t))r.add(t);
        }return r;
    }
    public static final class Hit {
        public LocalStore.Photo photo;
        public String matched="",context="",section="",times="";
        public int y=0;
        public boolean correction=false;
    }
    public static ArrayList<Hit> search(LocalStore.Photo p,String query) {
        ArrayList<Hit> matches=new ArrayList<>();
        if(normalize(query).isEmpty())return matches;
        ArrayList<LocalStore.Line> lines=new ArrayList<>(p.lines);
        Collections.sort(lines,Comparator.comparingInt((LocalStore.Line l)->l.top).thenComparingInt(l->l.left));
        HashSet<Integer> seen=new HashSet<>();
        for(LocalStore.Line l:lines) {
            if(!matches(l.text,query))continue;
            int y=l.cy();boolean already=false;
            for(int prev:seen)if(Math.abs(prev-y)<=14){already=true;break;}
            if(already)continue;seen.add(y);
            ArrayList<LocalStore.Line> row=new ArrayList<>();
            int tolerance=Math.max(12,Math.min(23,l.bottom-l.top));
            for(LocalStore.Line neighbor:lines)if(Math.abs(neighbor.cy()-y)<=tolerance)row.add(neighbor);
            Collections.sort(row,Comparator.comparingInt(a->a.left));
            StringBuilder sb=new StringBuilder();
            for(LocalStore.Line item:row){if(sb.length()>0)sb.append(" · ");sb.append(item.text);}
            Hit h=new Hit();h.photo=p;h.matched=l.text;h.context=sb.toString();h.y=y;
            ArrayList<String> ts=times(sb.toString());
            if("TREINAMENTO".equals(p.kind)) {
                // Horario do cabecalho refere-se ao treinamento, nao ao turno.
                ts=times(p.raw);h.times=ts.isEmpty()?"Horario do treinamento nao detectado":("Treinamento: "+ts.get(0));
            }else {
                h.times=ts.size()>=2?"Possivel entrada/saida: "+ts.get(0)+" - "+ts.get(1)
                    :ts.size()==1?"Horario possivel: "+ts.get(0):"Horario nao identificado na linha";
            }
            h.section=sectionAbove(lines,y);
            matches.add(h);
        }
        if(!p.corrected.trim().isEmpty()){
            String[] textLines=p.corrected.split("\\r?\\n");
            for(String s:textLines)if(matches(s,query)){
                Hit h=new Hit();h.photo=p;h.matched=s;h.context="Texto corrigido manualmente";
                h.times="Confira o horario na foto";h.correction=true;matches.add(h);
            }
        }
        return matches;
    }
    private static String sectionAbove(ArrayList<LocalStore.Line> lines,int y) {
        String candidate="";int best=Integer.MAX_VALUE;
        for(LocalStore.Line l:lines){
            int delta=y-l.cy();if(delta<=0||delta>450||delta>=best)continue;
            String s=normalize(l.text);
            if(s.contains("restaurant")||s.contains("beverage")||s.contains("buffet")
                ||s.contains("runner")||s.contains("wine ")||s.contains("medical")
                ||s.contains("deck ")||s.contains("training")) {
                candidate=l.text;best=delta;
            }
        }
        return candidate;
    }
    public static String today() {return new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).format(new Date());}
}
