package org.questfreedom.ui;
import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.questfreedom.core.PackagePolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A portable 2D Android launcher. NOT an OS image or VR runtime. */
public final class MainActivity extends Activity {
 private static final int BG=0xff0c131d, PANEL=0xff192533, TEXT=0xffecf5ff, ACCENT=0xff55e0cf;
 private boolean hideMeta=true;
 private int dp(int n) {return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  hideMeta=getPreferences(MODE_PRIVATE).getBoolean("hide_meta",true);
  render();
 }
 private TextView label(String s,int size,int c,boolean bold) {
  TextView v=new TextView(this);
  v.setText(s); v.setTextColor(c); v.setTextSize(size);
  if(bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  return v;
 }
 private Button button(String s,Runnable action) {
  Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(TEXT);
  GradientDrawable shape=new GradientDrawable(); shape.setColor(PANEL); shape.setCornerRadius(dp(15));
  b.setBackground(shape); b.setOnClickListener(v->action.run());
  return b;
 }
 private void start(Intent i) {
  try {startActivity(i);} catch(ActivityNotFoundException|SecurityException e) {
   Toast.makeText(this,"Unavailable on this device",Toast.LENGTH_SHORT).show();
  }
 }
 private void render() {
  ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(BG); scroll.setFillViewport(true);
  LinearLayout body=new LinearLayout(this); body.setPadding(dp(24),dp(26),dp(24),dp(22)); body.setOrientation(1);
  scroll.addView(body); setContentView(scroll);
  body.addView(label("QUESTFREEDOM",13,ACCENT,true));
  body.addView(label("Your Android space",30,TEXT,true));
  body.addView(label("Independent launcher prototype · v0.1 alpha",13,0xffa6b8c8,false));
  LinearLayout bar=new LinearLayout(this); bar.setPadding(0,dp(22),0,dp(20));
  body.addView(bar);
  bar.addView(button("Settings",()->start(new Intent(Settings.ACTION_SETTINGS))),new LinearLayout.LayoutParams(0,dp(50),1));
  bar.addView(button("Files",()->{Intent i=new Intent(Intent.ACTION_GET_CONTENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); start(i);}),new LinearLayout.LayoutParams(0,dp(50),1));
  bar.addView(button("Refresh",this::render),new LinearLayout.LayoutParams(0,dp(50),1));
  body.addView(label("Applications",22,TEXT,true));
  body.addView(label("Meta apps are hidden in this launcher, not removed from the OS.",12,0xffa6b8c8,false));
  PackageManager pm=getPackageManager();
  Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
  List<ResolveInfo> apps=new ArrayList<>(pm.queryIntentActivities(launcher,0));
  apps.removeIf(r->r.activityInfo==null||!PackagePolicy.shouldShow(r.activityInfo.packageName,hideMeta)||getPackageName().equals(r.activityInfo.packageName));
  apps.sort(Comparator.comparing(r->r.loadLabel(pm).toString(),String.CASE_INSENSITIVE_ORDER));
  GridLayout grid=new GridLayout(this); grid.setColumnCount(getResources().getDisplayMetrics().widthPixels>dp(800)?3:2);
  body.addView(grid);
  for(ResolveInfo a:apps) {
   String pkg=a.activityInfo.packageName,cls=a.activityInfo.name;
   Button card=button(a.loadLabel(pm).toString(),()->{
    Intent i=new Intent(Intent.ACTION_MAIN); i.setClassName(pkg,cls); i.addCategory(Intent.CATEGORY_LAUNCHER); start(i);
   });
   GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=dp(175); lp.height=dp(80); lp.setMargins(dp(5),dp(5),dp(5),dp(5)); grid.addView(card,lp);
  }
  if(apps.isEmpty())body.addView(label("No apps found",14,TEXT,false));
  body.addView(button(hideMeta?"Show Meta apps":"Hide Meta apps",()->{
   hideMeta=!hideMeta; getPreferences(MODE_PRIVATE).edit().putBoolean("hide_meta",hideMeta).apply(); render();
  }),new LinearLayout.LayoutParams(-1,dp(55)));
 }
}
