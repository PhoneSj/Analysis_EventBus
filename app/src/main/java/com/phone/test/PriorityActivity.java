package com.phone.test;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.greenrobot.eventbus.EventBus;

/**
 * Created by Phone on 2017/6/29.
 * <p>
 * 测试触发响应的优先级：这里MyTextView优先级设置为1，MyTextView2优先级设置为2，并且MyTextView处理后禁止事件再传递
 * </p>
 */

public class PriorityActivity extends Activity {

	private EditText editText;
	private Button button;
	private MyTextView textView0;
	private MyTextView2 textView1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_priority);
		initView();
	}

	@Override
	protected void onStart() {
		super.onStart();
		EventBus.getDefault().register(textView0);
		EventBus.getDefault().register(textView1);
	}

	@Override
	protected void onStop() {
		super.onStop();
		EventBus.getDefault().unregister(textView0);
		EventBus.getDefault().unregister(textView1);
	}

	private void initView() {
		editText = (EditText) findViewById(R.id.editText);
		button = (Button) findViewById(R.id.button);
		textView0 = (MyTextView) findViewById(R.id.textView0);
		textView1 = (MyTextView2) findViewById(R.id.textView1);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String data = editText.getText().toString();
				EventBus.getDefault().post(data);
			}
		});
	}

}
