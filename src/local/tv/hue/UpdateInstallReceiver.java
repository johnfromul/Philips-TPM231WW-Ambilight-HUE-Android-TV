package local.tv.hue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirmation);
                } catch (RuntimeException error) {
                    showError(context);
                }
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS
                && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            Diagnostics.event(Diagnostics.id(), "update.install.error", "status=" + status);
            showError(context);
        }
    }

    private static void showError(Context context) {
        Context localized = AppLanguage.wrap(context);
        android.widget.Toast.makeText(localized, AppUpdates.label(localized, "error"),
                android.widget.Toast.LENGTH_LONG).show();
    }
}
