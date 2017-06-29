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

public class CustomEventActivity extends Activity {

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
				MyData data = new MyData();
				data.name = editText.getText().toString();
				data.age = 27;
				data.gender = "male";
				EventBus.getDefault().post(data);
			}
		});
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onResult(MyData data) {
		StringBuilder sb = new StringBuilder();
		sb.append(data.name + "\n");
		sb.append(data.age + "\n");
		sb.append(data.gender + "\n");
		textView.setText(sb.toString());
	}

	class MyData {
		String name;
		int age;
		String gender;
	}
}
