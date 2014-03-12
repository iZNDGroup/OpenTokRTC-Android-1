package com.tokbox.android.opentokrtc;

import java.util.ArrayList;
import java.util.HashMap;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.Connection;
import com.opentok.android.Publisher;
import com.opentok.android.Session;
import com.opentok.android.Stream;

import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

public class Room extends Session {

	Context mContext;
	String apikey;
	String sessionId;
	String token;
	
	// Interface
	ViewPager mPlayersViewCotainer;
	ViewGroup mPreview;
	TextView mMessageView;
	ScrollView mMessageScroll;

	// Players status
	ArrayList<Participant> mPlayers = new ArrayList<Participant>();
	HashMap<Stream, Participant> mPlayerStream = new HashMap<Stream, Participant>();
	HashMap<String, Participant> mPlayerConnection = new HashMap<String, Participant>();

	PagerAdapter mPagerAdapter = new PagerAdapter() {

		@Override
		public boolean isViewFromObject(View arg0, Object arg1) {
			return ((Participant) arg1).getView() == arg0;
		}

		@Override
		public int getCount() {
			return mPlayers.size();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			if (position < mPlayers.size()) {
				return mPlayers.get(position).getName();
			} else {
				return null;
			}
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			Participant p = mPlayers.get(position);
			container.addView(p.getView());
			return p;
		}

		@Override
		public void setPrimaryItem(ViewGroup container, int position,
				Object object) {
			for (Participant p : mPlayers) {
				if (p == object) {
					if (!p.getSubscribeToVideo()) {
						p.setSubscribeToVideo(true);
					}
				} else {
					if (p.getSubscribeToVideo()) {
						p.setSubscribeToVideo(false);
					}
				}
			}
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			Participant p = (Participant) object;
			container.removeView(p.getView());
		}

		@Override
		public int getItemPosition(Object object) {
			for (int i = 0; i < mPlayers.size(); i++) {
				if (mPlayers.get(i) == object) {
					return i;
				}
			}
			return POSITION_NONE;
		}

	};

	public Room(Context context, String roomName, String sessionId, String token, String apiKey) {
		super(context, sessionId, null);
		this.apikey = apiKey;
		this.sessionId = sessionId;
		this.token = token;
		this.mContext = context;
	}

	// public methods
	public void setPlayersViewContainer(ViewPager container) {
		this.mPlayersViewCotainer = container;
		this.mPlayersViewCotainer.setAdapter(mPagerAdapter);
		mPagerAdapter.notifyDataSetChanged();
	}

	public void setMessageView(TextView et, ScrollView scroller) {
		this.mMessageView = et;
		this.mMessageScroll = scroller;
	}

	public void setPreviewView(ViewGroup preview) {
		this.mPreview = preview;
	}

	public void connect() {
		this.connect(apikey, token);
	}

	public void sendChatMessage(String message) {
		sendSignal("chat", message);
		presentMessage("Me", message);
	}

	// callbacks
	@Override
	protected void onConnected(Session session) {
		Publisher p = new Publisher(mContext, null, "MyPlayer");
		publish(p);

		// Add video preview
		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		mPreview.addView(p.getView(), lp);
		p.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
				BaseVideoRenderer.STYLE_VIDEO_FILL);

		presentText("Welcome to OpenTok Chat.");
	}

	@Override
	protected void onReceivedStream(Session session, Stream stream) {
		Participant p = new Participant(mContext, stream);
	
		// we can use connection data to obtain each user id
		p.setUserId(stream.getConnection().getData());

		// Subscribe audio only if we have more than one player
		if (mPlayers.size() != 0) {
			p.setSubscribeToVideo(false);
		}

		// Subscribe to this player
		this.subscribe(p);

		mPlayers.add(p);
		mPlayerStream.put(stream, p);
		mPlayerConnection.put(stream.getConnection().getConnectionId(), p);
		mPagerAdapter.notifyDataSetChanged();

		presentText("\n" + p.getName() + " has joined the chat");
	}

	@Override
	protected void onDroppedStream(Session session, Stream stream) {
		Participant p = mPlayerStream.get(stream);
		if (p != null) {
			mPlayers.remove(p);
			mPlayerStream.remove(stream);
			mPlayerConnection.remove(stream.getConnection().getConnectionId());
			mPagerAdapter.notifyDataSetChanged();

			presentText("\n" + p.getName() + " has left the chat");
		}
	}

	@Override
	protected void onSignal(Session session, String type, String data,
			Connection connection) {
        String mycid = this.getConnection().getConnectionId();
        String cid = connection.getConnectionId();
        if (!cid.equals(mycid)) {
            if ("chat".equals(type)) {
                Participant p = mPlayerConnection.get(cid);
                if (p != null) {
                    presentMessage(p.getName(), data);
                }
            }
        }
	}

	private void presentMessage(String who, String message) {
		presentText("\n" + who + ": " + message);
	}

	private void presentText(String message) {
		mMessageView.setText(mMessageView.getText() + message);
		mMessageScroll.post(new Runnable() {
			@Override
			public void run() {
				int totalHeight = mMessageView.getHeight();
				mMessageScroll.smoothScrollTo(0, totalHeight);
			}
		});
	}
}