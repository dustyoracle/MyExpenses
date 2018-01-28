/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.activity;

import android.accounts.Account;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.DialogUtils;
import org.totschnig.myexpenses.fragment.SettingsFragment;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.service.DailyAutoBackupScheduler;
import org.totschnig.myexpenses.sync.GenericAccountService;
import org.totschnig.myexpenses.task.TaskExecutionFragment;
import org.totschnig.myexpenses.util.DistribHelper;
import org.totschnig.myexpenses.util.PermissionHelper;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.UiUtils;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.widget.AbstractWidget;
import org.totschnig.myexpenses.widget.AccountWidget;
import org.totschnig.myexpenses.widget.TemplateWidget;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;

import static org.totschnig.myexpenses.preference.PrefKey.AUTO_BACKUP;
import static org.totschnig.myexpenses.preference.PrefKey.AUTO_BACKUP_TIME;
import static org.totschnig.myexpenses.preference.PrefKey.GROUP_MONTH_STARTS;
import static org.totschnig.myexpenses.preference.PrefKey.GROUP_WEEK_STARTS;
import static org.totschnig.myexpenses.preference.PrefKey.PROTECTION_DEVICE_LOCK_SCREEN;
import static org.totschnig.myexpenses.preference.PrefKey.PROTECTION_LEGACY;
import static org.totschnig.myexpenses.preference.PrefKey.PERFORM_PROTECTION_SCREEN;
import static org.totschnig.myexpenses.preference.PrefKey.PLANNER_CALENDAR_ID;
import static org.totschnig.myexpenses.preference.PrefKey.PROTECTION_ENABLE_ACCOUNT_WIDGET;
import static org.totschnig.myexpenses.preference.PrefKey.PROTECTION_ENABLE_TEMPLATE_WIDGET;
import static org.totschnig.myexpenses.preference.PrefKey.SYNC_FREQUCENCY;
import static org.totschnig.myexpenses.preference.PrefKey.UI_FONTSIZE;
import static org.totschnig.myexpenses.preference.PrefKey.UI_HOME_SCREEN_SHORTCUTS;
import static org.totschnig.myexpenses.preference.PrefKey.UI_LANGUAGE;
import static org.totschnig.myexpenses.preference.PrefKey.UI_THEME_KEY;
import static org.totschnig.myexpenses.sync.GenericAccountService.HOUR_IN_SECONDS;

/**
 * Present references screen defined in Layout file
 *
 * @author Michael Totschnig
 */
