package com.tokbox.android.opentokrtc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

public class OpenTokRTC extends Activity
{
	private static final String TAG = "opentokrtc";
	  
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main_layout); 
    }

    public void joinRoom(View v) {
        Log.i(TAG, "join room button clicked.");

        EditText roomNameInput = (EditText) findViewById(R.id.input_room_name);
        String roomName = roomNameInput.getText().toString();

        Intent enterChatRoomIntent = new Intent(this, ChatRoomActivity.class);
        enterChatRoomIntent.putExtra(ChatRoomActivity.ARG_ROOM_ID, roomName);
        startActivity(enterChatRoomIntent);
    }

}
