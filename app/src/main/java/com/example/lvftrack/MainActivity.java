package com.example.lvftrack;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {
    // The minimum distance to change Updates in meters
    private static final long MIN_UPDATE_DISTANCE = 0;

    // The minimum time between updates in milliseconds
    private static final long MIN_UPDATE_TIME = 0;

    // global constants for latitude and longitude
    public static double last_lat = 48.0;
    public static double last_lon = 7.0;
    public static double last_acc = 0.0;

    // further global constant
    public static String mTelNummer = "LastenVeloTest";
    public static boolean moving= false;

    // define location Listener
    LocationManager locationManager;
    LocationListener locationListener = new LocationListener() {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onLocationChanged(Location location) {
            double latitude;
            latitude = location.getLatitude();
            double longitude;
            longitude = location.getLongitude();
            double accuracy;
            accuracy = location.getAccuracy();
            double speed;
            speed = location.getSpeed();
            speed = speed * 3.6;

            if (speed>2.5){
                moving=true;
                sent_data_to_lvf( longitude, latitude, accuracy, speed, false );
            }
            else if (moving) {
                moving = false;
                sent_data_to_lvf( longitude, latitude, accuracy, speed, true );
            }


        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        locationManager = (LocationManager) this.getSystemService( Context.LOCATION_SERVICE );
        if (locationManager == null) throw new AssertionError();
        if (ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions( this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 23 );
            return;
        }
        // call location Manager
        locationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, MIN_UPDATE_TIME, MIN_UPDATE_DISTANCE, locationListener );

    }

    // using getLastKnownLocation() calling once the minute instead of onChange?
    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void onStart() {
        super.onStart();
        final int milisInAnHour = 60000*60;
        final long time = System.currentTimeMillis();

        final Runnable update = new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            public void run() {
                Location currentLoc;
                while (true) {
                    System.out.println( System.currentTimeMillis() );
                    if (ActivityCompat.checkSelfPermission( MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission( MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions( MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 23 );
                        return;
                    }
                    assert locationManager != null;
                    currentLoc = locationManager.getLastKnownLocation( LocationManager.GPS_PROVIDER );
                    System.out.println( currentLoc );
                    if (currentLoc != null) {
                        double latitude;
                        latitude = currentLoc.getLatitude();
                        double longitude;
                        longitude = currentLoc.getLongitude();
                        double accuracy;
                        accuracy = currentLoc.getAccuracy();
                        double speed;
                        speed = currentLoc.getSpeed();
                        speed = speed * 3.6;

                        sent_data_to_lvf( longitude, latitude, accuracy, speed, true );
                        break;
                    }
                    break;
                }
            }
        };

        Timer timer = new Timer();
        timer.schedule( new TimerTask() {
            public void run() {
                update.run();
            }
        }, time % milisInAnHour, milisInAnHour );

        // This will update for the current minute, it will be updated again in at most one minute.
        update.run();
    }

    /**
     * calc distance between two points at earth surface
     * @return double
     * */
    public double calc_distance(double lon_1, double lat_1, double lon_2, double lat2) {
        double R_E = 6371000.;
        double pi = 3.141592653589793238462643383279502884197169399375105820974944592;
        return ((R_E * pi) / 180.) * Math.acos( Math.cos( lat_1 ) * Math.cos( lat2 ) * Math.cos( lon_1 - lon_2 ) + Math.sin( lat_1 ) * Math.sin( lat2 ) );
    }

    /**
     * sent data to LVF Server
     * */
    // Create GetText Method
    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("HardwareIds")
    public  void  sent_data_to_lvf(final double lon, final double lat, final double acc, final double spe, final boolean force_sent){
        // read telefonnumber
        TelephonyManager mTelMan = (TelephonyManager) getSystemService( TELEPHONY_SERVICE );
        if (ActivityCompat.checkSelfPermission( this, Manifest.permission.READ_PHONE_NUMBERS ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions( this, new String[]{Manifest.permission.READ_PHONE_NUMBERS}, 23 );
        } else {
            assert mTelMan != null;
            //mTelNummer = mTelMan.getLine1Number();
        }

        Thread thread = new Thread( new Runnable() {
            @Override
            public void run() {
                try {
                    // calc distance to last location
                    double current_distance;
                    current_distance = calc_distance( lon, lat, last_lon, last_lat );
                    //System.out.println( current_distance );
                    if (current_distance+acc+last_acc > 100. || force_sent) {

                        // Get user defined values
                        last_lat = lat;
                        last_lon = lon;

                        // get battery state
                        BatteryManager bm = (BatteryManager) getSystemService( BATTERY_SERVICE );
                        assert bm != null;
                        final int batLevel = bm.getIntProperty( BatteryManager.BATTERY_PROPERTY_CAPACITY );

                        // Create data variable for sent values to server
                        String data = URLEncoder.encode( "id", "UTF-8" )
                                + "=" + URLEncoder.encode( mTelNummer, "UTF-8" );

                        data += "&" + URLEncoder.encode( "lon", "UTF-8" ) + "="
                                + URLEncoder.encode( String.valueOf( lon ), "UTF-8" );

                        data += "&" + URLEncoder.encode( "lat", "UTF-8" )
                                + "=" + URLEncoder.encode( String.valueOf( lat ), "UTF-8" );

                        data += "&" + URLEncoder.encode( "acc", "UTF-8" )
                                + "=" + URLEncoder.encode( String.valueOf( acc ), "UTF-8" );

                        data += "&" + URLEncoder.encode( "spe", "UTF-8" )
                                + "=" + URLEncoder.encode( String.valueOf( spe ), "UTF-8" );

                        data += "&" + URLEncoder.encode( "bat", "UTF-8" )
                                + "=" + URLEncoder.encode( String.valueOf( batLevel ), "UTF-8" );

                        // Send data
                        // Defined URL  where to send data
                        URL url = new URL( "https://www.lastenvelofreiburg.de/bbposlvf.php" );

                        // Send POST data request
                        URLConnection conn = url.openConnection();
                        conn.setDoOutput( true );
                        OutputStreamWriter wr = new OutputStreamWriter( conn.getOutputStream() );
                        wr.write( data );
                        wr.flush();
                        // Get the server response

                        BufferedReader reader = new BufferedReader( new InputStreamReader( conn.getInputStream() ) );
                        StringBuilder sb = new StringBuilder();
                        String line;

                        // Read Server Response
                        while ((line = reader.readLine()) != null) {
                            // Append server response in string
                            sb.append( line ).append( "\n" );
                        }
                        //String text = sb.toString();
                        //System.out.println( "data reported" );
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } );
        thread.start();
    }
 }



