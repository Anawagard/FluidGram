package com.awagard.batteryguardian;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import android.text.TextWatcher;
import android.text.Editable;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Set<String> chosen=new HashSet<>();
    private final List<ApplicationInfo> installed=new ArrayList<>();
    private final Set<String> excluded=new HashSet<>(Arrays.asList(
      "android","com.android.systemui","com.android.settings","com.android.phone",
      "com.google.android.gms","moe.shizuku.privileged.api","com.awagard.batteryguardian"));
    private LinearLayout layout,list;
    private TextView status;
    private EditText filter;
    private static final int REQUEST=1001;
    private final Shizuku.OnRequestPermissionResultListener listener=(code,result)-> {
       if(code==REQUEST)ui.post(this::updateStatus);
    };
    @Override public void onCreate(Bundle b){
      super.onCreate(b);
      Shizuku.addRequestPermissionResultListener(listener);
      ScrollView scroll=new ScrollView(this);
      layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(22,20,22,20);
      layout.setBackgroundColor(Color.rgb(245,250,247));scroll.addView(layout);setContentView(scroll);
      heading("Battery Guardian",26);
      heading("Управление фоновой активностью через Shizuku",14);
      status=heading("Проверка Shizuku",16);
      button("Подключить Shizuku",()->connect());
      button("Обновить список и статус",()->{updateStatus();load();});
      heading("Отмечай только необязательные приложения. Ограничения могут задержать уведомления.",13);
      filter=new EditText(this);filter.setSingleLine(true);filter.setHint("Поиск приложения");layout.addView(filter);
      filter.addTextChangedListener(new TextWatcher(){
        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        public void onTextChanged(CharSequence s,int start,int before,int count){render();}
        public void afterTextChanged(Editable e){}
      });
      button("Ограничить выбранные",()->confirm(true));
      button("Восстановить выбранные",()->confirm(false));
      button("Проверить состояние выбранных",()->inspect());
      list=new LinearLayout(this);list.setOrientation(1);layout.addView(list);
      updateStatus();load();
    }
    private TextView heading(String t,int size){
      TextView v=new TextView(this);v.setText(t);v.setTextColor(Color.rgb(18,76,64));
      v.setTextSize(size);v.setPadding(4,10,4,10);layout.addView(v);return v;
    }
    private void button(String t,Runnable action){
      Button b=new Button(this);b.setAllCaps(false);b.setText(t);layout.addView(b);b.setOnClickListener(v->action.run());
    }
    private void msg(String text){ui.post(()->Toast.makeText(this,text,Toast.LENGTH_LONG).show());}
    private boolean ready(){
       try{return Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}
       catch(Throwable e){return false;}
    }
    private void updateStatus(){
       try{status.setText(!Shizuku.pingBinder()?"Shizuku не запущен":
         ready()?"Shizuku подключён":"Shizuku: нужно разрешение");}
       catch(Throwable e){status.setText("Shizuku недоступен");}
    }
    private void connect(){
       try{if(!Shizuku.pingBinder()){msg("Запусти Shizuku и вернись сюда");return;}
          if(!ready())Shizuku.requestPermission(REQUEST);else msg("Разрешение уже предоставлено");
          updateStatus();
       }catch(Throwable t){msg("Ошибка: "+t.getMessage());}
    }
    private void load(){
       worker.execute(()->{
         List<ApplicationInfo> result=new ArrayList<>();
         PackageManager pm=getPackageManager();
         for(ApplicationInfo a:pm.getInstalledApplications(0)){
           if((a.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)continue;
           if(excluded.contains(a.packageName)||a.packageName.equals(getPackageName()))continue;
           result.add(a);
         }
         result.sort(Comparator.comparing(a->a.loadLabel(pm).toString().toLowerCase(Locale.ROOT)));
         ui.post(()->{installed.clear();installed.addAll(result);render();});
       });
    }
    private void render(){
       if(list==null||filter==null)return;
       list.removeAllViews();String q=filter.getText().toString().toLowerCase(Locale.ROOT);
       for(ApplicationInfo a:installed){
          String name=a.loadLabel(getPackageManager()).toString();
          if(!name.toLowerCase(Locale.ROOT).contains(q)&&!a.packageName.toLowerCase(Locale.ROOT).contains(q))continue;
          CheckBox c=new CheckBox(this);c.setText(name+"\n"+a.packageName);c.setChecked(chosen.contains(a.packageName));
          c.setOnCheckedChangeListener((v,on)->{if(on)chosen.add(a.packageName);else chosen.remove(a.packageName);});
          list.addView(c);
       }
    }
    private void confirm(boolean limit){
       if(!ready()){msg("Сначала подключи Shizuku");return;}
       if(chosen.isEmpty()){msg("Выбери приложения");return;}
       List<String> items=new ArrayList<>(chosen);
       new AlertDialog.Builder(this).setTitle(limit?"Ограничить фон":"Восстановить фон")
        .setMessage("Изменить настройки для "+items.size()+" приложений?")
        .setNegativeButton("Отмена",null)
        .setPositiveButton("Применить",(dialog,which)->worker.execute(()->modify(items,limit))).show();
    }
    private void modify(List<String> pkgs,boolean limit){
       int done=0;StringBuilder errors=new StringBuilder();
       for(String pkg:pkgs){
         if(excluded.contains(pkg)||pkg.equals(getPackageName()))continue;
         try{
           String key="previous_"+pkg;
           if(limit){
              String current=command("cmd","appops","get",pkg,"RUN_ANY_IN_BACKGROUND");
              String previous=current.contains("ignore")?"ignore":current.contains("deny")?"deny":
                              current.contains("allow")?"allow":"default";
              if(!getPreferences(0).contains(key))getPreferences(0).edit().putString(key,previous).commit();
              command("cmd","appops","set",pkg,"RUN_ANY_IN_BACKGROUND","ignore");
           }else{
              if(!getPreferences(0).contains(key))throw new IOException("нет сохранённой настройки");
              String original=getPreferences(0).getString(key,"default");
              command("cmd","appops","set",pkg,"RUN_ANY_IN_BACKGROUND",original);
              getPreferences(0).edit().remove(key).commit();
           }
           done++;
         }catch(Exception e){errors.append(pkg).append(": ").append(e.getMessage()).append("\n");}
       }
       final int count=done;
       ui.post(()->new AlertDialog.Builder(this).setTitle("Успешно: "+count+"/"+pkgs.size())
         .setMessage(errors.length()==0?"Настройки применены":errors.toString())
         .setPositiveButton("ОК",null).show());
    }
    private void inspect(){
      if(!ready()){msg("Сначала подключи Shizuku");return;}
      List<String> pkgs=new ArrayList<>(chosen);
      worker.execute(()->{
        StringBuilder sb=new StringBuilder();
        for(String pkg:pkgs)try{sb.append(pkg).append(": ").append(command("cmd","appops","get",pkg,"RUN_ANY_IN_BACKGROUND")).append("\n");}
        catch(Exception e){sb.append(pkg).append(": ошибка ").append(e.getMessage()).append("\n");}
        ui.post(()->new AlertDialog.Builder(this).setTitle("Состояние ограничений")
         .setMessage(sb.length()==0?"Ничего не выбрано":sb.toString()).setPositiveButton("ОК",null).show());
      });
    }
    @SuppressWarnings("deprecation")
    private String command(String... a)throws Exception{
      String[] args=new String[a.length+1];args[0]="/system/bin/"+a[0];System.arraycopy(a,1,args,1,a.length);
      ShizukuRemoteProcess p=Shizuku.newProcess(args,null,null);
      String output=read(p.getInputStream()), err=read(p.getErrorStream());
      int code=p.waitFor();p.destroy();
      if(code!=0)throw new IOException("код "+code+" "+err);
      return output;
    }
    private String read(InputStream in)throws IOException{
      ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[1024];int n;
      while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>16000)break;}
      return out.toString("UTF-8").trim();
    }
    @Override protected void onDestroy(){
      Shizuku.removeRequestPermissionResultListener(listener);worker.shutdownNow();super.onDestroy();
    }
}