public class MyPreferenceActivity extends ProtectedFragmentActivity implements
    OnSharedPreferenceChangeListener,
    ContribIFace, PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

  public static final String KEY_OPEN_PREF_KEY = "openPrefKey";
  private String initialPrefToShow;
  private SettingsFragment activeFragment;
  private Snackbar snackbar;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    setTheme(MyApplication.getThemeId());
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings);
    setupToolbar(true);
    if (savedInstanceState == null) {
      // Create the fragment only when the activity is created for the first time.
      // ie. not after orientation changes
      Fragment fragment = getFragment();
      if (fragment == null) {
        fragment = new SettingsFragment();
      }

      FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
      ft.replace(R.id.fragment_container, fragment, SettingsFragment.class.getSimpleName());
      ft.commit();
    }
    initialPrefToShow = savedInstanceState == null ?
        getIntent().getStringExtra(KEY_OPEN_PREF_KEY) : null;

    //when a user no longer has access to auto backup we do not want him to believe that it works
    if (!ContribFeature.AUTO_BACKUP.hasAccess() && ContribFeature.AUTO_BACKUP.usagesLeft() < 1) {
      AUTO_BACKUP.putBoolean(false);
    }
  }

  private SettingsFragment getFragment() {
    return activeFragment;
  }

  public void setFragment(SettingsFragment fragment) {
    activeFragment = fragment;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    //currently no help menu
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home && getSupportFragmentManager().getBackStackEntryCount() > 0) {
      getSupportFragmentManager().popBackStack();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onResume() {
    super.onResume();
    MyApplication.getInstance().getSettings().registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    MyApplication.getInstance().getSettings().unregisterOnSharedPreferenceChangeListener(this);
  }

  private void restart() {
    Intent intent = getIntent();
    finish();
    startActivity(intent);
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
      case R.id.FTP_DIALOG:
        return DialogUtils.sendWithFTPDialog(this);
      case R.id.MORE_INFO_DIALOG:
        LayoutInflater li = LayoutInflater.from(this);
        //noinspection InflateParams
        View view = li.inflate(R.layout.more_info, null);
        ((TextView) view.findViewById(R.id.aboutVersionCode)).setText(DistribHelper.getVersionInfo(this));
        TextView projectContainer = view.findViewById(R.id.project_container);
        projectContainer.setText(Utils.makeBulletList(this,
            Stream.of(Utils.getProjectDependencies(this))
                .map(project -> {
                  String name = project.get("name");
                  return String.format("%s, from %s, licenced under %s",
                      project.containsKey("extra_info") ?
                          String.format("%s (%s)", name, project.get("extra_info")) : name,
                      project.get("url"), project.get("licence"));
                }).collect(Collectors.toList()), R.drawable.ic_menu_forward));
        TextView additionalContainer = view.findViewById(R.id.additional_container);
        additionalContainer.setText(Utils.makeBulletList(this,
            Arrays.asList(getResources().getStringArray(R.array.additional_credits)),
            R.drawable.ic_menu_forward));
        return new AlertDialog.Builder(this)
            .setTitle(R.string.pref_more_info_dialog_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create();
    }
    return null;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                        String key) {
    if (key.equals(UI_LANGUAGE.getKey()) ||
        key.equals(GROUP_MONTH_STARTS.getKey()) ||
        key.equals(GROUP_WEEK_STARTS.getKey())) {
      DatabaseConstants.buildLocalized(Locale.getDefault());
      Transaction.buildProjection();
    }
    if (key.equals(UI_FONTSIZE.getKey())) {
      updateAllWidgets();
    }
    if (key.equals(PROTECTION_LEGACY.getKey()) || key.equals(PROTECTION_DEVICE_LOCK_SCREEN.getKey())) {
      getFragment().setProtectionDependentsState();
      updateAllWidgets();
    } else if (key.equals(UI_FONTSIZE.getKey()) ||
        key.equals(UI_LANGUAGE.getKey()) ||
        key.equals(UI_THEME_KEY.getKey())) {
      restart();
    } else if (key.equals(PROTECTION_ENABLE_ACCOUNT_WIDGET.getKey())) {
      //Log.d("DEBUG","shared preference changed: Account Widget");
      updateWidgets(AccountWidget.class);
    } else if (key.equals(PROTECTION_ENABLE_TEMPLATE_WIDGET.getKey())) {
      //Log.d("DEBUG","shared preference changed: Template Widget");
      updateWidgets(TemplateWidget.class);
    } else if (key.equals(AUTO_BACKUP.getKey()) || key.equals(AUTO_BACKUP_TIME.getKey())) {
      DailyAutoBackupScheduler.updateAutoBackupAlarms(this);
    } else if (key.equals(SYNC_FREQUCENCY.getKey())) {
      for (Account account : GenericAccountService.getAccountsAsArray(this)) {
        ContentResolver.addPeriodicSync(account, TransactionProvider.AUTHORITY, Bundle.EMPTY,
            SYNC_FREQUCENCY.getInt(GenericAccountService.DEFAULT_SYNC_FREQUENCY_HOURS) * HOUR_IN_SECONDS);
      }
    }
  }

  private void updateAllWidgets() {
    updateWidgets(AccountWidget.class);
    updateWidgets(TemplateWidget.class);
  }

  private void updateWidgets(Class<? extends AbstractWidget<?>> provider) {
    AbstractWidget.updateWidgets(this, provider);
  }

  public void validateLicence() {
    startValidationTask(TaskExecutionFragment.TASK_VALIDATE_LICENCE, R.string.progress_validating_licence);
  }

  private void startValidationTask(int taskId, int progressResId) {
    startTaskExecution(taskId, new String[]{}, null, 0);
    snackbar = Snackbar.make(
        findViewById(R.id.fragment_container), progressResId, Snackbar.LENGTH_INDEFINITE);
    UiUtils.configureSnackbarForDarkTheme(snackbar);
    snackbar.show();
  }

  private Intent findDirPicker() {
    Intent intent = new Intent("com.estrongs.action.PICK_DIRECTORY ");
    intent.putExtra("com.estrongs.intent.extra.TITLE", "Select Directory");
    if (Utils.isIntentAvailable(this, intent)) {
      return intent;
    }
    return null;
  }

  @Override
  public void contribFeatureCalled(ContribFeature feature, Serializable tag) {
    if (feature == ContribFeature.CSV_IMPORT) {
      Intent i = new Intent(this, CsvImportActivity.class);
      startActivity(i);
    }
  }

  @Override
  public void contribFeatureNotCalled(ContribFeature feature) {

  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String permissions[], @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode) {
      case PermissionHelper.PERMISSIONS_REQUEST_WRITE_CALENDAR:
        if (PermissionHelper.allGranted(grantResults)) {
          initialPrefToShow = PLANNER_CALENDAR_ID.getKey();
        }
    }
  }

  @Override
  protected void onResumeFragments() {
    super.onResumeFragments();
    if (initialPrefToShow != null) {
      getFragment().showPreference(initialPrefToShow);
      initialPrefToShow = null;
    }
  }

  @Override
  public boolean onPreferenceStartScreen(PreferenceFragmentCompat preferenceFragmentCompat,
                                         PreferenceScreen preferenceScreen) {
    final String key = preferenceScreen.getKey();
    if (key.equals(PERFORM_PROTECTION_SCREEN.getKey()) &&
        MyApplication.getInstance().isProtected()) {
      DialogUtils.showPasswordDialog(this, DialogUtils.passwordDialog(this, true), false,
          () -> startPreferenceScreen(key));
      return true;
    }
    if (key.equals(UI_HOME_SCREEN_SHORTCUTS.getKey())) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        //TODO on O we will be able to pin the shortcuts
        Toast.makeText(this, R.string.home_screen_shortcuts_nougate_info, Toast.LENGTH_LONG).show();
        return true;
      }
    }
    startPreferenceScreen(key);
    return true;
  }

  @Override
  public void onPostExecute(int taskId, Object o) {
    super.onPostExecute(taskId, o);
    if (taskId == TaskExecutionFragment.TASK_VALIDATE_LICENCE ||
        taskId == TaskExecutionFragment.TASK_REMOVE_LICENCE) {
      snackbar.dismiss();
      if (o instanceof Result) {
        Result r = ((Result) o);
        snackbar = Snackbar.make(
            findViewById(R.id.fragment_container), r.print(this), Snackbar.LENGTH_LONG);
        UiUtils.configureSnackbarForDarkTheme(snackbar);
        snackbar.show();
        getFragment().setProtectionDependentsState();
        getFragment().configureContribPrefs();

      }
    }
  }

  @Override
  public boolean dispatchCommand(int command, Object tag) {
    if (command == R.id.REMOVE_LICENCE_COMMAND) {
      startValidationTask(TaskExecutionFragment.TASK_REMOVE_LICENCE, R.string.progress_removing_licence);
      return true;
    }
    return super.dispatchCommand(command, tag);
  }

  private void startPreferenceScreen(String key) {
    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
    SettingsFragment fragment = new SettingsFragment();
    Bundle args = new Bundle();
    args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, key);
    fragment.setArguments(args);
    ft.replace(R.id.fragment_container, fragment, key);
    ft.addToBackStack(key);
    ft.commit();
  }

}