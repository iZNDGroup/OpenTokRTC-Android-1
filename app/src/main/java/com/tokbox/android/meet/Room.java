package com.tokbox.android.meet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.Connection;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.VideoUtils;

public class Room extends Session {

    private static final String LOGTAG = "Room";

    private Context mContext;

    private String apikey;
    private String sessionId;
    private String token;

    private Publisher mPublisher;
    private Participant mLastParticipant;
    private int mCurrentPosition;
    private String mPublisherName = null;
    private HashMap<Stream, Participant> mParticipantStream = new HashMap<Stream, Participant>();
    private HashMap<String, Participant> mParticipantConnection
            = new HashMap<String, Participant>();
    private ArrayList<Participant> mParticipants = new ArrayList<Participant>();

    private ViewGroup mPreview;
    private TextView mMessageView;
    private ScrollView mMessageScroll;
    private LinearLayout mParticipantsViewContainer;

    private ViewGroup mLastParticipantView;
    private OnClickListener onSubscriberUIClick;

    private Handler mHandler;

    private ChatRoomActivity mActivity;


    public Room(Context context, String roomName, String sessionId, String token, String apiKey,
            String username) {
        super(context, apiKey, sessionId);
        this.apikey = apiKey;
        this.sessionId = sessionId;
        this.token = token;
        this.mContext = context;
        this.mPublisherName = username;
        this.mHandler = new Handler(context.getMainLooper());
        this.mActivity = (ChatRoomActivity) this.mContext;


    }

    public void setParticipantsViewContainer(LinearLayout container, ViewGroup lastParticipantView,
            OnClickListener onSubscriberUIClick) {
        this.mParticipantsViewContainer = container;
        this.mLastParticipantView = lastParticipantView;
        this.onSubscriberUIClick = onSubscriberUIClick;
    }

    public void setMessageView(TextView et, ScrollView scroller) {
        this.mMessageView = et;
        this.mMessageScroll = scroller;
    }

    public void setPreviewView(ViewGroup preview) {
        this.mPreview = preview;
    }

    public void connect() {
        this.connect(token);
    }

