package com.rhay.pkgexplorer;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
 private static final int PICK=1,SAVE=2,SAVE_CSV=3,EXPORT_ENTRY=4;
 private Uri file;
 private TextView output,status;
 private String report="",csv="";
 private int selectedEntry=-1; private long selectedOffset=0,selectedSize=0;
 private LinearLayout root;
 private int cyan=Color.rgb(42,225,232), bg=Color.rgb(9,14,27), panel=Color.rgb(22,33,53);
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  ScrollView scroll=new ScrollView(this);
  root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,30,28,36);root.setBackgroundColor(bg);
  scroll.addView(root);setContentView(scroll);
  TextView title=text("RHAY  /  PKG EXPLORER",25,cyan);title.setTypeface(null,Typeface.BOLD);root.addView(title);
  root.addView(text("PRO ANALYZER 2.3  •  PS4 PACKAGE INSPECTOR",12,Color.rgb(160,187,209)));
  section("ARQUIVO");
  button("01   SELECIONAR PKG",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);});
  status=text("Nenhum pacote selecionado",13,Color.LTGRAY);root.addView(status);
  section("FERRAMENTAS");
  button("02   ANALISAR METADADOS E ENTRADAS",()->analyze(false));
  button("03   ANALISAR + BUSCAR ASSINATURAS (32 MB)",()->analyze(true));
  button("04   EXPORTAR RELATÓRIO TXT",()->{if(report.isEmpty()){toast("Faça uma análise primeiro");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"RHAY_PKG_Explorer_2_relatorio.txt");startActivityForResult(i,SAVE);});
  button("05   EXPORTAR TABELA CSV",()->{if(csv.isEmpty()){toast("Analise o PKG primeiro");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"RHAY_PKG_entradas.csv");startActivityForResult(i,SAVE_CSV);});
  button("06   EXTRAIR ENTRADA INDIVIDUAL (RAW)",()->chooseEntry());
  section("RESULTADO");
  output=text("Selecione seu PKG e execute a análise. O original nunca será modificado.",13,Color.rgb(217,232,246));output.setTextIsSelectable(true);output.setPadding(18,20,18,20);output.setBackgroundColor(panel);root.addView(output);
  root.addView(text("Modo somente leitura • Não descriptografa dados protegidos • A listagem de entradas não equivale à extração dos arquivos do jogo.",11,Color.rgb(151,170,191)));
 }
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(8,12,8,12);return t;}
 private void section(String s){TextView t=text(s,12,cyan);t.setTypeface(null,Typeface.BOLD);root.addView(t);}
 private void button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(26,74,104)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=10;root.addView(b,p);b.setOnClickListener(v->r.run());}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;if(req==PICK){file=data.getData();status.setText("Selecionado: "+file.getLastPathSegment());try{getContentResolver().takePersistableUriPermission(file,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}else if(req==EXPORT_ENTRY){exportEntry(data.getData());}else if(req==SAVE||req==SAVE_CSV){try(OutputStream o=getContentResolver().openOutputStream(data.getData())){if(o==null)throw new IOException("Não foi possível abrir destino");o.write((req==SAVE?report:csv).getBytes(StandardCharsets.UTF_8));toast("Arquivo salvo");}catch(Exception e){toast(e.toString());}}}
 private void chooseEntry(){
  if(file==null){toast("Selecione o PKG");return;}
  EditText field=new EditText(this);field.setInputType(2);field.setHint("Exemplo: 5 para a entrada ID 0x200");
  new AlertDialog.Builder(this).setTitle("Extrair entrada RAW").setMessage("Informe o índice da entrada (0 a 24 neste PKG). Somente dados brutos, sem descriptografia. Limite: 64 MiB por entrada.").setView(field).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{
   int index;try{index=Integer.parseInt(field.getText().toString().trim());}catch(Exception e){toast("Índice inválido");return;}
   new Thread(()->{try{
    byte[] h=new byte[32];byte[] header=new byte[32];
    try(InputStream in=getContentResolver().openInputStream(file)){if(in==null)throw new IOException("Arquivo inacessível");readFully(in,header);}
    long count=u32(header,0x10),offset=u32(header,0x18);
    if(index<0||index>=count||count>200000)throw new IOException("Índice fora da tabela");
    try(InputStream in=getContentResolver().openInputStream(file)){if(in==null)throw new IOException("Arquivo inacessível");skipFully(in,offset+index*32L);readFully(in,h);}
    long entryOffset=u32(h,16),entrySize=u32(h,20);
    if(entrySize<=0||entrySize>67108864L)throw new IOException("Tamanho não permitido: "+entrySize+" bytes (máximo 64 MiB)");
    long actualSize=-1;try(android.database.Cursor cur=getContentResolver().query(file,new String[]{android.provider.OpenableColumns.SIZE},null,null,null)){if(cur!=null&&cur.moveToFirst()&&!cur.isNull(0))actualSize=cur.getLong(0);}catch(Exception ignored){}
    if(actualSize>=0&&(entryOffset>actualSize||entrySize>actualSize-entryOffset))throw new IOException("Entrada ultrapassa tamanho do PKG");
    selectedEntry=index;selectedOffset=entryOffset;selectedSize=entrySize;
    runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/octet-stream");i.putExtra(Intent.EXTRA_TITLE,String.format(Locale.US,"rhay_entry_%04d_id_%X.bin",index,u32(h,0)));startActivityForResult(i,EXPORT_ENTRY);});
   }catch(Exception e){runOnUiThread(()->toast("Não foi possível selecionar entrada: "+e.getMessage()));}}).start();
  }).show();
 }
 private void exportEntry(Uri destination){
  final long offset=selectedOffset,size=selectedSize;final int index=selectedEntry;
  if(destination==null||file==null||index<0){toast("Extração cancelada");return;}
  status.setText("Exportando entrada #"+index+"...");
  new Thread(()->{
   String message;
   try(InputStream in=getContentResolver().openInputStream(file);OutputStream out=getContentResolver().openOutputStream(destination)){
    if(in==null||out==null)throw new IOException("Não foi possível abrir arquivo");
    skipFully(in,offset);byte[] buffer=new byte[65536];long remaining=size;
    while(remaining>0){int n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new EOFException("Entrada incompleta");if(n>0){out.write(buffer,0,n);remaining-=n;}}
    message="Entrada #"+index+" exportada: "+size+" bytes (RAW)";
   }catch(Exception e){message="Erro na exportação: "+e.getMessage();}
   final String m=message;runOnUiThread(()->{status.setText(m);toast(m);});
  }).start();
 }
 private void analyze(boolean scan){if(file==null){toast("Selecione um PKG");return;}status.setText("Analisando...");new Thread(()->{String r;try{r=read(scan);}catch(Exception e){r="Erro na leitura: "+e;}final String done=r;runOnUiThread(()->{report=done;output.setText(done);status.setText("Análise concluída");});}).start();}
 private static long u32(byte[] b,int p){return ((long)(b[p]&255)<<24)|((long)(b[p+1]&255)<<16)|((long)(b[p+2]&255)<<8)|(b[p+3]&255);}
 private static long u64(byte[] b,int p){return (u32(b,p)<<32)|u32(b,p+4);}
 private static String hex(long x){return "0x"+Long.toHexString(x).toUpperCase(Locale.US);}
 private static String ascii(byte[] b,int p,int len){int end=p;while(end<p+len&&b[end]!=0)end++;return new String(b,p,end-p,StandardCharsets.US_ASCII).trim();}
 private static String bytes(long x){return x+" bytes ("+String.format(Locale.US,"%.2f",x/1048576.0)+" MiB)";}
 private String read(boolean scan)throws IOException{
  StringBuilder csvData=new StringBuilder("indice,id,nome_ref,flags,extra,offset,tamanho,limites_validos\\n");
  StringBuilder s=new StringBuilder("RHAY PKG EXPLORER 2.3\n==============================\n");
  s.append("URI: ").append(file).append("\n");
  long fileSize=-1;
  try(android.database.Cursor c=getContentResolver().query(file,new String[]{android.provider.OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))fileSize=c.getLong(0);}catch(Exception ignored){}
  if(fileSize>=0)s.append("Tamanho real: ").append(bytes(fileSize)).append("\n");
  byte[] h=new byte[512];int n=0;
  try(InputStream in=getContentResolver().openInputStream(file)){if(in==null)throw new IOException("Não foi possível abrir PKG");while(n<h.length){int k=in.read(h,n,h.length-n);if(k<0)break;if(k==0)continue;n+=k;}}
  if(n<128)throw new IOException("Cabeçalho incompleto: "+n+" bytes");
  if(u32(h,0)!=0x7F434E54L){s.append("ERRO: assinatura não é PS4 CNT\n");return s.toString();}
  long entryCount=u32(h,0x10),entryCount2=u32(h,0x14),tableOffset=u32(h,0x18),tableSize=u32(h,0x1C),bodyOffset=u64(h,0x20),bodySize=u64(h,0x28);
  s.append("\n[IDENTIFICAÇÃO]\nAssinatura: 7F 43 4E 54 (PS4 CNT)\n");
  s.append("Content ID: ").append(ascii(h,0x40,36)).append("\n");
  s.append("Tipo/revisão (raw): ").append(hex(u32(h,4))).append("\n");
  s.append("\n[ESTRUTURA DO PKG]\nEntradas declaradas: ").append(entryCount).append("\n");
  s.append("Contador secundário: ").append(entryCount2).append("\n");
  s.append("Offset da tabela: ").append(hex(tableOffset)).append("\n");
  s.append("Tamanho da tabela (campo bruto): ").append(hex(tableSize)).append("\n");
  s.append("Offset do corpo: ").append(hex(bodyOffset)).append("\n");
  s.append("Tamanho do corpo (campo bruto): ").append(hex(bodySize)).append("\n");
  long expected=entryCount*32L;
  if(entryCount==0||entryCount>200000||tableOffset<512||expected>16*1024*1024L){s.append("\nTabela fora dos limites seguros; leitura cancelada.\n");}
  else{
   long wanted=tableOffset+expected;
   if(fileSize>=0&&wanted>fileSize){s.append("\nTabela ultrapassa tamanho do arquivo.\n");}
   else{
    byte[] entries=new byte[(int)expected];
    try(InputStream in=getContentResolver().openInputStream(file)){
     if(in==null)throw new IOException("Falha ao abrir arquivo");
     skipFully(in,tableOffset);
     readFully(in,entries);
    }
    s.append("\n[TABELA DE ENTRADAS — ").append(entryCount).append(" registros]\n");
    s.append("Campos numéricos apresentados em hexadecimal. IDs são metadados, não nomes de arquivos.\n");
    int suspicious=0;
    for(int i=0;i<entryCount;i++){
     int p=i*32;long id=u32(entries,p),nameOff=u32(entries,p+4),flags=u32(entries,p+8),unk=u32(entries,p+12),off=u32(entries,p+16),size=u32(entries,p+20);
     boolean valid=!(off<0||size<0||(fileSize>=0&&(off>fileSize||size>fileSize-off)));if(!valid)suspicious++;
     csvData.append(i).append(",").append(hex(id)).append(",").append(hex(nameOff)).append(",").append(hex(flags)).append(",").append(hex(unk)).append(",").append(off).append(",").append(size).append(",").append(valid).append("\\n");
     s.append(String.format(Locale.US,"#%04d ID=%s nomeRef=%s flags=%s extra=%s offset=%s tamanho=%s\n",i,hex(id),hex(nameOff),hex(flags),hex(unk),hex(off),hex(size)));
    }
    s.append("Entradas com limites suspeitos: ").append(suspicious).append("\n");
    s.append("Nota: offset e tamanho de cada registro são campos de 32 bits; os 8 bytes finais não são tamanho.\n");
   }
  }
  if(scan){
   s.append("\n[BUSCA DE ASSINATURAS — PRIMEIROS 32 MiB]\n");
   byte[] b=new byte[65536];long pos=0;int hits=0;
   String[] terms={".fmdl",".fpk",".ftex",".cpk","face","param.sfo","eboot.bin","sce_sys"};
   StringBuilder token=new StringBuilder();long start=0;
   try(InputStream in=getContentResolver().openInputStream(file)){
    if(in==null)throw new IOException("Falha ao abrir amostra");
    int count;
    while(pos<33554432&&(count=in.read(b,0,(int)Math.min(b.length,33554432-pos)))!=-1){
     for(int i=0;i<count;i++){int v=b[i]&255;
      if(v>=32&&v<=126){if(token.length()==0)start=pos+i;if(token.length()<256)token.append((char)v);}
      else{if(token.length()>0){String z=token.toString().toLowerCase(Locale.ROOT);for(String term:terms)if(z.contains(term)){if(hits<80)s.append(hex(start)).append(": ").append(token).append("\n");hits++;break;}}token.setLength(0);}
     }pos+=count;
    }
   }
   s.append("Bytes examinados: ").append(pos).append("\nOcorrências: ").append(hits).append("\n");
  }
  s.append("\n[LIMITAÇÕES]\nA tabela pode conter metadados ou entradas criptografadas. Esta versão não descriptografa, não reconstrói arquivos e não altera o PKG.\n");
  return s.toString();
 }
 private static void skipFully(InputStream in,long n)throws IOException{byte[] scratch=new byte[65536];while(n>0){long k=in.skip(n);if(k<=0){int x=in.read(scratch,0,(int)Math.min(n,scratch.length));if(x<0)throw new EOFException("Arquivo termina antes da tabela");k=x;}n-=k;}}
 private static void readFully(InputStream in,byte[] b)throws IOException{int p=0;while(p<b.length){int k=in.read(b,p,b.length-p);if(k<0)throw new EOFException("Tabela incompleta");if(k>0)p+=k;}}
}
