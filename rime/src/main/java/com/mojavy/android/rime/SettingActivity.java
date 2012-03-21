package com.mojavy.android.pima;

import java.io.IOException;

import com.mojavy.android.pima.util.PimaUtil;
import com.mojavy.android.pima.util.Configuration;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class SettingActivity extends Activity {
	
	TextView mTextView;
	Configuration mConfiguration = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);
	
		mTextView = (TextView)findViewById(R.id.settingContents);

		try {
			mConfiguration = new Configuration(this, false);
		} catch (IOException e) {
			PimaUtil.error("failed to open configuration");
			throw new RuntimeException(e);
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		String s = mConfiguration.getConfigurationContents();
		PimaUtil.info("settings: %s", s);
		if (mTextView != null)
			mTextView.setText(s);		
	}

	@Override
	protected void onPause() {
		super.onPause();

		try {
			mConfiguration.saveConfiguration(mTextView.getText().toString());
		} catch (IOException e) {
			PimaUtil.error("failed to save conf: %s", e.toString());
		}
	}

}
