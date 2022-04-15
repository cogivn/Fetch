package com.tonyodev.fetchapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonElement;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2.Status;
import com.tonyodev.fetch2core.Downloader;
import com.tonyodev.fetch2core.Extras;
import com.tonyodev.fetch2core.FetchObserver;
import com.tonyodev.fetch2core.MutableExtras;
import com.tonyodev.fetch2core.Reason;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;


public class SingleDownloadActivity extends AppCompatActivity implements FetchObserver<Download> {

    private static final int STORAGE_PERMISSION_CODE = 100;

    private View mainView;
    private TextView progressTextView;
    private Button deleteButton;
    private TextView titleTextView;
    private TextView etaTextView;
    private TextView downloadSpeedTextView;
    private Request request;
    private Fetch fetch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_download);
        mainView = findViewById(R.id.activity_single_download);
        deleteButton = findViewById(R.id.delete_key);
        progressTextView = findViewById(R.id.progressTextView);
        titleTextView = findViewById(R.id.titleTextView);
        etaTextView = findViewById(R.id.etaTextView);
        downloadSpeedTextView = findViewById(R.id.downloadSpeedTextView);
        fetch = Fetch.Impl.getDefaultInstance();
        checkStoragePermission();

        deleteButton.setOnClickListener(v -> {
            List<Integer> ids = new ArrayList<>();
            ids.add(request.getId());
            fetch.deleteExtraByKey(ids, "testBoolean", null, null);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (request != null) {
            fetch.attachFetchObserversForDownload(request.getId(), this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (request != null) {
            fetch.removeFetchObserversForDownload(request.getId(), this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fetch.close();
    }

    @Override
    public void onChanged(Download data, @NotNull Reason reason) {
        updateViews(data, reason);
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        } else {
            enqueueDownload();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enqueueDownload();
        } else {
            Snackbar.make(mainView, R.string.permission_not_enabled, Snackbar.LENGTH_LONG).show();
        }
    }

    private void enqueueDownload() {
        final String url = Data.sampleUrls[0];
        final String filePath = Data.getSaveDir(this) + "/movies/" + Data.getNameFromUrl(url);
        request = new Request(url, filePath);

        ArrayList<String> tags = new ArrayList<>();
        tags.add("bookmark-1");
        request.setTags(tags);
        request.setDownloadOnEnqueue(true);
        request.setExtras(getExtrasForRequest(request));

        fetch.attachFetchObserversForDownload(request.getId(), this)
                .enqueue(request, result -> request = result,
                        result -> Timber.d("SingleDownloadActivity Error: %1$s", result.toString()));
    }

    private Extras getExtrasForRequest(Request request) {
        final MutableExtras extras = new MutableExtras();
        extras.putBoolean("testBoolean", true);
        extras.putString("testString", "test");
        extras.putFloat("testFloat", Float.MIN_VALUE);
        extras.putDouble("testDouble", Double.MIN_VALUE);
        extras.putInt("testInt", Integer.MAX_VALUE);
        extras.putLong("testLong", Long.MAX_VALUE);
        return extras;
    }

    private void updateViews(@NotNull Download download, Reason reason) {
        if (request.getId() == download.getId()) {
            if (reason == Reason.DOWNLOAD_QUEUED || reason == Reason.DOWNLOAD_COMPLETED) {
                setTitleView(download.getFile());
            }
            setProgressView(download.getStatus(), download.getProgress());
            etaTextView.setText(Utils.getETAString(this, download.getEtaInMilliSeconds()));
            downloadSpeedTextView.setText(Utils.getDownloadSpeedString(this, download.getDownloadedBytesPerSecond()));
            if (download.getError() != Error.NONE) {
                showDownloadErrorSnackBar(download.getError());
            }

            if (download.getStatus() == Status.COMPLETED) {
                final MutableExtras extras = new MutableExtras();
                extras.putBoolean("nathan_is_here", true);
                fetch.replaceExtras(download.getId(), extras, result -> {
                    Timber.d("update extras success = %s", result);
                }, result -> {
                    Timber.d("error= %s", result);
                });
            }
        }
    }

    private void setTitleView(@NonNull final String fileName) {
        final Uri uri = Uri.parse(fileName);
        titleTextView.setText(uri.getLastPathSegment());
    }

    private void setProgressView(@NonNull final Status status, final int progress) {
        switch (status) {
            case QUEUED: {
                progressTextView.setText(R.string.queued);
                break;
            }
            case ADDED: {
                progressTextView.setText(R.string.added);
                break;
            }
            case DOWNLOADING:
            case COMPLETED: {
                if (progress == -1) {
                    progressTextView.setText(R.string.downloading);
                } else {
                    final String progressString = getResources().getString(R.string.percent_progress, progress);
                    progressTextView.setText(progressString);
                }
                break;
            }
            default: {
                progressTextView.setText(R.string.status_unknown);
                break;
            }
        }
    }

    private void showDownloadErrorSnackBar(@NotNull Error error) {
        Downloader.Response response = error.getHttpResponse();
        if (response != null) {
            JsonElement element = response.getErrorResponse();
            if (element != null) {
                String message = element.getAsJsonObject().get("message").getAsString();
                final Snackbar snackbar = Snackbar.make(mainView,
                        "Download Failed: ErrorCode: " + message,
                        Snackbar.LENGTH_INDEFINITE);

                snackbar.setAction(R.string.retry, v -> {
                    fetch.retry(request.getId());
                    snackbar.dismiss();
                });
                snackbar.show();
            }
        }
    }

}
