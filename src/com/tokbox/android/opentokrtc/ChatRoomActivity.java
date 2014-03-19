package com.tokbox.android.opentokrtc;

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

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

import com.opentok.android.OpenTokConfig;

public class ChatRoomActivity extends Activity implements
		SubscriberControlFragment.SubscriberCallbacks,
		PublisherControlFragment.PublisherCallbacks {

	private static final int NOTIFICATION_ID = 1;
    public static final String LOGTAG = "ChatRoomActivity";
	public static final String ARG_ROOM_ID = "roomId";
	private String mRoomName;
	private ProgressDialog mConnectingDialog;
	private EditText mMessageEditText;
	private ViewGroup mPreview;
	private ViewPager mPlayersView;
	protected Room mRoom;
	private boolean mSubscriberVideoOnly = false;

	private RelativeLayout mMessageBox;
	private RelativeLayout fragmentPubContainer;
	private RelativeLayout fragmentSubContainer;
	
	// Fragments
	private SubscriberControlFragment mSubscriberFragment;
	private PublisherControlFragment mPublisherFragment;

	protected Handler mHandler = new Handler();

	private NotificationCompat.Builder mNotifyBuilder;
	NotificationManager mNotificationManager;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Show the Up button in the action bar.
		// getActionBar().setDisplayHomeAsUpEnabled(true);

		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// Stop screen from going to sleep
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);

		setContentView(R.layout.room_layout);

		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
				R.layout.custom_title);

		mMessageBox = (RelativeLayout) findViewById(R.id.messagebox);

		Uri url = getIntent().getData();
        if(url == null) {
            mRoomName = getIntent().getStringExtra(ARG_ROOM_ID);
        } else {
            mRoomName = url.getPathSegments().get(0);
        }

		mMessageEditText = (EditText) findViewById(R.id.message);

		mPreview = (ViewGroup) findViewById(R.id.publisherview);

		mPlayersView = (ViewPager) findViewById(R.id.pager);

		fragmentPubContainer = (RelativeLayout) findViewById(R.id.fragment_pub_container);
		fragmentSubContainer  = (RelativeLayout) findViewById(R.id.fragment_sub_container);
		
		if (savedInstanceState == null) {
			initSubscriberFragment();
			initPublisherFragment();
		}

        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        //enable OTKit logs
        OpenTokConfig.setOTKitLogs(true);
        //enable bindings logs
        OpenTokConfig.setJNILogs(true);
		
		initializeRoom();

	}

	@Override
	public void onPause() {
		super.onPause();

		if (mRoom != null) {
			mRoom.onPause();
		}
		mNotifyBuilder = new NotificationCompat.Builder(this)
        .setContentTitle("OpenTokRTC")
        .setContentText("Ongoing call")
        .setSmallIcon(R.drawable.ic_launcher).setOngoing(true);
        
		Intent notificationIntent = new Intent(this, ChatRoomActivity.class);
	    notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    notificationIntent.putExtra(ChatRoomActivity.ARG_ROOM_ID, mRoomName);
	    PendingIntent intent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
	    
	    mNotifyBuilder.setContentIntent(intent);
		
        mNotificationManager.notify(
                NOTIFICATION_ID,
                mNotifyBuilder.build());
	}

	@Override
	public void onResume() {
		super.onResume();

		if (mRoom != null) {
			mRoom.onResume();
		}

		mNotificationManager.cancel(NOTIFICATION_ID);

        if (mRoom != null) {
        	mRoom.onResume();
        }

	}

	@Override
	public void onStop() {
		super.onStop();

		if(this.isFinishing()) {
	        mNotificationManager.cancel(NOTIFICATION_ID);
	        
            if (mRoom != null) {
            	mRoom.disconnect();
            }
        }
	}

	private void initializeRoom() {
		Log.i(LOGTAG, "initializing chat room fragment for room: " + mRoomName);
		setTitle(mRoomName);

		// show connecting dialog
		mConnectingDialog = new ProgressDialog(this);
		mConnectingDialog.setTitle("Joining Room...");
		mConnectingDialog.setMessage("Please wait.");
		mConnectingDialog.setCancelable(false);
		mConnectingDialog.setIndeterminate(true);
		mConnectingDialog.show();

		GetRoomDataTask task = new GetRoomDataTask();
		task.execute(mRoomName);
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
				sessionId = roomJson.getString("sid");
				token = roomJson.getString("token");
				apiKey = roomJson.getString("apiKey");
				mDidCompleteSuccessfully = true;
			} catch (Exception exception) {
				Log.e(LOGTAG,
						"could not get room data: " + exception.getMessage());
				mDidCompleteSuccessfully = false;
				return null;
			}
			return new Room(ChatRoomActivity.this, params[0], sessionId, token,
					apiKey);
		}

		@Override
		protected void onPostExecute(final Room room) {
			// TODO: it might be better to set up some kind of callback
			// interface
			if (mDidCompleteSuccessfully) {
				mConnectingDialog.dismiss();
				mRoom = room;
				mPreview.setOnClickListener(onPublisherUIClick);
				mRoom.setPreviewView(mPreview);
				mRoom.setPlayersViewContainer(mPlayersView, onSubscriberUIClick);
				mRoom.setMessageView((TextView) findViewById(R.id.messageView),
						(ScrollView) findViewById(R.id.scroller));
				mRoom.connect();
			} else {
				mConnectingDialog.dismiss();
				mConnectingDialog = null;
				// TODO: show failure dialog
			}
		}

		protected void initializeGetRequest(String room) {
			URI roomURI;
			URL url;
			// TODO: construct urlStr from injectable values for testing
			String urlStr = "https://opentokrtc.com/" + room + ".json";
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
			// TODO: check if alternate constructor will escape invalid
			// characters properly, might be able to avoid all above code in
			// this method
			mHttpGet = new HttpGet(roomURI);
		}
	}

	public void onClickSend(View v) {
		mRoom.sendChatMessage(mMessageEditText.getText().toString());
		mMessageEditText.setText("");
	}

	public void onClickTextChat(View v) {

		if (mMessageBox.getVisibility() == View.GONE) {
			mMessageBox.setVisibility(View.VISIBLE);
		} else {
			mMessageBox.setVisibility(View.GONE);
		}
	}

	public void onPublisherViewClick(View v) {
		if (mRoom != null && mRoom.getmCurrentParticipant() != null) {
			mRoom.getmCurrentParticipant().getView()
					.setOnClickListener(onPublisherUIClick);
		}
	}

	public void loadInterface() {

		// Surfaceview ordering hack for 2.3 devices
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (mRoom != null && mRoom.getmCurrentParticipant() != null) {
					mSubscriberFragment.initSubscriberWidget();
				}
			}
		}, 0);

		// Show/hide UI controls
		if (mSubscriberVideoOnly) {
			if (mRoom.getmCurrentParticipant() != null) {
				mRoom.getmCurrentParticipant().getView()
						.setVisibility(View.GONE);
			}
			// findViewById(R.id.audioOnly1).setVisibility(View.VISIBLE);
			// findViewById(R.id.audioOnly2).setVisibility(View.VISIBLE);
		}
	}

	public void initPublisherFragment() {
		mPublisherFragment = new PublisherControlFragment();
		getFragmentManager().beginTransaction()
				.add(R.id.fragment_pub_container, mPublisherFragment)
				.commit();
	}

	public void initSubscriberFragment() {
		mSubscriberFragment = new SubscriberControlFragment();
		getFragmentManager().beginTransaction()
				.add(R.id.fragment_sub_container, mSubscriberFragment).commit();
	}

	public Room getmRoom() {
		return mRoom;
	}

	public void setmHandler(Handler mHandler) {
		this.mHandler = mHandler;
	}

	public Handler getmHandler() {
		return this.mHandler;
	}

	@Override
	public void onMuteSubscriber() {
		if (mRoom.getmCurrentParticipant() != null) {
			mRoom.getmCurrentParticipant().setSubscribeToAudio(
					!mRoom.getmCurrentParticipant().getSubscribeToAudio());
		}
	}

	@Override
	public void onMutePublisher() {
		if (mRoom.getmPublisher() != null) {
			mRoom.getmPublisher().setPublishAudio(
					!mRoom.getmPublisher().getPublishAudio());
		}
	}

	@Override
	public void onSwapCamera() {
		if (mRoom.getmPublisher() != null) {
			mRoom.getmPublisher().swapCamera();
		}
	}

	@Override
	public void onEndCall() {
		if (mRoom != null) {
			mRoom.disconnect();
		}
		finish();
	}

	private OnClickListener onSubscriberUIClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Log.i(LOGTAG, "onClick subscriber UI");
			mSubscriberFragment.subscriberClick();
			showSubFragment();
            mPublisherFragment.publisherClick();
            showPubFragment();
		}
	};

	private OnClickListener onPublisherUIClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Log.i(LOGTAG, "onClick publisher UI");
            mSubscriberFragment.subscriberClick();
            showSubFragment();
			mPublisherFragment.publisherClick();
			showPubFragment();
		}
	};
	
	@Override
	public void onStatusPubBar() {
		showPubFragment();
	}
	@Override
	public void onStatusSubBar() {
		showSubFragment();	
	}
	
	public void showPubFragment(){
		
		if (fragmentPubContainer.getVisibility() == View.GONE) {
			Log.i(LOGTAG, "onClick VISIBLE");
			RelativeLayout.LayoutParams params = (LayoutParams) mPreview
					.getLayoutParams();
			params.bottomMargin = dpToPx(58);;
			mPreview.setLayoutParams(params);
			fragmentPubContainer.setVisibility(View.VISIBLE);
		} else {
			RelativeLayout.LayoutParams params = (LayoutParams) mPreview
					.getLayoutParams();
			params.addRule(RelativeLayout.ALIGN_BOTTOM);
			params.bottomMargin = dpToPx(10);
			mPreview.setLayoutParams(params);
			fragmentPubContainer.setVisibility(View.GONE);

		}
	}
	
	public void showSubFragment(){
		
		if (fragmentSubContainer.getVisibility() == View.GONE) {
			fragmentSubContainer.setVisibility(View.VISIBLE);
		} else {
			fragmentSubContainer.setVisibility(View.GONE);
		}
	}
	
	/**
     * Converts dp to real pixels, according to the screen density.
     * @param dp A number of density-independent pixels.
     * @return The equivalent number of real pixels.
     */
    private int dpToPx(int dp) {
        double screenDensity = this.getResources().getDisplayMetrics().density;
        return (int) (screenDensity * (double) dp);
    }

}