package com.phone.test;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends Activity {

	private String title[] = { "Simple", "CustomEvent", "thread", "priority", "sticky" };
	private Class acts[] = { SimpleActivity.class, CustomEventActivity.class, ThreadActivity.class,
			PriorityActivity.class, StickyActivity.class };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ListView listView = (ListView) findViewById(R.id.listView);
		listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, title));
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				startActivity(new Intent(MainActivity.this, acts[i]));
			}
		});
	}

}
