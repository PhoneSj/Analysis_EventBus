package com.phone.test;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Created by Phone on 2017/6/29.
 * <p>
 * 测试粘性事件：在将粘性事件发送到总线之后注册的对象，也能响应
 * </p>
 * <p>
 * 两个条件：发送粘性事件postSticky(event);响应方法需要在注解中表明sticky=true
 * </p>
 */

public class StickyActivity extends Activity {

	private EditText editText;
	private Button button;
	private TextView textView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_simple);
		initView();
	}

	private void initView() {
		editText = (EditText) findViewById(R.id.editText);
		button = (Button) findViewById(R.id.button);
		textView = (TextView) findViewById(R.id.textView);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				textView.setText("waiting 2 second...");
				String data = editText.getText().toString();
				EventBus.getDefault().postSticky(data);
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						EventBus.getDefault().register(this);
					}

					@Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
					public void onResult(String data) {
						textView.setText(textView.getText() + "\n" + data);
						EventBus.getDefault().unregister(this);
					}
				}, 2000);
			}
		});
	}
}
