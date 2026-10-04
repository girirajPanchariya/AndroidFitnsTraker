package com.cscorner.fitsynck.data.reposetry

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng

class FintnessReposetry(
    private val context: Context
) {

    // ============================================================
    // LOCATION CLIENT
    // ============================================================

    private val locationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)


    // ============================================================
    // VARIABLES
    // ============================================================

    // Last valid GPS location
    private var lastValidLocation: Location? = null

    // Check whether user is actually moving
    private var isActualMovie = false

    // Total workout duration
    private var totalDuration: Long = 0

    // Tracking start time
    private var trackingStrartTime: Long = 0

    // Minimum distance required to consider movement
    // Unit = meters
    private val MOVEMENT_THRESHOLD = 1.0f


    // ============================================================
    // ROUTE POINTS
    // ============================================================

    private val _routePoints =
        MutableLiveData<List<LatLng>>(emptyList())

    val routePoints: LiveData<List<LatLng>> =
        _routePoints


    // ============================================================
    // TOTAL DISTANCE
    // ============================================================

    // Distance is stored in meters
    private val _totalDistance =
        MutableLiveData<Float>(0f)

    val totalDistance: LiveData<Float> =
        _totalDistance


    // ============================================================
    // TRACKING STATUS
    // ============================================================

    private val _isTracking =
        MutableLiveData<Boolean>(false)

    val isTracking: LiveData<Boolean> =
        _isTracking


    // ============================================================
    // CURRENT LOCATION
    // ============================================================

    private val _currentLoctaion =
        MutableLiveData<LatLng?>(null)

    val currentLoctaion: LiveData<LatLng> =
        _currentLoctaion as LiveData<LatLng>


    // ============================================================
    // TIME VARIABLES
    // ============================================================

    // Tracking start time
    private var startTime: Long = 0

    // Total active movement time
    private var ActiveMovementTime: Long = 0

    // Last time user was detected moving
    private var lastMovementTime: Long = 0

    // Previous GPS update time
    private var lastLocationUpdateTime: Long = 0


    // ============================================================
    // LOCATION CALLBACK
    // ============================================================

    private val locationCallback =
        object : LocationCallback() {

            override fun onLocationResult(
                result: LocationResult
            ) {

                result.lastLocation?.let { newLocation ->

                    Log.d(
                        "FitnessRepository",
                        "New location received: " +
                                "${newLocation.latitude}, ${newLocation.longitude}"
                    )


                    // Current timestamp
                    val currentTime =
                        System.currentTimeMillis()


                    // ====================================================
                    // UPDATE CURRENT LOCATION
                    // ====================================================

                    _currentLoctaion.postValue(
                        LatLng(
                            newLocation.latitude,
                            newLocation.longitude
                        )
                    )


                    // ====================================================
                    // GPS ACCURACY CHECK
                    // ====================================================

                    // Accuracy greater than 20 meters
                    // means GPS location is not reliable enough.
                    if (newLocation.accuracy > 20f) {

                        Log.d(
                            "FitnessRepository",
                            "GPS accuracy is very poor: " +
                                    "${newLocation.accuracy} meters"
                        )

                        isActualMovie = false

                        return
                    }


                    // ====================================================
                    // CHECK PREVIOUS LOCATION
                    // ====================================================

                    lastValidLocation?.let { lastLocation ->


                        // Calculate distance between
                        // previous location and current location
                        val distance =
                            newLocation.distanceTo(lastLocation)


                        // ====================================================
                        // CALCULATE TIME GAP
                        // ====================================================

                        val timeGap =
                            currentTime - lastLocationUpdateTime


                        // ====================================================
                        // CALCULATE SPEED
                        // ====================================================

                        // Speed = Distance / Time
                        //
                        // distance = meters
                        // time = seconds
                        //
                        // Result = meters/second

                        val speed: Float =
                            if (timeGap > 0) {

                                distance /
                                        (timeGap / 1000f)

                            } else {
                                0f
                            }


                        // ====================================================
                        // ACTUAL MOVEMENT CHECK
                        // ====================================================

                        isActualMovie =
                            distance > MOVEMENT_THRESHOLD &&
                                    speed > 0.3f &&
                                    speed < 8f


                        // ====================================================
                        // USER IS ACTUALLY MOVING
                        // ====================================================

                        if (isActualMovie) {

                            // Update active movement time
                            updateMovementTime(currentTime)

                            // Update route and distance
                            updateLocationData(newLocation)

                            Log.d(
                                "FitnessRepository",
                                "Valid movement: " +
                                        "$distance meters, " +
                                        "Speed: $speed m/s"
                            )
                        }


                    }.run {

                        // ====================================================
                        // FIRST VALID LOCATION
                        // ====================================================

                        lastValidLocation = newLocation

                        isActualMovie = false

                        Log.d(
                            "FitnessRepository",
                            "First valid location received"
                        )
                    }


                    // Save current timestamp
                    // for next speed calculation
                    lastLocationUpdateTime =
                        currentTime
                }
            }
        }


    // ============================================================
    // UPDATE MOVEMENT TIME
    // ============================================================

    private fun updateMovementTime(
        currentTime: Long
    ) {

        // If this is not the first movement update
        if (lastMovementTime > 0) {

            ActiveMovementTime +=
                currentTime - lastMovementTime
        }

        // Save current movement time
        lastMovementTime = currentTime
    }


    // ============================================================
    // UPDATE LOCATION DATA
    // ============================================================

    private fun updateLocationData(
        newLocation: Location
    ) {

        // If user is not actually moving,
        // do not add the location to route.
        if (!isActualMovie) {

            Log.d(
                "FitnessRepository",
                "User is not actually moving"
            )

            return
        }


        // Convert Location to LatLng
        val latlin =
            LatLng(
                newLocation.latitude,
                newLocation.longitude
            )


        // Get current route points
        val currentPoints =
            _routePoints.value
                ?.toMutableList()
                ?: mutableListOf()


        // ============================================================
        // FIRST ROUTE POINT
        // ============================================================

        if (currentPoints.isEmpty()) {

            currentPoints.add(latlin)

            _routePoints.postValue(currentPoints)

            Log.d(
                "FitnessRepository",
                "First route point added"
            )

            // Save valid location
            lastValidLocation = newLocation

            return
        }


        // ============================================================
        // DISTANCE FROM LAST ROUTE POINT
        // ============================================================

        val distanceFromLastPoint =
            calculateDistance(
                currentPoints.last(),
                latlin
            )


        // ============================================================
        // ADD POINT ONLY IF USER MOVED
        // ============================================================

        if (distanceFromLastPoint > MOVEMENT_THRESHOLD) {

            // Add new point
            currentPoints.add(latlin)

            // Update route
            _routePoints.postValue(currentPoints)


            // ========================================================
            // UPDATE TOTAL DISTANCE
            // ========================================================

            val currentTotal =
                _totalDistance.value ?: 0f

            val newTotal =
                currentTotal + distanceFromLastPoint

            _totalDistance.postValue(newTotal)


            Log.d(
                "FitnessRepository",
                "Distance added: $distanceFromLastPoint m"
            )

            Log.d(
                "FitnessRepository",
                "Total distance: $newTotal m"
            )
        }


        // Save latest valid location
        lastValidLocation = newLocation
    }


    // ============================================================
    // INITIALIZE TRACKING
    // ============================================================

    private fun initializedTracking() {

        // Current time
        val currentTime =
            System.currentTimeMillis()


        // Save tracking start time
        startTime = currentTime

        trackingStrartTime = currentTime


        // Reset movement time
        ActiveMovementTime = 0

        lastMovementTime = 0

        lastLocationUpdateTime = 0


        // Tracking started
        _isTracking.postValue(true)


        Log.d(
            "FitnessRepository",
            "Tracking initialized"
        )
    }


    // ============================================================
    // REQUEST LOCATION UPDATES
    // ============================================================

    private fun requestLocationUpdates() {

        // Create location request
        val locationRequist =
            LocationRequest.create().apply {

                // High accuracy GPS
                priority =
                    LocationRequest.PRIORITY_HIGH_ACCURACY

                // Request location every 1 second
                interval = 1000

                // Fastest update every 500ms
                fastestInterval = 500

                // Minimum movement = 1 meter
                smallestDisplacement = 1f
            }


        // ============================================================
        // CHECK PERMISSION
        // ============================================================

        if (checkLocationPermission()) {

            try {

                // Start receiving GPS updates
                locationClient.requestLocationUpdates(
                    locationRequist,
                    locationCallback,
                    context.mainLooper
                )

                Log.d(
                    "FitnessRepository",
                    "Location updates started"
                )

            } catch (e: SecurityException) {

                Toast.makeText(
                    context,
                    "Location permission not granted",
                    Toast.LENGTH_SHORT
                ).show()

                Log.e(
                    "FitnessRepository",
                    "SecurityException",
                    e
                )
            }
        } else {

            Toast.makeText(
                context,
                "Location permission required",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // ============================================================
    // CHECK LOCATION PERMISSION
    // ============================================================

    private fun checkLocationPermission(): Boolean {

        val fineLocation =
            ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED


        val coarseLocation =
            ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED


        // Either permission is enough
        return fineLocation || coarseLocation
    }


    // ============================================================
    // CALCULATE DISTANCE
    // ============================================================

    private fun calculateDistance(
        last: LatLng,
        latlin: LatLng
    ): Float {

        // Array to store calculated distance
        val result =
            FloatArray(1)


        // Location.distanceBetween()
        // returns distance in meters.
        Location.distanceBetween(
            last.latitude,
            last.longitude,
            latlin.latitude,
            latlin.longitude,
            result
        )


        // Return distance in meters
        return result[0]
    }


    // ============================================================
    // CALCULATE CALORIES
    // ============================================================

     fun calculateCalries(
        weight: Float,
        distance: Float,
        duration: Long
    ): Float {

        // If duration is less than 1 second,
        // calories cannot be calculated.
        if (duration < 1000) {
            return 0f
        }


        // Convert milliseconds to hours
        val hours: Double =
            duration / (1000.0 * 60.0 * 60.0)


        if (hours <= 0) {
            return 0f
        }


        // Distance should be in kilometers
        val distanceKm =
            distance / 1000.0


        // Calculate speed in km/hour
        val speed: Double =
            distanceKm / hours


        // Select MET according to speed
        val met =
            when {

                speed <= 4.0 ->
                    2.0

                speed <= 8.0 ->
                    7.0

                speed <= 12.0 ->
                    8.5

                else ->
                    10.0
            }


        // Calories = MET × weight × hours
        val calories =
            met * weight * hours


        return calories.toFloat()
    }


    // ============================================================
    // CALCULATE PACE
    // ============================================================

     fun calculatePace(
        distance: Float,
        duration: Long
    ): Double {

        // Distance must be greater than zero
        if (distance <= 0) {
            return 0.0
        }


        // Duration must be greater than zero
        if (duration <= 0) {
            return 0.0
        }


        // Convert distance from meters to kilometers
        val distanceKm =
            distance / 1000.0


        // Convert duration from milliseconds to minutes
        val minutes =
            duration / (1000.0 * 60.0)


        if (distanceKm <= 0) {
            return 0.0
        }


        // Pace = minutes / kilometers
        //
        // Example:
        // 5 km in 30 minutes
        // pace = 30 / 5
        // pace = 6 min/km

        return minutes / distanceKm
    }


    // ============================================================
    // START TRACKING
    // ============================================================

    fun startTraking() {

        // Check permission first
        if (!checkLocationPermission()) {

            Toast.makeText(
                context,
                "Location permission required",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        // Initialize tracking
        initializedTracking()


        // Start GPS updates
        requestLocationUpdates()


        Log.d(
            "FitnessRepository",
            "Tracking started"
        )
    }


    // ============================================================
    // STOP TRACKING
    // ============================================================

    fun stopTraking() {

        // Stop GPS updates
        locationClient.removeLocationUpdates(
            locationCallback
        )


        // Tracking stopped
        _isTracking.postValue(false)


        // Calculate total duration
        if (trackingStrartTime > 0) {

            totalDuration =
                System.currentTimeMillis() -
                        trackingStrartTime
        }


        Log.d(
            "FitnessRepository",
            "Tracking stopped"
        )

        Log.d(
            "FitnessRepository",
            "Total duration: $totalDuration ms"
        )

        Log.d(
            "FitnessRepository",
            "Active movement time: $ActiveMovementTime ms"
        )


        // Reset timer variables
        resetTimer()
    }


    // ============================================================
    // RESET TIMER
    // ============================================================

    private fun resetTimer() {

        // Do NOT reset totalDuration here,
        // because getCurrentDuration() needs
        // the final duration after stop.

        lastMovementTime = 0

        ActiveMovementTime = 0

        trackingStrartTime = 0

        lastLocationUpdateTime = 0
    }


    // ============================================================
    // CLEAR TRACKING
    // ============================================================

    fun clearTraking() {

        // Stop GPS updates
        locationClient.removeLocationUpdates(
            locationCallback
        )


        // Clear route
        _routePoints.postValue(
            emptyList()
        )


        // Reset distance
        _totalDistance.postValue(0f)


        // Tracking false
        _isTracking.postValue(false)


        // Reset start time
        startTime = 0


        // Reset timers
        resetTimer()


        // Reset total duration
        totalDuration = 0


        // Remove previous GPS location
        lastValidLocation = null


        // Reset movement status
        isActualMovie = false


        // Reset current location
        _currentLoctaion.postValue(null)


        Log.d(
            "FitnessRepository",
            "Tracking data completely cleared"
        )
    }


    // ============================================================
    // GET CURRENT DURATION
    // ============================================================

    fun getCurrentDuration(): Long {

        return if (isTracking.value == true) {

            // Tracking is running
            System.currentTimeMillis() -
                    trackingStrartTime

        } else {

            // Tracking stopped
            totalDuration
        }
    }


    // ============================================================
    // GET ACTIVE MOVEMENT TIME
    // ============================================================

    fun getActiveMovementTime(): Long {

        return if (isTracking.value == true) {

            // If tracking is running,
            // calculate current active time.
            val currentTime =
                System.currentTimeMillis()


            if (lastMovementTime > 0) {

                ActiveMovementTime +
                        (currentTime - lastMovementTime)

            } else {

                ActiveMovementTime
            }

        } else {

            ActiveMovementTime
        }
    }
}