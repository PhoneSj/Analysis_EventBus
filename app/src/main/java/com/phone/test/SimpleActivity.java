package com.phone.test;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class SimpleActivity extends Activity {

	private EditText editText;
	private Button button;
	private TextView textView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_simple);
		initView();
	}

	@Override
	protected void onStart() {
		super.onStart();
		EventBus.getDefault().register(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		EventBus.getDefault().unregister(this);
	}

	private void initView() {
		editText = (EditText) findViewById(R.id.editText);
		button = (Button) findViewById(R.id.button);
		textView = (TextView) findViewById(R.id.textView);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String data = editText.getText().toString();
				EventBus.getDefault().post(data);
			}
		});
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onResult(String data) {
		textView.setText(data);
	}

}
