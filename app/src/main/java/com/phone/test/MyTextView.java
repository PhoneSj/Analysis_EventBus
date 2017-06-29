package com.phone.test;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Created by Phone on 2017/6/29.
 */

public class MyTextView extends TextView {

	public MyTextView(Context context) {
		this(context, null);
	}

	public MyTextView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public MyTextView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Subscribe(threadMode = ThreadMode.POSTING, priority = 1)
	public void onResult(String data) {
		setText(data);
		EventBus.getDefault().cancelEventDelivery(data);
	}
}
