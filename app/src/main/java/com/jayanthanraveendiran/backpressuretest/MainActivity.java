package com.jayanthanraveendiran.backpressuretest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupViews();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupViews() {
        findViewById(R.id.successful_backpressure).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                Observable
                        .from(getIntegerList(15))
                        .doOnSubscribe(new OnSubscribeAction())
                        .observeOn(Schedulers.io())
                        .flatMap(new FlatMapFunc())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                new PrivateSubscriber(
                                        (TextView) findViewById(R.id.successful_backpressure_text)
                                )
                        );
            }
        });

        final int count = 100; // Should be > 16 for Android & > 128 for other platforms
        findViewById(R.id.backpressure_missing).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                Observable
                        .from(getIntegerList(count))
                        .doOnSubscribe(new OnSubscribeAction())
                        .observeOn(Schedulers.io())
                        .flatMap(new FlatMapFunc())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                new PrivateSubscriber(
                                        (TextView) findViewById(R.id.backpressure_missing_text)
                                )
                        );
            }
        });
    }

    private List<Integer> getIntegerList(int count) {
        List<Integer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(i);
        }

        return list;
    }


    private static class FlatMapFunc implements Func1<Integer, Observable<String>> {

        @Override
        public Observable<String> call(final Integer count) {
            return Observable.create(new Observable.OnSubscribe<String>() {
                @Override
                public void call(Subscriber<? super String> subscriber) {
                    doLongBlockingTask();
                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onNext("My number is: " + count + "\n");
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.onCompleted();
                        }
                    }
                }
            });
        }


        private void doLongBlockingTask() {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private static class PrivateSubscriber extends Subscriber<String> {

        private final TextView mTextView;

        public PrivateSubscriber(TextView textView) {
            mTextView = textView;
        }

        @Override
        public void onCompleted() {
            mTextView.append("on completed");
        }

        @Override
        public void onError(Throwable e) {
            mTextView.append(e.toString());
        }

        @Override
        public void onNext(String s) {
            mTextView.append(s);/**/
        }
    }


    private static class OnSubscribeAction implements Action0 {

        @Override
        public void call() {
            Log.i("backpressure", "subscribed");
        }
    }
}
