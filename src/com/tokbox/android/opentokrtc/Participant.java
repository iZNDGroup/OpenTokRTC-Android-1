package com.tokbox.android.opentokrtc;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;

public class Participant extends Subscriber {

	private static final String LOGTAG = "Participant";
	
	private String mUserId;
    private String mName;
    private Context mContext;
    protected Boolean mSubscriberVideoOnly = false;
    private ChatRoomActivity mActivity;
    
	public Participant(Context context, Stream stream) {
        super(context, stream);
    
        this.mContext = context;
        this.mActivity = (ChatRoomActivity) this.mContext;
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
	protected void onVideoDisabled() {
		super.onVideoDisabled();
		Log.i(LOGTAG, "Video quality changed. It is disabled for the subscriber");
		mSubscriberVideoOnly = true;
		mActivity.setAudioOnlyView(true);
	}
    
	@Override
	protected void onVideoDataReceived() {
		super.onVideoDataReceived();
		Log.i(LOGTAG, "First frame received");
		mActivity.updateLoadingSub();
	}

	@Override
	protected void onError(OpentokError error) {
		super.onError(error);
		showErrorDialog(error);
	}

	private void showErrorDialog(OpentokError error) {
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
				this.mContext);
 
		alertDialogBuilder.setTitle("OpenTokRTC Error");
		alertDialogBuilder
			.setMessage(error.getMessage())
			.setCancelable(false)
			.setPositiveButton("Yes",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						mActivity.finish();
					}
				  });
		AlertDialog alertDialog = alertDialogBuilder.create();
		
		alertDialog.show();
	}
	
}
