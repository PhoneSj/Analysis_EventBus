package com.phone.test;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Created by Phone on 2017/6/29.
 * 测试响应方法的执行线程
 */

public class ThreadActivity extends Activity {

	private EditText editText;
	private Button postInMain;
	private Button postInSub;
	private TextView mainTextView;
	private TextView postingTextView;
	private TextView backgroundTextView;
	private TextView asyncTextView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_thread);
		initView();
	}

	private void initView() {
		editText = (EditText) findViewById(R.id.editText);
		postInMain = (Button) findViewById(R.id.button_main);
		postInSub = (Button) findViewById(R.id.button_sub);
		postInMain.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Log.d("main", "================================");
				String data = editText.getText().toString();
				EventBus.getDefault().post(data);
			}
		});
		postInSub.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Log.d("sub", "================================");
				final String data = editText.getText().toString();
				new Runnable() {
					@Override
					public void run() {
						EventBus.getDefault().post(data);
					}
				}.run();
			}
		});
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

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onResultInMain(String data) {
		Log.d("MAIN", "data:" + data);
		Log.i("MAIN", "thread name:" + Thread.currentThread().getName());
		Log.i("MAIN", "thread id:" + Thread.currentThread().getId());
	}

	@Subscribe(threadMode = ThreadMode.POSTING)
	public void onResultInPosting(String data) {
		Log.d("POSTING", "data:" + data);
		Log.i("POSTING", "thread name:" + Thread.currentThread().getName());
		Log.i("POSTING", "thread id:" + Thread.currentThread().getId());
	}

	@Subscribe(threadMode = ThreadMode.BACKGROUND)
	public void onResultInBackground(String data) {
		Log.d("BACKGROUND", "data:" + data);
		Log.i("BACKGROUND", "thread name:" + Thread.currentThread().getName());
		Log.i("BACKGROUND", "thread id:" + Thread.currentThread().getId());
	}

	@Subscribe(threadMode = ThreadMode.ASYNC)
	public void onResultInAsync(String data) {
		Log.d("ASYNC", "data:" + data);
		Log.i("ASYNC", "thread name:" + Thread.currentThread().getName());
		Log.i("ASYNC", "thread id:" + Thread.currentThread().getId());
	}
}
