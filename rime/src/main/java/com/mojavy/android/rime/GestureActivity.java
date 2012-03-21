package com.mojavy.android.pima;

import com.mojavy.android.pima.util.PimaUtil;

import android.app.Activity;
import android.gesture.Gesture;
import android.gesture.GestureOverlayView;
import android.gesture.GestureOverlayView.OnGesturePerformedListener;
import android.os.Bundle;

public class GestureActivity extends Activity {
	
	GestureOverlayView mGestureOverlay;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mGestureOverlay = (GestureOverlayView) findViewById(R.id.gestures);
		mGestureOverlay.addOnGesturePerformedListener(new OnGesturePerformedListener() {
			@Override
			public void onGesturePerformed(GestureOverlayView overlay, Gesture gesture) {
				PimaUtil.info("gesture performed");
			}
		});
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

}
