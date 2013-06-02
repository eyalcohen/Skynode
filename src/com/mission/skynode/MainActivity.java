package com.mission.skynode;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.location.*;
import android.content.Context;

import java.util.*;
import org.apache.commons.collections.buffer.CircularFifoBuffer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.Handler;

public class MainActivity extends Activity {
	SkynodeModel model; 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        model = new SkynodeModel(this);
        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() { public void run() {
        	viewUpdater();        	
        }};
        Thread updater = new Thread() {
        	public void run() {
	        	while (true) { 
	        		handler.post(runnable); 
	        		try {
	        			Thread.sleep(100);
	        		}
	        		catch (InterruptedException e) {}
	        	}
        	}
        };
        updater.setDaemon(true);
        updater.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    public void gpsLoggingButton(View view) {
    	Button button = (Button)view;

    	model.manageGpsLogging();
    	
    	if (model.gpsLoggingState) {
    		button.setText("Stop GPS");
    	} else {
    		button.setText("GPS Logging");
    	}
    }
    
    void viewUpdater() {
    	final TextView samplesCollectedText = (TextView)findViewById(R.id.samplesCollectedText);
    	samplesCollectedText.setText(String.valueOf(model.samplesCollected));
    }
}


class SkynodeModel {
    CircularFifoBuffer timeSeriesData;
    boolean gpsLoggingState;
    LocationManager locationManager;
    LocationListener locationListener;
    int samplesCollected = 0;

    class InterestingTimeSeriesData {
        long time;
        Location location;
    }

    SkynodeModel(Activity activity) {
        timeSeriesData = new CircularFifoBuffer(4095);
        gpsLoggingState = false;
        locationManager = (LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
    }

    void manageGpsLogging() {
        if (gpsLoggingState) {
            locationManager.removeUpdates(locationListener);
        }
        else {
        // Define a listener that responds to location updates
            locationListener = new LocationListener() {
                public void onLocationChanged(Location location) {
                	logData(location);
                }
                public void onStatusChanged(String provider, int status, Bundle extras) {}
                public void onProviderEnabled(String provider) {}
                public void onProviderDisabled(String provider) {}
              };
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
        gpsLoggingState = !gpsLoggingState;
    }

    void logData(Location location) {
        InterestingTimeSeriesData point = new InterestingTimeSeriesData();
        point.time = Calendar.getInstance().getTimeInMillis();
        point.location = location;
        timeSeriesData.add(point);
        samplesCollected++;
    }
}

