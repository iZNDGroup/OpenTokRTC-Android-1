package com.tokbox.android.opentokrtc;

import android.content.Context;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;

public class Participant extends Subscriber {

	private String mUserId;
    private String mName;
    private Context mContext;
    protected Boolean mSubscriberVideoOnly = false;
  
	public Participant(Context context, Stream stream) {
        super(context, stream, null);
        // With the userId we can query our own database
        // to extract player information
        // this.name = "Guest-" + (this.myConnectionId.substring(this.myConnectionId.length - 8, this.myConnectionId.length));
        setmName("User" + ((int)(Math.random()*1000)));
        this.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
    }

    public String getmUserId() {
        return mUserId;
    }

    public void setmUserId(String name) {
        this.mUserId = name;
    }

    public String getmName() {
        return mName;
    }

    public void setmName(String name) {
        this.mName = name;
    }

    public Boolean getmSubscriberVideoOnly() {
		return mSubscriberVideoOnly;
	}

    @Override
	protected void onVideoDisabled(SubscriberKit subscriber) {
		super.onVideoDisabled(subscriber);
		ChatRoomActivity mActivity = (ChatRoomActivity) this.mContext;
		mSubscriberVideoOnly = true;
		mActivity.setAudioOnlyView(true);
	}


}
