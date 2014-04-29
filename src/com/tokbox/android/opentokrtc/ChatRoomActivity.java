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

import android.app.ActionBar;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tokbox.android.opentokrtc.fragments.PublisherControlFragment;
import com.tokbox.android.opentokrtc.fragments.PublisherStatusFragment;
import com.tokbox.android.opentokrtc.fragments.SubscriberControlFragment;

public class ChatRoomActivity extends Activity implements
		SubscriberControlFragment.SubscriberCallbacks,
		PublisherControlFragment.PublisherCallbacks {

	private static final int NOTIFICATION_ID = 1;
	public static final String LOGTAG = "ChatRoomActivity";
	public static final String ARG_ROOM_ID = "roomId";
	public static final String ARG_USERNAME_ID = "usernameId";
	private static final int ANIMATION_DURATION = 500;   
	private static final String URL = "http://opentokrtc.com/";
	
	private String mRoomName;
	protected Room mRoom;
	private String mUsername = null;
	private boolean mSubscriberVideoOnly = false;
	private boolean mArchiving = false;
	
	private ProgressDialog mConnectingDialog;
	private EditText mMessageEditText;
	private ViewGroup mPreview;
	private ViewPager mPlayersView;	
	private ImageView mLeftArrowImage;
	private ImageView mRightArrowImage;	
	
	private RelativeLayout mSubscriberAudioOnlyView;
	private RelativeLayout mMessageBox;
	
	// Fragments
	protected SubscriberControlFragment mSubscriberFragment;
	protected PublisherControlFragment mPublisherFragment;
	protected PublisherStatusFragment mPublisherStatusFragment;
	
	protected Handler mHandler = new Handler();

	private NotificationCompat.Builder mNotifyBuilder;
	NotificationManager mNotificationManager;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.room_layout);
		
		//set custom title bar
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

		mPlayersView = (ViewPager) findViewById(R.id.pager);
		mLeftArrowImage = (ImageView) findViewById(R.id.left_arrow);
		mRightArrowImage = (ImageView) findViewById(R.id.right_arrow);
		
		mSubscriberAudioOnlyView = (RelativeLayout) findViewById(R.id.audioOnlyView);

		Uri url = getIntent().getData();
        if(url == null) {
            mRoomName = getIntent().getStringExtra(ARG_ROOM_ID);
            mUsername = getIntent().getStringExtra(ARG_USERNAME_ID);
        } else {
            mRoomName = url.getPathSegments().get(0);
        }
		
        TextView title = (TextView) findViewById(R.id.title);
        title.setText(mRoomName);
	
		if (savedInstanceState == null) {
			initSubscriberFragment();
			initPublisherFragment();
			initPublisherStatusFragment();
		}
	      
        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      	initializeRoom();
		
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		// Remove publisher & subscriber views because we want to reuse them
		if (mRoom != null && mRoom.getmCurrentParticipant() != null) {
			mRoom.getmParticipantsViewContainer().removeView(mRoom.getmCurrentParticipant().getView());
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

		if (mRoom != null) {
			mRoom.onPause();
			
			if (mRoom != null && mRoom.getmCurrentParticipant() != null) {
				mRoom.getmParticipantsViewContainer().removeView(mRoom.getmCurrentParticipant().getView());
			}
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
        
        reloadInterface();
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
				if (mRoom != null && mRoom.getmCurrentParticipant() != null) {
					mRoom.getmParticipantsViewContainer().addView(mRoom.getmCurrentParticipant().getView());
				}
			}
		}, 500);
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
					apiKey, params [1]);
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

	public void onClickShareLink(View v) {	
		String roomUrl = URL + mRoomName;
		String text = getString(R.string.sharingLink) + " " + roomUrl;
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		sendIntent.putExtra(Intent.EXTRA_TEXT, text);
		sendIntent.setType("text/plain");
		startActivity(sendIntent);
	}
	
	public void onPublisherViewClick(View v) {
		if (mRoom != null && mRoom.getmCurrentParticipant() != null) {
			mRoom.getmCurrentParticipant().getView()
					.setOnClickListener(onPublisherUIClick);
		}
	}
	
	public void initPublisherFragment() {
		mPublisherFragment = new PublisherControlFragment();
		getFragmentManager().beginTransaction()
				.add(R.id.fragment_pub_container, mPublisherFragment)
				.commit();	
	}
	
	public void initPublisherStatusFragment() {
		mPublisherStatusFragment = new PublisherStatusFragment();
		getFragmentManager().beginTransaction()
				.add(R.id.fragment_pub_status_container, mPublisherStatusFragment)
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
			mSubscriberFragment.subscriberClick();
			showArrowsOnSubscriber();
			
			if (mRoom.getmPublisher() != null) {
				mPublisherFragment.publisherClick();
				if (mArchiving) {
					mPublisherStatusFragment.publisherClick();	
				}
				setPublisherMargins();
			}
		}
	};

	private OnClickListener onPublisherUIClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if	(mRoom.getmCurrentParticipant()!= null) {
				mSubscriberFragment.subscriberClick();
				showArrowsOnSubscriber();
            }
            if (mRoom.getmPublisher() != null) {
            	mPublisherFragment.publisherClick();
            	if (mArchiving) {
            		mPublisherStatusFragment.publisherClick();
            	}
            	setPublisherMargins();
            }
        }
	};
	
	@Override
	public void onStatusPubBar() {
		setPublisherMargins();
	}
	
	@Override
	public void onStatusSubBar() {
		showArrowsOnSubscriber();	
	}
	
	public void setPublisherMargins(){
		int bottomMargin = 0;
		RelativeLayout.LayoutParams params = (LayoutParams) mPreview
				.getLayoutParams();
		RelativeLayout.LayoutParams pubControlLayoutParams = (LayoutParams) mPublisherFragment
				.getmPublisherContainer().getLayoutParams();
		RelativeLayout.LayoutParams pubStatusLayoutParams = (LayoutParams) mPublisherStatusFragment
				.getMPubStatusContainer().getLayoutParams();

		if (mPublisherFragment.ismPublisherWidgetVisible() && mArchiving) {
			bottomMargin = pubControlLayoutParams.height
					+ pubStatusLayoutParams.height + dpToPx(20);
		}
		else {
			if (mPublisherFragment.ismPublisherWidgetVisible()) {
				bottomMargin = pubControlLayoutParams.height + dpToPx(20);
			} else {	
				params.addRule(RelativeLayout.ALIGN_BOTTOM);
				bottomMargin = dpToPx(20);
			}
		}
		params.bottomMargin = bottomMargin;
		params.leftMargin = dpToPx(20);
		mPreview.setLayoutParams(params);
	}
	
	public void showArrowsOnSubscriber(){
		
		boolean show = false;
		if (mRoom.getmParticipants().size() > 1 ) {
	        	if (mLeftArrowImage.getVisibility() == View.GONE) {
	        		show = true;
	        	}
	        	else {
	        		show = false;
	        	}
	        	mLeftArrowImage.clearAnimation();
	    		mRightArrowImage.clearAnimation();
	    		float dest = show ? 1.0f : 0.0f;
	    		AlphaAnimation aa = new AlphaAnimation(1.0f - dest, dest);
	    		aa.setDuration(ANIMATION_DURATION);
	    		aa.setFillAfter(true);
	    		mLeftArrowImage.startAnimation(aa);
	    		mRightArrowImage.startAnimation(aa);	    		
		}
		
		//show subscriber views arrows
        if (show) {
        	mLeftArrowImage.setVisibility(View.VISIBLE);
        	mRightArrowImage.setVisibility(View.VISIBLE);
        }
        else {
        	mLeftArrowImage.setVisibility(View.GONE);
        	mRightArrowImage.setVisibility(View.GONE);
        }
	}
	
	public void nextParticipant(View view) {
		int nextPosition = mRoom.getmCurrentPosition() +1;
		mPlayersView.setCurrentItem(nextPosition);
	}
	
	public void lastParticipant(View view) {
		int nextPosition = mRoom.getmCurrentPosition() -1;
		mPlayersView.setCurrentItem(nextPosition);
	}
	
	public PublisherControlFragment getmPublisherFragment() {
		return mPublisherFragment;
	}
	
	public void setAudioOnlyView(boolean audioOnlyEnabled) {
		mSubscriberVideoOnly = audioOnlyEnabled;

		if (audioOnlyEnabled) {
			mRoom.getmCurrentParticipant().getView().setVisibility(View.GONE);
			mSubscriberAudioOnlyView.setVisibility(View.VISIBLE);
			mSubscriberAudioOnlyView.setOnClickListener(onSubscriberUIClick);

			// Audio only text for subscriber
			TextView subStatusText = (TextView) findViewById(R.id.subscriberName);
			subStatusText.setText(R.string.audioOnly);
			AlphaAnimation aa = new AlphaAnimation(1.0f, 0.0f);
			aa.setDuration(ANIMATION_DURATION);
			subStatusText.startAnimation(aa);
			
		} else {
			if (!mSubscriberVideoOnly) {
				mRoom.getmCurrentParticipant().getView().setVisibility(View.VISIBLE);
				mSubscriberAudioOnlyView.setVisibility(View.GONE);
			}
		}
	}
	
	public void updateArchivingStatus(boolean archiving) {
		mPublisherFragment.showPublisherWidget(false);
		mArchiving = archiving;
		
		if (archiving) {
			mPublisherStatusFragment.updateArchivingUI(true);
			mPublisherFragment.showPublisherWidget(true);
			mPublisherFragment.initPublisherUI();
			setPublisherMargins();

			if (mRoom.getmCurrentParticipant() != null) {
				mSubscriberFragment.showSubscriberWidget(true);
			}
		}
		else {
			mPublisherStatusFragment.updateArchivingUI(false);
			setPublisherMargins();
		}
	}
	
	/**
     * Converts dp to real pixels, according to the screen density.
     * @param dp A number of density-independent pixels.
     * @return The equivalent number of real pixels.
     */
    public int dpToPx(int dp) {
        double screenDensity = this.getResources().getDisplayMetrics().density;
        return (int) (screenDensity * (double) dp);
    }
}