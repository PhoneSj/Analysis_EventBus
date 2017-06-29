package com.phone.test;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Created by Phone on 2017/6/29.
 */

public class MyTextView2 extends TextView {

	public MyTextView2(Context context) {
		this(context, null);
	}

	public MyTextView2(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public MyTextView2(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Subscribe(threadMode = ThreadMode.POSTING)
	public void onResult(String data) {
		setText(data);
	}
}
