package com.dacs.sict.htxv.taxitranspot.View;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.dacs.sict.htxv.taxitranspot.Common.Common;
import com.dacs.sict.htxv.taxitranspot.Helper.CustomInfoWindow;
import com.dacs.sict.htxv.taxitranspot.Model.FCMResponse;
import com.dacs.sict.htxv.taxitranspot.Model.Notification;
import com.dacs.sict.htxv.taxitranspot.Model.Rider;
import com.dacs.sict.htxv.taxitranspot.Model.Sender;
import com.dacs.sict.htxv.taxitranspot.Model.Token;
import com.dacs.sict.htxv.taxitranspot.R;
import com.dacs.sict.htxv.taxitranspot.Remote.IFCMService;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Home extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    SupportMapFragment mapFragment;

    //location
    private GoogleMap mMap;
    private static final int My_PREMISSON_REQUEST_CODE = 7000;
    private static final int PLAY_SERVICES_RES_REQUEST = 7001;

    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;

    private static int UPDATE_INRERVAL = 5000;
    private static int FATEST_INTERVAL = 3000;
    private static int DISPLACEMENT = 10;

    DatabaseReference ref;
    GeoFire geoFire;

    Marker mUserMarker;

    //BottomSheet
    ImageView imgExpandable;
    BottomSheetRiderFragment mBottomSheet;
    Button btnPickupRequest;

    boolean isDriverFound = false;
    String driverId = "";
    int radius = 1; // 1km
    int distance = 1; // 1km
    private static final int LIMIT = 3;

    IFCMService mService;

    //peesense system
    DatabaseReference driversAvailable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_home );
        Toolbar toolbar = (Toolbar) findViewById( R.id.toolbar );
        setSupportActionBar( toolbar );


        DrawerLayout drawer = (DrawerLayout) findViewById( R.id.drawer_layout );
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle( this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close );
        drawer.addDrawerListener( toggle );
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById( R.id.nav_view );
        navigationView.setNavigationItemSelectedListener( this );

        mService = Common.getFCMservice();

        //map
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById( R.id.map );
        mapFragment.getMapAsync( this );


        //Init view
        imgExpandable = (ImageView) findViewById( R.id.imgExpandable );
        mBottomSheet = (BottomSheetRiderFragment) BottomSheetRiderFragment.newInstance( "Rider bottom sheet" );
        imgExpandable.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBottomSheet.show( getSupportFragmentManager(), mBottomSheet.getTag() );
            }
        } );

        btnPickupRequest = (Button) findViewById( R.id.btnPickupRequest );
        btnPickupRequest.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isDriverFound)
                    requestPickupHere( FirebaseAuth.getInstance().getCurrentUser().getUid() );
                else sendRequestToDriver( driverId );


            }
        } );

        setUpLocation();

        updateFirebaseToken();
    }

    private void updateFirebaseToken() {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        DatabaseReference tokens = db.getReference( Common.token_tbl );

        Token token = new Token( FirebaseInstanceId.getInstance().getToken() );
        tokens.child( FirebaseAuth.getInstance().getCurrentUser().getUid() ).setValue( token );
    }

    private void sendRequestToDriver(String driveId) {
        DatabaseReference tokens = FirebaseDatabase.getInstance().getReference( Common.token_tbl );
        tokens.orderByKey().equalTo( driveId ).addListenerForSingleValueEvent( new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapShot : dataSnapshot.getChildren()) {

                    Token token = postSnapShot.getValue( Token.class );//get token objek from database with key

                    String json_lat_long = new Gson().toJson( new LatLng( mLastLocation.getLatitude(), mLastLocation.getLongitude() ) );
                    String riderTonken = FirebaseInstanceId.getInstance().getToken();
                    Notification data = new Notification( riderTonken, json_lat_long );//send it to driver and will deseriliaize it again
                    Sender content = new Sender( token.getToken(), data );

                    mService.sendMessage( content ).enqueue( new Callback<FCMResponse>() {
                        @Override
                        public void onResponse(Call<FCMResponse> call, Response<FCMResponse> response) {
                            if (response.body().success == 1 ){
                                Toast.makeText( Home.this, "Request sent!!", Toast.LENGTH_SHORT ).show();

                            }else Toast.makeText( Home.this, "Faile", Toast.LENGTH_SHORT ).show();
                        }

                        @Override
                        public void onFailure(Call<FCMResponse> call, Throwable t) {
                            Log.e( "ERROR", t.getMessage() );


                        }
                    } );


                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        } );
    }

    private void requestPickupHere(String uid) {
        DatabaseReference dbRequest = FirebaseDatabase.getInstance().getReference( Common.pickup_request_tbl );
        GeoFire mGeoFire = new GeoFire( dbRequest );
        mGeoFire.setLocation( uid, new GeoLocation( mLastLocation.getLatitude(), mLastLocation.getLongitude() ) );
        if (mUserMarker.isVisible()) mUserMarker.remove();

        //add marker
        mUserMarker = mMap.addMarker( new MarkerOptions().title( "Pickup Here" ).snippet( "" ).position( new LatLng( mLastLocation.getLatitude(), mLastLocation.getLongitude() ) ).icon( BitmapDescriptorFactory.defaultMarker( BitmapDescriptorFactory.HUE_RED ) ) );
        mUserMarker.showInfoWindow();

        btnPickupRequest.setText( "Getting your DRIVER..." );

        findDriver();

    }

    private void findDriver() {
        DatabaseReference drivers = FirebaseDatabase.getInstance().getReference( Common.driver_tbl );
        GeoFire gfDrivers = new GeoFire( drivers );

        GeoQuery geoQuery = gfDrivers.queryAtLocation( new GeoLocation( mLastLocation.getLatitude(), mLastLocation.getLongitude() ), radius );
        geoQuery.removeAllListeners();
        geoQuery.addGeoQueryEventListener( new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                // if found
                if (!isDriverFound) {
                    isDriverFound = true;
                    driverId = key;
                    btnPickupRequest.setText( "CALL DRIVER" );
                    Toast.makeText( Home.this, "" + key, Toast.LENGTH_SHORT ).show();
                }

            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                //if still not found driver, increase distance
                if (!isDriverFound) {
                    radius++;
                    findDriver();
                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        } );
    }


    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case My_PREMISSON_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (checkPlayServices()) {
                        buildGoogleApiClient();
                        createLocatioRequest();
                        displayLocation();
                    }
                }
                break;
        }
    }

    private void setUpLocation() {
        if (ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED)
        {
            //request runtime permission
            ActivityCompat.requestPermissions( this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, My_PREMISSON_REQUEST_CODE );
        } else {
            if (checkPlayServices()) {
                buildGoogleApiClient();
                createLocatioRequest();
                displayLocation();
            }
        }
    }

    private void displayLocation() {
        if (ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation( mGoogleApiClient );
        if (mLastLocation != null) {

            // presense system

            driversAvailable=FirebaseDatabase.getInstance().getReference(Common.driver_tbl);
            driversAvailable.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    loadAllAvailableDriver();
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });

            final double latitude = mLastLocation.getLatitude();
            final double longitude = mLastLocation.getLongitude();

            //add marker
            if (mUserMarker != null) mUserMarker.remove();
            mUserMarker = mMap.addMarker( new MarkerOptions().position( new LatLng( latitude, longitude ) ).title( "You" ) );

            mMap.animateCamera( CameraUpdateFactory.newLatLngZoom( new LatLng( latitude, longitude ), 15.0f ) );

            loadAllAvailableDriver();


            Log.d( "DEV", String.format( "You location was changed : %f / %f ", latitude, longitude ) );

        } else {
            Log.d( "ERROR", "Canot get your location" );
        }

    }

    private void loadAllAvailableDriver() {

        mMap.clear();
        mMap.addMarker( new MarkerOptions().position( new LatLng( mLastLocation.getLatitude(), mLastLocation.getLongitude() ) ).title( "You" ) );

        //Load all  available Driver is distance 3km
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference( Common.driver_tbl );
        GeoFire gf = new GeoFire( driverLocation );

        GeoQuery geoQuery = gf.queryAtLocation( new GeoLocation( mLastLocation.getLatitude(), mLastLocation.getLongitude() ), distance );
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener( new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, final GeoLocation location) {
                FirebaseDatabase.getInstance().getReference( Common.user_driver_tbl ).child( key ).addListenerForSingleValueEvent( new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Rider rider = dataSnapshot.getValue( Rider.class );

                        //add driver to map
                        mMap.addMarker( new MarkerOptions().position( new LatLng( location.latitude, location.longitude ) ).flat( true ).title( rider.getmName() ).snippet( "Phone : " + rider.getmPhoneNumber() ).icon( BitmapDescriptorFactory.fromResource( R.drawable.car ) ) );
                    }

                    @Override

                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                } );

            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if (distance <= LIMIT) {
                    distance++;
                    loadAllAvailableDriver();
                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        } );
    }

    private void createLocatioRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval( UPDATE_INRERVAL );
        mLocationRequest.setFastestInterval( FATEST_INTERVAL );
        mLocationRequest.setPriority( LocationRequest.PRIORITY_HIGH_ACCURACY );
        mLocationRequest.setSmallestDisplacement( DISPLACEMENT );

    }

    private void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder( this ).addConnectionCallbacks( this ).addOnConnectionFailedListener( this ).addApi( LocationServices.API ).build();
        mGoogleApiClient.connect();
    }

    private boolean checkPlayServices() {
        int resultcode = GooglePlayServicesUtil.isGooglePlayServicesAvailable( this );
        if (resultcode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError( resultcode ))
                GooglePlayServicesUtil.getErrorDialog( resultcode, this, PLAY_SERVICES_RES_REQUEST ).show();
            else {
                Toast.makeText( this, "This device is not supported", Toast.LENGTH_SHORT ).show();
                finish();
            }
            return false;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById( R.id.drawer_layout );
        if (drawer.isDrawerOpen( GravityCompat.START )) {
            drawer.closeDrawer( GravityCompat.START );
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate( R.menu.home, menu );
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

        return super.onOptionsItemSelected( item );
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById( R.id.drawer_layout );
        drawer.closeDrawer( GravityCompat.START );
        return true;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        displayLocation();
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates( mGoogleApiClient, mLocationRequest, (com.google.android.gms.location.LocationListener) this );
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        displayLocation();

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled( true );
        mMap.getUiSettings().setZoomGesturesEnabled( true );
        mMap.setInfoWindowAdapter( new CustomInfoWindow( this ) );
    }
}
