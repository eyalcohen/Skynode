package com.mission.skynode;

import android.os.Bundle;

import android.app.Activity;
import android.view.Menu;
import android.location.*;
import android.content.Context;

import java.net.MalformedURLException;
import java.util.*;
import org.apache.commons.collections.buffer.CircularFifoBuffer;
import org.json.*;

import io.socket.*;

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
        
        Thread periodicPush = new Thread() { 
        	public void run() {
        		while (true) {
        			model.pushData();
	        		try {
	        			Thread.sleep(100);
	        		}
	        		catch (InterruptedException e) {}
        		}
        	}
        };
        periodicPush.setDaemon(true);
        periodicPush.start();
        
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
    SocketIO socket = null;

    class InterestingTimeSeriesData {
        public long time;
        public double latitude;
        public double longitude;
    }

    SkynodeModel(Activity activity) {
        timeSeriesData = new CircularFifoBuffer(64);
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
            
            // Try to connect to server
    		
    		try {
    			socket = new SocketIO("http://76.21.40.139:8002/");
    		} catch (MalformedURLException e1) {
    			// TODO Auto-generated catch block
    			e1.printStackTrace();
    		}
            socket.connect(new IOCallback() {
                @Override
                public void onMessage(JSONObject json, IOAcknowledge ack) {
                    try {
                        System.out.println("Server said:" + json.toString(2));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String data, IOAcknowledge ack) {
                    System.out.println("Server said: " + data);
                }

                @Override
                public void onError(SocketIOException socketIOException) {
                    System.out.println("an Error occured");
                    socketIOException.printStackTrace();
                }

                @Override
                public void onDisconnect() {
                    System.out.println("Connection terminated.");
                }

                @Override
                public void onConnect() {
                    System.out.println("Connection established");
                }

                @Override
                public void on(String event, IOAcknowledge ack, Object... args) {
                    System.out.println("Server triggered event '" + event + "'");
                }
            });            
        }
        gpsLoggingState = !gpsLoggingState;
    }

    void logData(Location location) {
        InterestingTimeSeriesData point = new InterestingTimeSeriesData();
        point.time = Calendar.getInstance().getTimeInMillis();
        point.latitude = location.getLatitude();
        point.longitude = location.getLongitude();
        timeSeriesData.add(point);
        samplesCollected++;
    }
    
    void pushData() {
    	while (!timeSeriesData.isEmpty()) {
    		InterestingTimeSeriesData d = (InterestingTimeSeriesData)timeSeriesData.remove();
    		try {
    			JSONObject jsonObject = new JSONObject()
    				.put("time", d.time)
    				.put("latitude", d.latitude)
    				.put("longitude", d.longitude);
    			socket.send(jsonObject);
    		} catch (Exception e) { e.printStackTrace(); }
    	}
    }
}

