package com.tokbox.android.meet;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.opentok.android.OpenTokConfig;
import com.tokbox.android.meet.services.ClearNotificationService;
import com.tokbox.android.meet.services.ClearNotificationService.ClearBinder;
import com.tokbox.android.ui.AudioLevelView;

public class ChatRoomActivity extends Activity {

    private static final String LOGTAG = "ChatRoomActivity";

    private static final int ANIMATION_DURATION = 500;
    public static final String ARG_ROOM_ID = "roomId";
    public static final String ARG_USERNAME_ID = "usernameId";

    private String serverURL = null;
    private String mRoomName;
    private Room mRoom;
    private String mUsername = null;

    private ProgressDialog mConnectingDialog;
    private AlertDialog mErrorDialog;
    private EditText mMessageEditText;
    private RelativeLayout mMessageBox;
    private AudioLevelView mAudioLevelView;

    protected Handler mHandler = new Handler();
    private NotificationCompat.Builder mNotifyBuilder;
    private NotificationManager mNotificationManager;
    private ServiceConnection mConnection;
    private boolean mIsBound = false;

    private ViewGroup mPreview;
    private ViewGroup mLastParticipantView;
    private LinearLayout mParticipantsView;
    private ProgressBar mLoadingSub; // Spinning wheel for loading subscriber view

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.chat_room_layout);

        //Custom title bar
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);

        View cView = getLayoutInflater().inflate(R.layout.custom_title, null);
        actionBar.setCustomView(cView);

        mMessageBox = (RelativeLayout) findViewById(R.id.messagebox);
        mMessageEditText = (EditText) findViewById(R.id.message);

        mPreview = (ViewGroup) findViewById(R.id.publisherview);
        mParticipantsView = (LinearLayout) findViewById(R.id.gallery);
        mLastParticipantView = (ViewGroup) findViewById(R.id.mainsubscriberView);
        mLoadingSub = (ProgressBar) findViewById(R.id.loadingSpinner);

        mAudioLevelView = (AudioLevelView) findViewById(R.id.subscribermeter);
        mAudioLevelView.setIcons(BitmapFactory.decodeResource(getResources(),
                R.drawable.headset));

        Uri url = getIntent().getData();
        serverURL = getResources().getString(R.string.serverURL);

        if (url == null) {
            mRoomName = getIntent().getStringExtra(ARG_ROOM_ID);
            mUsername = getIntent().getStringExtra(ARG_USERNAME_ID);
        } else {
            if (url.getScheme().equals("otmeet")) {
                mRoomName = url.getHost();
            } else {
                mRoomName = url.getPathSegments().get(0);
            }
        }

        TextView title = (TextView) findViewById(R.id.title);
        title.setText(mRoomName);

        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        initializeRoom();

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Remove publisher & subscriber views because we want to reuse them
        if (mRoom != null && mRoom.getParticipants().size() > 0) {
            mRoom.getParticipantsViewContainer()
                    .removeAllViews();
            mRoom.getLastParticipantView().removeAllViews();
        }
        reloadInterface();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        //Pause implies go to audio only mode
        if (mRoom != null) {
            mRoom.onPause();

            // Remove publisher & subscriber views because we want to reuse them
            if (mRoom != null && mRoom.getParticipants().size() > 0) {
                mRoom.getParticipantsViewContainer()
                        .removeAllViews();
                mRoom.getLastParticipantView().removeAllViews();
            }
        }

        //Add notification to status bar which gets removed if the user force kills the application.
        mNotifyBuilder = new NotificationCompat.Builder(this)
                .setContentTitle("Meet TokBox")
                .setContentText("Ongoing call")
                .setSmallIcon(R.drawable.ic_launcher).setOngoing(true);

        Intent notificationIntent = new Intent(this, ChatRoomActivity.class);
        notificationIntent
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(ChatRoomActivity.ARG_ROOM_ID, mRoomName);
        PendingIntent intent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        mNotifyBuilder.setContentIntent(intent);

        //Creates a service which removes the notification after application is forced closed.
        if (mConnection == null) {
            mConnection = new ServiceConnection() {

                public void onServiceConnected(ComponentName className, IBinder binder) {
                    ((ClearBinder) binder).service.startService(
                            new Intent(ChatRoomActivity.this, ClearNotificationService.class));
                    NotificationManager mNotificationManager
                            = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    mNotificationManager.notify(ClearNotificationService.NOTIFICATION_ID,
                            mNotifyBuilder.build());
                }

                public void onServiceDisconnected(ComponentName className) {
                    mConnection = null;
                }

            };
        }
        if (!mIsBound) {
            bindService(new Intent(ChatRoomActivity.this,
                            ClearNotificationService.class), mConnection,
                    Context.BIND_AUTO_CREATE);
            mIsBound = true;
            startService(notificationIntent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        super.onResume();
        //Resume implies restore video mode if it was enable before pausing app

        //If service is binded remove it, so that the next time onPause can bind service.
        if (mIsBound) {
            unbindService(mConnection);
            stopService(new Intent(ClearNotificationService.MY_SERVICE));
            mIsBound = false;
        }

        if (mRoom != null) {
            mRoom.onResume();
        }

        mNotificationManager.cancel(ClearNotificationService.NOTIFICATION_ID);
        reloadInterface();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }

        if (this.isFinishing()) {
            mNotificationManager.cancel(ClearNotificationService.NOTIFICATION_ID);
            if (mRoom != null) {
                mRoom.disconnect();
            }
        }
    }

    @Override
    public void onDestroy() {
        mNotificationManager.cancel(ClearNotificationService.NOTIFICATION_ID);

        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }

        if (mRoom != null) {
            mRoom.disconnect();
        }

        super.onDestroy();
        finish();
    }

    @Override
    public void onBackPressed() {

        if (mRoom != null) {
            mRoom.disconnect();
        }

        super.onBackPressed();
    }

    public void reloadInterface() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (mRoom != null && mRoom.getParticipants().size() > 0) {
                    for(int i = 0; i< mRoom.getParticipants().size()-1; i++) {
                        mRoom.getParticipantsViewContainer()
                                .addView(mRoom.getParticipants().get(i).getView());
                    }
                    mRoom.getLastParticipantView().addView(mRoom.getLastParticipant().getView());
                }
            }
        }, 500);
    }

    private void initializeRoom() {
        Log.i(LOGTAG, "initializing chat room fragment for room: " + mRoomName);
        setTitle(mRoomName);

        //Show connecting dialog
        mConnectingDialog = new ProgressDialog(this);
        mConnectingDialog.setTitle("Joining Room...");
        mConnectingDialog.setMessage("Please wait.");
        mConnectingDialog.setCancelable(false);
        mConnectingDialog.setIndeterminate(true);
        mConnectingDialog.show();

        GetRoomDataTask task = new GetRoomDataTask();
        task.execute(mRoomName, mUsername);
    }

    private class GetRoomDataTask extends AsyncTask<String, Void, Room> {

        protected HttpClient mHttpClient;

        protected HttpGet mHttpGet;

        protected boolean mDidCompleteSuccessfully;

        public GetRoomDataTask() {
            mHttpClient = new DefaultHttpClient();
        }

        @Override
        protected Room doInBackground(String... params) {
            String sessionId = null;
            String token = null;
            String apiKey = null;
            initializeGetRequest(params[0]);
            try {
                HttpResponse roomResponse = mHttpClient.execute(mHttpGet);
                HttpEntity roomEntity = roomResponse.getEntity();
                String temp = EntityUtils.toString(roomEntity);
                Log.i(LOGTAG, "retrieved room response: " + temp);
                JSONObject roomJson = new JSONObject(temp);
                sessionId = roomJson.getString("sessionId");
                token = roomJson.getString("token");
                apiKey = roomJson.getString("apiKey");
                mDidCompleteSuccessfully = true;
            } catch (Exception exception) {
                Log.e(LOGTAG,
                        "could not get room data: " + exception.getMessage());
                mDidCompleteSuccessfully = false;
                return null;
            }

            try {
                OpenTokConfig.setAPIRootURL(BuildConfig.MEET_ENVIRONMENT, true);
                OpenTokConfig.setOTKitLogs(true);
                OpenTokConfig.setJNILogs(true);
                OpenTokConfig.setWebRTCLogs(true);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

            return new Room(ChatRoomActivity.this, params[0], sessionId, token,
                    apiKey, params[1]);
        }

        @Override
        protected void onPostExecute(final Room room) {
            if (mDidCompleteSuccessfully) {
                mConnectingDialog.dismiss();
                mRoom = room;
                mRoom.setPreviewView(mPreview);
                mRoom.setParticipantsViewContainer(mParticipantsView, mLastParticipantView, null);
                mRoom.setMessageView((TextView) findViewById(R.id.messageView),
                        (ScrollView) findViewById(R.id.scroller));
                mRoom.connect();
            } else {
                mConnectingDialog.dismiss();
                mConnectingDialog = null;
                showErrorDialog();
            }
        }

        protected void initializeGetRequest(String room) {
            URI roomURI;
            URL url;

            String urlStr = getResources().getString(R.string.serverURL) + room;
            try {
                url = new URL(urlStr);
                roomURI = new URI(url.getProtocol(), url.getUserInfo(),
                        url.getHost(), url.getPort(), url.getPath(),
                        url.getQuery(), url.getRef());
            } catch (URISyntaxException exception) {
                Log.e(LOGTAG,
                        "the room URI is malformed: " + exception.getMessage());
                return;
            } catch (MalformedURLException exception) {
                Log.e(LOGTAG,
                        "the room URI is malformed: " + exception.getMessage());
                return;
            }
            mHttpGet = new HttpGet(roomURI);
            mHttpGet.addHeader("Accept", "application/json, text/plain, */*");
        }
    }

    private DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
            finish();
        }
    };

    private void showErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.error_title);
        builder.setMessage(R.string.error);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", errorListener);
        mErrorDialog = builder.create();
        mErrorDialog.show();
    }

    public void onClickSend(View v) {
        if (mMessageEditText.getText().toString().compareTo("") == 0) {
            Log.d("Send Message", "Cannot Send - Empty String");
        } else {
            mRoom.sendChatMessage(mMessageEditText.getText().toString());
            mMessageEditText.setText("");
        }
    }

    public void onClickTextChat(View v) {
        if (mMessageBox.getVisibility() == View.GONE) {
            mMessageBox.setVisibility(View.VISIBLE);
        } else {
            mMessageBox.setVisibility(View.GONE);
        }
    }

    public void onClickShareLink(View v) {
        String roomUrl = serverURL + mRoomName;
        String text = getString(R.string.sharingLink) + " " + roomUrl;
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        startActivity(sendIntent);
    }

    public Room getRoom() {
        return mRoom;
    }

    public Handler getHandler() {
        return this.mHandler;
    }

    public void updateLoadingSub() {
        mRoom.loadSubscriberView();
    }

    //last participant video view
    public void disableVideo(View view){
        boolean enableAudioOnly = this.mRoom.getLastParticipant().getSubscribeToVideo();
        if (enableAudioOnly) {
            this.mRoom.getLastParticipant().setSubscribeToVideo(false);
        }
        else {
            this.mRoom.getLastParticipant().setSubscribeToVideo(true);
        }

        setAudioOnlyViewLastParticipant(enableAudioOnly, this.mRoom.getLastParticipant());
    }

    //Show audio only icon when video quality changed and it is disabled for the last subscriber
    public void setAudioOnlyViewLastParticipant(boolean audioOnlyEnabled, Participant participant) {
        boolean subscriberVideoOnly = audioOnlyEnabled;

        if (audioOnlyEnabled) {
            this.mRoom.getLastParticipantView().removeView(participant.getView());
            this.mRoom.getLastParticipantView().addView(getAudioOnlyIcon());

            //TODO add audiometer
        } else {
            if (!subscriberVideoOnly) {
                this.mRoom.getLastParticipantView().removeAllViews();
                this.mRoom.getLastParticipantView().addView(participant.getView());
            }
        }
    }

    public void setAudioOnlyViewListPartcipants (boolean audioOnlyEnabled, Participant participant, int index , View.OnClickListener clickListener) {

        Log.d(LOGTAG, "set AudioOnlyViewListPartcipants "+ audioOnlyEnabled + "INDEX: " + index);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                480, 320);

        if (audioOnlyEnabled) {
            this.mRoom.getParticipantsViewContainer().removeViewAt(index);
            View audioOnlyView = getAudioOnlyIcon();
            audioOnlyView.setOnClickListener(clickListener);
            audioOnlyView.setTag(index);
            this.mRoom.getParticipantsViewContainer().addView(audioOnlyView, index, lp);

        } else {
            this.mRoom.getParticipantsViewContainer().removeViewAt(index);
            this.mRoom.getParticipantsViewContainer().addView(participant.getView(), index, lp);

        }

    }

    public ImageView getAudioOnlyIcon() {

        ImageView imageView = new ImageView(this);
        //setting image resource
        imageView.setImageResource(R.drawable.avatar);
        //setting image position
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return imageView;
    }

    public ProgressBar getLoadingSub() {
        return mLoadingSub;
    }

    //Convert dp to real pixels, according to the screen density.
    public int dpToPx(int dp) {
        double screenDensity = this.getResources().getDisplayMetrics().density;
        return (int) (screenDensity * (double) dp);
    }
}