    public void sendChatMessage(String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("name", mPublisherName);
            json.put("text", message);
            sendSignal("chat", json.toString());
            presentMessage("Me", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public Publisher getPublisher() {
        return mPublisher;
    }

    public Participant getLastParticipant() {
        return mLastParticipant;
    }

    public ArrayList<Participant> getParticipants() {
        return mParticipants;
    }

    public LinearLayout getParticipantsViewContainer() {
        return mParticipantsViewContainer;
    }

    public ViewGroup getLastParticipantView() {
        return mLastParticipantView;
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

    //Callbacks
    @Override
    public void onPause() {
        super.onPause();
        if (mPublisher != null) {
            mPreview.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.postDelayed(new Runnable() {

            @Override
            public void run() {
                if (mPublisher != null) {
                    mPreview.setVisibility(View.VISIBLE);
                    mPreview.removeView(mPublisher.getView());
                    RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                    mPreview.addView(mPublisher.getView(), lp);
                }
            }
        }, 500);
    }

    @Override
    protected void onConnected() {
        mPublisher = new Publisher(mContext, "Android");
        mPublisher.setName(mPublisherName);
        mPublisher.setPublisherListener(new PublisherKit.PublisherListener() {
            @Override
            public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
                Log.d(LOGTAG, "onStreamCreated!!");
            }

            @Override
            public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
                Log.d(LOGTAG, "onStreamDestroyed!!");
            }

            @Override
            public void onError(PublisherKit publisherKit, OpentokError opentokError) {
                Log.d(LOGTAG, "onError!!");
            }
        });
        publish(mPublisher);

        // Add video preview
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        SurfaceView v = (SurfaceView) mPublisher.getView();
        v.setZOrderOnTop(true);

        mPreview.addView(v, lp);
        mPublisher.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL);

        presentText((mActivity.getResources().getString(R.string.welcome_text_chat)));
        if (mPublisherName != null && !mPublisherName.isEmpty()) {
            sendChatMessage(
                    mActivity.getResources().getString((R.string.nick)) + " " + mPublisherName);
        }
    }

    @Override
    protected void onStreamReceived(Stream stream) {

        Participant p = new Participant(mContext, stream);

        //We can use connection data to obtain each user id
        p.setUserId(stream.getConnection().getData());

        if ( mParticipants.size() > 0 ) {
            final Participant lastParticipant = mParticipants.get(mParticipants.size() - 1);
            this.mLastParticipantView.removeView(lastParticipant.getView());

            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    480, 320);
            lastParticipant.setPreferredResolution(new VideoUtils.Size(320, 240)); //TODO set the view size as preferred resolution
            this.mParticipantsViewContainer.addView(lastParticipant.getView(), lp);
            lastParticipant.setSubscribeToVideo(true);
            lastParticipant.getView().setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean enableAudioOnly = lastParticipant.getSubscribeToVideo();

                    if (enableAudioOnly) {
                        lastParticipant.setSubscribeToVideo(false);
                    }
                    else {
                        lastParticipant.setSubscribeToVideo(true);
                    }
                    int index = mParticipantsViewContainer.indexOfChild(lastParticipant.getView());
                    if ( index == -1 ){
                        index = (int)v.getTag();
                    }
                    mActivity.setAudioOnlyViewListPartcipants(enableAudioOnly, lastParticipant, index, this);

                }
            });
        }
        mActivity.getLoadingSub().setVisibility(View.VISIBLE);
        p.setPreferredResolution(new VideoUtils.Size(640, 480));

        mLastParticipant = p;

        //Subscribe to this participant
        this.subscribe(p);

        mParticipants.add(p);
        mParticipantStream.put(stream, p);
        mParticipantConnection.put(stream.getConnection().getConnectionId(), p);

        presentText("\n" + p.getName() + " has joined the chat");

    }

    @Override
    protected void onStreamDropped(Stream stream) {
        Participant p = mParticipantStream.get(stream);
        if (p != null) {
            mParticipants.remove(p);
            mParticipantStream.remove(stream);
            mParticipantConnection.remove(stream.getConnection().getConnectionId());

            mLastParticipant = null;

            presentText("\n" + p.getName() + " has left the chat");
        }

    }

    @Override
    protected void onSignalReceived(String type, String data,
            Connection connection) {
        Log.d(LOGTAG, "Received signal:" + type + " data:" + data + "connection: " + connection);

        if (connection != null) {
            String mycid = this.getConnection().getConnectionId();
            String cid = connection.getConnectionId();
            if (!cid.equals(mycid)) {
                if ("chat".equals(type)) {
                    //Text message
                    Participant p = mParticipantConnection.get(cid);
                    if (p != null) {
                        JSONObject json;
                        try {
                            json = new JSONObject(data);
                            String text = json.getString("text");
                            String name = json.getString("name");
                            p.setName(name);
                            presentMessage(p.getName(), text);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                } else if ("name".equals(type)) {
                    //Name change message
                    Participant p = mParticipantConnection.get(cid);
                    if (p != null) {
                        try {
                            String oldName = p.getName();
                            JSONArray jsonArray = new JSONArray(data);
                            String name = jsonArray.getString(1);
                            p.setName(name);
                            presentText("\n" + oldName + " is now known as " + name);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } else if ("initialize".equals(type)) {
            // Initialize message
            try {
                JSONObject json = new JSONObject(data);
                JSONObject users = json.getJSONObject("users");
                Iterator<?> it = users.keys();
                while (it.hasNext()) {
                    String pcid = (String) it.next();
                    Participant p = mParticipantConnection.get(pcid);
                    if (p != null) {
                        p.setName(users.getString(pcid));
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onArchiveStarted(String id, String name) {
        super.onArchiveStarted(id, name);

    }

    @Override
    protected void onArchiveStopped(String id) {
        super.onArchiveStopped(id);

    }

    @Override
    protected void onError(OpentokError error) {
        super.onError(error);
        Toast.makeText(this.mContext, error.getMessage(), Toast.LENGTH_SHORT).show();
    }

    public void loadSubscriberView() {
        //stop loading spinning
        if (mActivity.getLoadingSub().getVisibility() == View.VISIBLE) {
            mActivity.getLoadingSub().setVisibility(View.GONE);

            this.mLastParticipantView.addView(mLastParticipant.getView());
        }

    }


}