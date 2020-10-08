package com.baseflow.geolocator.location;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.baseflow.geolocator.errors.ErrorCallback;
import com.baseflow.geolocator.errors.ErrorCodes;
import com.baseflow.geolocator.errors.PermissionUndefinedException;
import com.baseflow.geolocator.permission.LocationPermission;
import com.baseflow.geolocator.permission.PermissionManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStates;

import io.flutter.plugin.common.PluginRegistry;

public class GeolocationManager implements PluginRegistry.ActivityResultListener {
    @NonNull
    private final PermissionManager permissionManager;
    @Nullable
    private LocationClient locationClient;

    public GeolocationManager(@NonNull PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    public void getLastKnownPosition(
            Context context,
            Activity activity,
            boolean forceLocationManager,
            PositionChangedCallback positionChangedCallback,
            ErrorCallback errorCallback) {

        handlePermissions(
                context,
                activity,
                () -> {
                    LocationClient locationClient = createLocationClient(context, forceLocationManager);
                    locationClient.getLastKnownPosition(positionChangedCallback, errorCallback);
                },
                errorCallback);
    }


    public void isLocationServiceEnabled(@Nullable Context context, LocationServiceListener listener) {
        if (context == null) {
            listener.onLocationServiceError(ErrorCodes.locationServicesDisabled);
        }
        boolean gps_enabled;
        boolean network_enabled;
        if (isGooglePlayServicesAvailable(context)) {
            LocationServices
                    .getSettingsClient(context)
                    .checkLocationSettings(
                            new LocationSettingsRequest.Builder()
                                    .build()).addOnCompleteListener((response) -> {
                if (response.isSuccessful()) {
                    LocationSettingsResponse lsr = response.getResult();
                    if (lsr != null) {
                        LocationSettingsStates settingsStates = lsr.getLocationSettingsStates();
                        listener.onLocationServiceResult(settingsStates.isGpsUsable() || settingsStates.isNetworkLocationUsable());
                    } else {
                        listener.onLocationServiceError(ErrorCodes.locationServicesDisabled);
                    }
                }
            });
        } else {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                listener.onLocationServiceResult(false);
            }
            gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            listener.onLocationServiceResult(gps_enabled || network_enabled);
        }
    }



    public void startPositionUpdates(
            Context context,
            Activity activity,
            LocationOptions options,
            PositionChangedCallback positionChangedCallback,
            ErrorCallback errorCallback) {

        handlePermissions(
                context,
                activity,
                () -> {
                    LocationClient locationClient = createLocationClient(context, options.isForceAndroidLocationManager());
                    locationClient.startPositionUpdates(activity, options, positionChangedCallback, errorCallback);
                },
                errorCallback);
    }

    public void stopPositionUpdates() {
        if (this.locationClient != null) {
            this.locationClient.stopPositionUpdates();
        }
    }

    private LocationClient createLocationClient(Context context, boolean forceLocationManagerClient) {
        if (forceLocationManagerClient) {
            this.locationClient = new LocationManagerClient(context);
            return this.locationClient;
        }

        this.locationClient = isGooglePlayServicesAvailable(context)
                ? new FusedLocationClient(context)
                : new LocationManagerClient(context);

        return this.locationClient;
    }

    private boolean isGooglePlayServicesAvailable(Context context) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    private void handlePermissions(Context context, @Nullable Activity activity, Runnable hasPermissionCallback, ErrorCallback errorCallback) {
        try {
            LocationPermission permissionStatus = permissionManager.checkPermissionStatus(context, activity);

            if (permissionStatus == LocationPermission.deniedForever) {
                errorCallback.onError(ErrorCodes.permissionDenied);
                return;
            }

            if (permissionStatus == LocationPermission.whileInUse || permissionStatus == LocationPermission.always) {
                hasPermissionCallback.run();
                return;
            }

            if (permissionStatus == LocationPermission.denied && activity != null) {
                permissionManager.requestPermission(
                        activity,
                        (permission) -> {
                            if (permission == LocationPermission.whileInUse || permission == LocationPermission.always) {
                                hasPermissionCallback.run();
                            } else {
                                errorCallback.onError(ErrorCodes.permissionDenied);
                            }
                        },
                        errorCallback);
            } else {
                errorCallback.onError(ErrorCodes.permissionDenied);
            }
        } catch (PermissionUndefinedException ex) {
            errorCallback.onError(ErrorCodes.permissionDefinitionsNotFound);
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (locationClient == null) {
            return false;
        }

        return locationClient.onActivityResult(requestCode, resultCode);
    }
}
