package com.cscorner.fitsynck.data.Models

import com.google.android.gms.maps.model.LatLng

data class WorkoutSession(
    val id: String,
    val distance: Float,
    val duration: Long,
    val caloriesBurned: Float,
    val routPoint: List<LatLng>,
    val averagePace: Double,
    val timestamp: Long

)
