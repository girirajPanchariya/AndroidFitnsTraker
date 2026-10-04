package com.cscorner.fitsynck

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import com.cscorner.fitsynck.data.reposetry.FintnessReposetry
import com.cscorner.fitsynck.databinding.ActivityMainBinding
import com.cscorner.fitsynck.ui.ViewModel.FitnessViewModel

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng

import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // OSMDroid Map
    private lateinit var map: MapView

    // Current location marker
    private lateinit var currentLocationMarker: Marker

    // Route line
    private lateinit var pathPolyline: Polyline

    // UI
    private lateinit var startStopButton: Button
    private lateinit var distanceValue: TextView
    private lateinit var caloriesValue: TextView
    private lateinit var durationValue: TextView

    // Workout state
    private var isWorkoutPaused = false

    // Used to center map only on first location
    private var firstLocationUpdate = true

    // Fused location client
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    // Location callback
    private lateinit var locationCallback: LocationCallback

    // ViewModel
    private val viewModel: FitnessViewModel by viewModels {

        FitnessViewModel.Factory(
            FintnessReposetry(this),
            context = this
        )
    }


    // ---------------------------------------------------------
    // PERMISSION RESULT
    // ---------------------------------------------------------

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val fineLocation =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

            val coarseLocation =
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (fineLocation || coarseLocation) {

                Toast.makeText(
                    this,
                    "Location permission granted",
                    Toast.LENGTH_SHORT
                ).show()

                startWorkout()

            } else {

                Toast.makeText(
                    this,
                    "Location permission is required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    // ---------------------------------------------------------
    // ON CREATE
    // ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // OSMDroid configuration
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences(
                "osmdroid",
                MODE_PRIVATE
            )
        )

        // ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // Get MapView from XML
        map = binding.map

        // Get UI references
        startStopButton = binding.btnStartWorkout
        distanceValue = binding.tvDistance
        caloriesValue = binding.tvCalories
        durationValue = binding.tvDuration

        // Setup everything
        setupMap()

        setupLocationCallback()

        setupObservers()

        setupButtons()

        setupWeightInput()
    }


    // ---------------------------------------------------------
    // MAP SETUP
    // ---------------------------------------------------------

    private fun setupMap() {

        // Map type
        map.setTileSource(
            org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK
        )

        // Allow zoom/pan
        map.setMultiTouchControls(true)

        // Initial zoom
        map.controller.setZoom(15.0)

        // Initial position
        val defaultLocation = GeoPoint(
            28.0229,
            73.3119
        )

        map.controller.setCenter(defaultLocation)


        // -----------------------------------------------------
        // CURRENT LOCATION MARKER
        // -----------------------------------------------------

        currentLocationMarker = Marker(map)

        currentLocationMarker.setAnchor(
            Marker.ANCHOR_CENTER,
            Marker.ANCHOR_BOTTOM
        )

        currentLocationMarker.icon =
            ContextCompat.getDrawable(
                this,
                com.cscorner.fitsynck.R.drawable.outline_add_location_24
            )
        currentLocationMarker.title = "Current Location"

        map.overlays.add(currentLocationMarker)


        // -----------------------------------------------------
        // ROUTE POLYLINE
        // -----------------------------------------------------

        pathPolyline = Polyline(map)

        pathPolyline.outlinePaint.strokeWidth = 10f

        pathPolyline.outlinePaint.color =
            ContextCompat.getColor(
                this,
                android.R.color.holo_blue_dark
            )

        map.overlays.add(pathPolyline)

        map.invalidate()
    }


    // ---------------------------------------------------------
    // BUTTON SETUP
    // ---------------------------------------------------------

    private fun setupButtons() {

        // START / PAUSE / RESUME
        binding.btnStartWorkout.setOnClickListener {

            when {

                // Workout is not running
                viewModel.istraking.value != true -> {

                    checkPermissionsAndStartTracking()
                }


                // Workout is paused
                isWorkoutPaused -> {

                    resumeTracking()
                }


                // Workout is running
                else -> {

                    pauseTracking()
                }
            }
        }


        // STOP BUTTON
        binding.btnStop.setOnClickListener {

            stopTracking()
        }
    }


    // ---------------------------------------------------------
    // WEIGHT INPUT
    // ---------------------------------------------------------

    private fun setupWeightInput() {

        binding.weightInput.setOnEditorActionListener { _, _, _ ->

            val weight =
                binding.weightInput
                    .text
                    .toString()
                    .toFloatOrNull()

            if (weight != null && weight > 0) {

                viewModel.updateWeight(weight)

                Toast.makeText(
                    this,
                    "Weight updated: $weight kg",
                    Toast.LENGTH_SHORT
                ).show()
            }

            false
        }
    }


    // ---------------------------------------------------------
    // PAUSE WORKOUT
    // ---------------------------------------------------------

    private fun pauseTracking() {

        isWorkoutPaused = true

        binding.btnStartWorkout.text = "RESUME"

        binding.btnStop.visibility = View.VISIBLE

        viewModel.pauseWorkout()
    }


    // ---------------------------------------------------------
    // RESUME WORKOUT
    // ---------------------------------------------------------

    private fun resumeTracking() {

        isWorkoutPaused = false

        binding.btnStartWorkout.text = "PAUSE"

        binding.btnStop.visibility = View.GONE

        viewModel.resumeWorkout()
    }


    // ---------------------------------------------------------
    // CHECK LOCATION PERMISSION
    // ---------------------------------------------------------

    private fun checkLocationPermission(): Boolean {

        val fineLocation =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED


        val coarseLocation =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED


        return fineLocation || coarseLocation
    }


    // ---------------------------------------------------------
    // CHECK PERMISSION AND START
    // ---------------------------------------------------------

    private fun checkPermissionsAndStartTracking() {

        if (checkLocationPermission()) {

            startWorkout()

        } else {

            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }


    // ---------------------------------------------------------
    // START WORKOUT
    // ---------------------------------------------------------

    private fun startWorkout() {

        if (!checkLocationPermission()) {
            return
        }

        // Start ViewModel workout
        viewModel.startWorkout()

        // Start GPS
        startLocationUpdates()

        // Update UI
        isWorkoutPaused = false

        binding.btnStartWorkout.text = "PAUSE"

        binding.btnStop.visibility = View.GONE
    }


    // ---------------------------------------------------------
    // STOP WORKOUT
    // ---------------------------------------------------------

    private fun stopTracking() {

        stopLocationUpdates()

        viewModel.stopWorkout()

        isWorkoutPaused = false

        binding.btnStartWorkout.text = "START"

        binding.btnStop.visibility = View.GONE

        firstLocationUpdate = true

        Toast.makeText(
            this,
            "Workout stopped",
            Toast.LENGTH_SHORT
        ).show()
    }


    // ---------------------------------------------------------
    // LOCATION CALLBACK
    // ---------------------------------------------------------

    private fun setupLocationCallback() {

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(
                result: LocationResult
            ) {

                result.lastLocation?.let { location ->

                    val latLng = LatLng(
                        location.latitude,
                        location.longitude
                    )

                    updateLocationMarker(latLng)
                }
            }
        }
    }


    // ---------------------------------------------------------
    // START GPS LOCATION UPDATES
    // ---------------------------------------------------------

    private fun startLocationUpdates() {

        if (!checkLocationPermission()) {
            return
        }

        val locationRequest =
            LocationRequest.create().apply {

                priority =
                    LocationRequest.PRIORITY_HIGH_ACCURACY

                interval = 1000L

                fastestInterval = 500L
            }


        try {

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )

        } catch (e: SecurityException) {

            Toast.makeText(
                this,
                "Unable to access location",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // ---------------------------------------------------------
    // STOP GPS LOCATION UPDATES
    // ---------------------------------------------------------

    private fun stopLocationUpdates() {

        fusedLocationClient.removeLocationUpdates(
            locationCallback
        )
    }


    // ---------------------------------------------------------
    // UPDATE CURRENT LOCATION MARKER
    // ---------------------------------------------------------

    private fun updateLocationMarker(
        latLng: LatLng
    ) {

        // IMPORTANT:
        // GeoPoint = latitude, longitude
        val geoPoint = GeoPoint(
            latLng.latitude,
            latLng.longitude
        )


        // Move marker
        currentLocationMarker.position = geoPoint


        // First location
        if (firstLocationUpdate) {

            map.controller.setZoom(18.0)

            map.controller.setCenter(geoPoint)

            firstLocationUpdate = false

        }


        // During workout keep map following user
        else if (viewModel.istraking.value == true) {

            map.controller.animateTo(geoPoint)
        }


        map.invalidate()
    }


    // ---------------------------------------------------------
    // OBSERVERS
    // ---------------------------------------------------------

    private fun setupObservers() {

        // -----------------------------------------
        // TRACKING
        // -----------------------------------------

        viewModel.istraking.observe(this) { isTracking ->

            if (isTracking) {

                if (!isWorkoutPaused) {
                    startStopButton.text = "PAUSE"
                }

            } else {

                if (!isWorkoutPaused) {
                    startStopButton.text = "START"
                }
            }
        }


        // -----------------------------------------
        // DURATION
        // -----------------------------------------

        viewModel.duration.observe(this) { duration ->

            durationValue.text =
                viewModel.formantDuration(duration)
        }


        // -----------------------------------------
        // ROUTE POINTS
        // -----------------------------------------

        viewModel.routPoints.observe(this) { points ->

            updateRouteOnMap(points)
        }


        // -----------------------------------------
        // CURRENT LOCATION
        // -----------------------------------------

        viewModel.currentLocation.observe(this) { location ->

            if (location != null) {

                updateLocationMarker(location)
            }
        }


        // -----------------------------------------
        // CALORIES
        // -----------------------------------------

        viewModel.calories.observe(this) { calories ->

            caloriesValue.text =
                viewModel.formantCalories(calories)
        }


        // -----------------------------------------
        // DISTANCE
        // -----------------------------------------

        viewModel.distance.observe(this) { distance ->

            distanceValue.text =
                viewModel.formatDistance(distance)
        }
    }


    // ---------------------------------------------------------
    // UPDATE ROUTE ON MAP
    // ---------------------------------------------------------

    private fun updateRouteOnMap(
        points: List<LatLng>
    ) {

        // Nothing to draw
        if (points.isEmpty()) {

            pathPolyline.setPoints(
                emptyList()
            )

            map.invalidate()

            return
        }


        try {

            // Convert Google LatLng
            // into OSMDroid GeoPoint

            val geoPoints =
                points.map { point ->

                    GeoPoint(
                        point.latitude,
                        point.longitude
                    )
                }


            // Draw route
            pathPolyline.setPoints(
                geoPoints
            )


            // Current location
            val currentLocation =
                geoPoints.last()


            // Update marker
            currentLocationMarker.position =
                currentLocation


            // Follow user
            if (viewModel.istraking.value == true) {

                map.controller.animateTo(
                    currentLocation
                )
            }


            map.invalidate()

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }


    // ---------------------------------------------------------
    // ACTIVITY RESUME
    // ---------------------------------------------------------

    override fun onResume() {

        super.onResume()

        map.onResume()
    }


    // ---------------------------------------------------------
    // ACTIVITY PAUSE
    // ---------------------------------------------------------

    override fun onPause() {

        map.onPause()

        super.onPause()
    }


    // ---------------------------------------------------------
    // ACTIVITY DESTROY
    // ---------------------------------------------------------

    override fun onDestroy() {

        stopLocationUpdates()

        super.onDestroy()
    }
}