package com.chethan.googledrivedemo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;
import java.util.Iterator;

import static android.R.attr.id;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, CompoundButton.OnCheckedChangeListener {

    private static final int RESOLVE_CONNECTION_REQUEST_CODE = 123;

    private static final String TAG = MainActivity.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;
    private Switch fanSwitch;
    private Switch lightSwitch;
    private ProgressDialog progressDialog;
    private DriveId driveFileId;

    private boolean isFanON;
    private boolean isLightON;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        fanSwitch = (Switch) findViewById(R.id.fanSwitch);
        lightSwitch = (Switch) findViewById(R.id.lightSwitch);

        fanSwitch.setOnCheckedChangeListener(this);
        lightSwitch.setOnCheckedChangeListener(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(true);
        progressDialog.setTitle("Initializing....");
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        upLoadIOTContent(driveFileId);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String getIOTContentString() {
        JSONObject iotJsonObject = new JSONObject();
        try {
            iotJsonObject.put("fan", fanSwitch.isChecked());
            iotJsonObject.put("light", lightSwitch.isChecked());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return iotJsonObject.toString();
    }

    /**
     * Create a new file and save it to Drive.
     */
    private void createIOTDataFile() {
        // Start by creating a new contents, and setting a callback.
        Log.i(TAG, "Creating new contents.");
        Drive.DriveApi.newDriveContents(mGoogleApiClient)
                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                    @Override
                    public void onResult(DriveApi.DriveContentsResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.i(TAG, "Failed to create new IOT file contents.");
                            return;
                        }
                        // Otherwise, we can write our data to the new contents.
                        Log.i(TAG, "New IOT created.");
                        // Get an output stream for the contents.
                        OutputStream outputStream = result.getDriveContents().getOutputStream();
                        try {
                            outputStream.write(getIOTContentString().getBytes());
                        } catch (IOException e1) {
                            Log.i(TAG, "Unable to write file contents.");
                        }
                        // Create the initial metadata - MIME type and title.
                        // Note that the user will be able to change the title later.
                        MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                                .setMimeType("text/plain").setTitle("IOT-PROJECT.txt").build();
                        // Create a file in the root folder
                        Drive.DriveApi.getRootFolder(mGoogleApiClient)
                                .createFile(mGoogleApiClient, metadataChangeSet, result.getDriveContents())
                                .setResultCallback(new ResultCallback<DriveFolder.DriveFileResult>() {
                                    @Override
                                    public void onResult(@NonNull DriveFolder.DriveFileResult driveFileResult) {
                                        Log.d(TAG, "result..." + driveFileResult.toString());
                                        driveFileId = driveFileResult.getDriveFile().getDriveId();
                                        progressDialog.cancel();
                                    }
                                });
                    }
                });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected......");
        progressDialog.show();
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.MIME_TYPE, "text/plain"))
                .addFilter(Filters.eq(SearchableField.TITLE, "IOT-PROJECT.txt"))
                .build();
        Drive.DriveApi.query(mGoogleApiClient, query)
                .setResultCallback(
                        new ResultCallback<DriveApi.MetadataBufferResult>() {
                            @Override
                            public void onResult(DriveApi.MetadataBufferResult result) {
                                Log.d(TAG, "onResult......" + result.getStatus().isSuccess());
                                int count = 0;
                                if (result.getStatus().isSuccess()) {
                                    Iterator<Metadata> iterator = result.getMetadataBuffer().iterator();
                                    while (iterator.hasNext()) {
                                        count++;
                                        Metadata next = iterator.next();
                                        driveFileId = next.getDriveId();
                                        Log.d(TAG, "driveFileId:" + driveFileId);
                                        getIOTContent(driveFileId);
                                    }
                                    Log.d(TAG, "onResult......" + count);
                                    if (count == 0) {
                                        createIOTDataFile();
                                    }
                                } else {
                                    createIOTDataFile();
                                }
                            }
                        });
    }

    private void getIOTContent(DriveId driveFileId) {
        progressDialog.show();
        DriveFile file = Drive.DriveApi.getFile(mGoogleApiClient, driveFileId);
        DriveFile.DownloadProgressListener mDownloadProgressListener = new DriveFile.DownloadProgressListener() {
            @Override
            public void onProgress(long bytesDownloaded, long bytesExpected) {

            }
        };
        file.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, mDownloadProgressListener)
                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                    @Override
                    public void onResult(DriveApi.DriveContentsResult result) {
                        try {
                            if (!result.getStatus().isSuccess()) {
                                return;
                            }
                            DriveContents contents = result.getDriveContents();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(contents.getInputStream()));
                            StringBuilder builder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                builder.append(line);
                            }
                            String contentsAsString = builder.toString();
                            JSONObject jsonObject = new JSONObject(contentsAsString);
                            fanSwitch.setOnCheckedChangeListener(null);
                            lightSwitch.setOnCheckedChangeListener(null);
                            fanSwitch.setChecked(jsonObject.getBoolean("fan"));
                            lightSwitch.setChecked(jsonObject.getBoolean("light"));
                            fanSwitch.setOnCheckedChangeListener(MainActivity.this);
                            lightSwitch.setOnCheckedChangeListener(MainActivity.this);
                            progressDialog.cancel();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    private void upLoadIOTContent(DriveId driveFileId) {
        progressDialog.show();
        DriveFile file = driveFileId.asDriveFile();//Drive.DriveApi.getFile(mGoogleApiClient, driveFileId);
        DriveFile.DownloadProgressListener mDownloadProgressListener = new DriveFile.DownloadProgressListener() {
            @Override
            public void onProgress(long bytesDownloaded, long bytesExpected) {
                int progress = (int) (bytesDownloaded * 100 / bytesExpected);
                Log.d(TAG, String.format("Loading progress: %d percent", progress));
            }
        };
        file.open(mGoogleApiClient, DriveFile.MODE_WRITE_ONLY, mDownloadProgressListener)
                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                    @Override
                    public void onResult(DriveApi.DriveContentsResult result) {
                        try {
                            if (!result.getStatus().isSuccess()) {
                                return;
                            }
                            DriveContents contents = result.getDriveContents();
                            OutputStream outputStream = contents.getOutputStream();
                            outputStream.write(getIOTContentString().getBytes());
                            contents.commit(mGoogleApiClient, null).setResultCallback(new ResultCallback<Status>() {
                                @Override
                                public void onResult(Status result) {
                                    Log.d(TAG, "onResult..." + result);
                                    // Handle the response status
                                    progressDialog.cancel();
                                    if (result.isSuccess()) {

                                    } else {
                                        //TODO
                                    }
                                }
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended......i" + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed......connectionResult.hasResolution()" + connectionResult.toString());
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE);
            } catch (IntentSender.SendIntentException e) {
                Log.d(TAG, "onConnectionFailed......SendIntentException" + e.getMessage());
                // Unable to resolve, message user appropriately
                e.printStackTrace();
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult......Request" + requestCode + " ResultCode:" + resultCode + " data:" + data);
        switch (requestCode) {
            case RESOLVE_CONNECTION_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    mGoogleApiClient.connect();
                }
                break;
        }
    }

}
