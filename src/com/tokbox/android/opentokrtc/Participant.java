package com.tokbox.android.opentokrtc;

import android.content.Context;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;

public class Participant extends Subscriber {

	private String userId;
    private String name;
        
    public Participant(Context context, Stream stream) {
        super(context, stream);
        // With the userId we can query our own database
        // to extract player information
        setName("User" + ((int)(Math.random()*1000)));
        this.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String name) {
        this.userId = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    @Override
	protected void onVideoDisabled(SubscriberKit subscriber) {
		super.onVideoDisabled(subscriber);
		this.setSubscribeToVideo(true);
	}


}
