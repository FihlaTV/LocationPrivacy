
package info.guardianproject.locationprivacy;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    static final String OSMAND_FREE = "net.osmand";
    static final String OSMAND_PLUS = "net.osmand.plus";
    static final String TRUSTED_APP_PREF = "TRUSTED_APP_PREF";

    private TrustedAppEntry NONE;
    private TrustedAppEntry CHOOSER;

    private SharedPreferences prefs;
    private String selectedPackageName;
    private PackageManager pm;
    private LinearLayout installOsmAndLayout;
    private Button chooseTrustedAppButton;
    private int iconSize;
    private int dp20;
    private int dp10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Resources r = getResources();
        float density = r.getDisplayMetrics().density;
        dp10 = (int) (10 * density);
        dp20 = (int) (20 * density);
        iconSize = r.getDrawable(R.drawable.ic_launcher).getIntrinsicHeight();

        pm = getPackageManager();

        NONE = new TrustedAppEntry("NONE", R.string.none,
                android.R.drawable.ic_menu_close_clear_cancel);
        CHOOSER = new TrustedAppEntry("CHOOSER", R.string.chooser,
                android.R.drawable.ic_menu_more);

        installOsmAndLayout = (LinearLayout) findViewById(R.id.installOsmAndLayout);

        chooseTrustedAppButton = (Button) findViewById(R.id.chooseTrustedAppButton);
        chooseTrustedAppButton.setOnClickListener(new OnClickListener() {

            private ArrayList<TrustedAppEntry> list;

            private int getIndexOfProviderList(String packageName) {
                for (TrustedAppEntry app : list) {
                    if (app.packageName.equals(packageName)) {
                        return list.indexOf(app);
                    }
                }
                return 1; // this is CHOOSER
            }

            @Override
            public void onClick(View v) {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.setData(Uri.parse("geo:34.99393,-106.61568?z=11"));
                List<ResolveInfo> resInfo = pm.queryIntentActivities(viewIntent, 0);
                if (resInfo.isEmpty())
                    return;

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.choose_trusted_map_app);

                list = new ArrayList<TrustedAppEntry>();
                list.add(0, NONE);
                list.add(1, CHOOSER);

                for (ResolveInfo resolveInfo : resInfo) {
                    if (resolveInfo.activityInfo == null)
                        continue;
                    list.add(new TrustedAppEntry(resolveInfo.activityInfo));
                }
                ListAdapter adapter = new ArrayAdapter<TrustedAppEntry>(MainActivity.this,
                        android.R.layout.select_dialog_singlechoice, android.R.id.text1, list) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        TextView textView = (TextView) view.findViewById(android.R.id.text1);
                        textView.setCompoundDrawables(list.get(position).icon, null, null, null);
                        // margin between image and text
                        textView.setCompoundDrawablePadding(dp10);

                        return view;
                    }
                };

                builder.setSingleChoiceItems(adapter, getIndexOfProviderList(selectedPackageName),
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                TrustedAppEntry entry = list.get(which);
                                setSelectedApp(entry);
                                dialog.dismiss();
                            }
                        });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean osmandFreeInstalled = isInstalled(OSMAND_FREE);
        boolean osmandPlusInstalled = isInstalled(OSMAND_PLUS);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.contains(TRUSTED_APP_PREF)) {
            setSelectedApp(prefs.getString(TRUSTED_APP_PREF, CHOOSER.packageName));
        } else if (osmandPlusInstalled) {
            setSelectedApp(OSMAND_PLUS);
        } else if (osmandFreeInstalled) {
            setSelectedApp(OSMAND_FREE);
        } else {
            setSelectedApp(CHOOSER);
        }

        if (osmandFreeInstalled || osmandPlusInstalled) {
            installOsmAndLayout.setVisibility(View.GONE);
        } else {
            installOsmAndLayout.setVisibility(View.VISIBLE);
            Button installOsmAnd = (Button) findViewById(R.id.installOsmAnd);
            installOsmAnd.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    String uriString;
                    // FDroid has OSMAND_PLUS but in Play, it costs money
                    if (isInstalled("org.fdroid.fdroid"))
                        uriString = "market://details?id=" + OSMAND_PLUS;
                    else if (isInstalled("com.android.vending")) // Google Play
                        uriString = "market://details?id=" + OSMAND_FREE;
                    else
                        uriString = "market://search?q=" + OSMAND_FREE + "&c=apps";
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriString));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            });
        }
    }

    private void setSelectedApp(String packageName) {
        if (TextUtils.equals(packageName, NONE.packageName)) {
            setSelectedApp(NONE);
        } else if (TextUtils.equals(packageName, CHOOSER.packageName)) {
            setSelectedApp(CHOOSER);
        } else {
            try {
                PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
                setSelectedApp(new TrustedAppEntry(pi.activities[0]));
            } catch (NameNotFoundException e) {
                setSelectedApp(CHOOSER);
            }
        }
    }

    @SuppressLint("NewApi")
    private void setSelectedApp(final TrustedAppEntry entry) {
        selectedPackageName = entry.packageName;
        prefs.edit().putString(TRUSTED_APP_PREF, selectedPackageName).apply();
        chooseTrustedAppButton.setText(entry.simpleName);
        chooseTrustedAppButton.setCompoundDrawables(entry.icon, null, null, null);
        chooseTrustedAppButton.setPadding(dp20, dp20, dp20, dp20);
        chooseTrustedAppButton.setCompoundDrawablePadding(dp10);
    }

    private boolean isInstalled(String packageName) {
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.about:
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private class TrustedAppEntry {
        public final String packageName;
        public final String simpleName;
        public final Drawable icon;

        /**
         * Create from probing for {@link Intent}s
         *
         * @param resolveInfo
         */
        public TrustedAppEntry(ActivityInfo activityInfo) {
            this.packageName = activityInfo.packageName;
            this.simpleName = String.valueOf(activityInfo.loadLabel(pm));
            Drawable icon = activityInfo.loadIcon(pm);
            icon.setBounds(0, 0, iconSize, iconSize);
            this.icon = icon;
        }

        /**
         * Create manual entry for non-apps, i.e. "Chooser" or "None"
         *
         * @param simpleName
         * @param icon
         */
        public TrustedAppEntry(String fakePackageName, int simpleNameId, int iconId) {
            this.packageName = fakePackageName;
            this.simpleName = MainActivity.this.getString(simpleNameId);
            Drawable icon = MainActivity.this.getResources().getDrawable(iconId);
            icon.setBounds(0, 0, iconSize, iconSize);
            this.icon = icon;
        }

        @Override
        public String toString() {
            return simpleName;
        }
    }

}
