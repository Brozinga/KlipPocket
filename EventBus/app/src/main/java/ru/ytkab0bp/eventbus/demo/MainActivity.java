package ru.ytkab0bp.eventbus.demo;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import ru.ytkab0bp.eventbus.EventBus;
import ru.ytkab0bp.eventbus.EventHandler;
import ru.ytkab0bp.eventbus.demo.events.InheritedEvent;
import ru.ytkab0bp.eventbus.demo.events.TestEvent;

public class MainActivity extends AppCompatActivity {
    private final static EventBus EVENT_BUS = EventBus.newBus("main");
    private static boolean a;

    @EventHandler(runOnMainThread = true)
    public void onTestEvent(TestEvent e) {
        Toast.makeText(MainActivity.this, "Delivered event in "+(System.currentTimeMillis() - e.startMs)+"ms!", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!a) {
            EventBus.registerImpl(this);
            a = true;
        }
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> EVENT_BUS.fireEvent(new InheritedEvent()));
        EVENT_BUS.registerListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EVENT_BUS.unregisterListener(this);
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
}