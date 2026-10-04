package com.cscorner.fitsynck.ui.ViewModel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cscorner.fitsynck.data.Models.WorkoutSession
import com.cscorner.fitsynck.data.reposetry.FintnessReposetry
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class FitnessViewModel(
    private val reposetry: FintnessReposetry,
    private val context: Context
) : ViewModel() {
    val currentLocation: LiveData<LatLng> = reposetry.currentLoctaion
    private val _distance  = MutableLiveData<Float>(0f)
    val distance: LiveData<Float> = _distance

    private val _calories = MutableLiveData<Float>(0f)
    val calories:LiveData<Float> =_calories

    private val _istraking = MutableLiveData<Boolean>(false)
    val istraking: LiveData<Boolean> = _istraking

    private val _duration = MutableLiveData<Long>(0L)
    val duration: LiveData<Long> = _duration


    private val _pace = MutableLiveData<Double>(0.0)
    val pace: LiveData<Double> = _pace

    private val _routPoints: LiveData<List<LatLng>> = reposetry.routePoints
    val routPoints  =_routPoints

    private var durationUdatejob: Job? = null
    private var userWeight: Float = 70f
    private var activeTime: Long = 0
    private var lastUpdataTime: Long = 0

    init {
        reposetry.totalDistance.observeForever {
            newDistance->
            _distance.value = newDistance
            updateColeries()
            updatePace()
        }
        reposetry.isTracking.observeForever {
            istraking->
            _istraking.value = istraking
            if(!istraking){
                lastUpdataTime = 0
            }
        }
    }
  fun startWorkout(){
        viewModelScope.launch {
            _istraking.value=true
            _duration.value = 0
            _calories.value=0f
            _distance.value=0f
            activeTime = 0
            lastUpdataTime = System.currentTimeMillis()
            reposetry.startTraking()
            startDurationUpdates()


        }
    }
    fun updateWeight(weight: Float) {
        if (weight > 0) {
            userWeight = weight

            // Weight change hone ke baad calories dobara calculate karo
            updateColeries()
        }
    }
    private fun startDurationUpdates(){
        durationUdatejob?.cancel()
        durationUdatejob = viewModelScope.launch {
            while (isActive && _istraking.value == true){
_duration.value = reposetry.getCurrentDuration()
                updateColeries()
                delay(1000)

            }
        }
    }

    private fun updateColeries() {
        val weight = 70f
        val distance = _distance.value?:0f
        val duration = _duration.value?:0L
        val Calries = reposetry.calculateCalries( weight,distance,duration)
        _calories.value = Calries
    }
    private fun updatePace() {
        val distance = _distance.value?:0f
        val duration = _duration.value?:0L

        if(distance>0){
            _pace.value = reposetry.calculatePace(distance,duration)
        }
    }

    fun stopWorkout(){
        _istraking.value = false
        reposetry.stopTraking()
        startDurationUpdates()
        lastUpdataTime=0
        activeTime = 0
//        saveWorkoutSession()
    }
    fun pauseWorkout(){
        _istraking.value = false
        reposetry.stopTraking()
        stopDurationUpdate()
        lastUpdataTime = 0

    }
    fun resumeWorkout(){
        viewModelScope.launch {
            lastUpdataTime = System.currentTimeMillis()
            _istraking.value = true
            reposetry.startTraking()
            startDurationUpdates()
        }
    }
    fun clearWorkout(){
        _distance.value = 0f
        _calories.value = 0f
        _duration.value = 0L
        _pace.value = 0.0
        activeTime = 0
        lastUpdataTime = 0
        reposetry.clearTraking()
    }
    private fun stopDurationUpdate(){
        durationUdatejob?.cancel()
        durationUdatejob = null
        _duration.value = reposetry.getCurrentDuration()
    }
    fun formantDuration(duration: Long): String{
        val seconds = (duration/1000)%60
        val minutes = (duration/(1000*60))%60
        val hours = duration /(1000 *60 *60)
        return String.format("%2d:%02d:%02d",hours,minutes,seconds)
    }
    fun formatnPace(pace: Double): String{
        return String.format("%.2f km",distance)
    }
    fun formantCalories(calories: Float): String{
        return String.format("%.0f kcal",calories)
    }
     fun formatDistance(distance: Float): String {

        return if (distance < 1000) {
            // 1000 meter se kam hai
            "${distance.toInt()} m"
        } else {
            // 1000 meter ya usse zyada hai
            String.format("%.2f km", distance / 1000f)
        }
    }

    class Factory(
        private val reposetry: FintnessReposetry,
        private val context: Context
    ): ViewModelProvider.Factory {
        override fun <T: ViewModel>create(modelClass: Class<T>):T{
            if(modelClass.isAssignableFrom(FitnessViewModel::class.java)){
                @Suppress("UNCHEKED CLASS")
                return FitnessViewModel(reposetry,context)as T
            }
            throw IllegalArgumentException("Unknow ViewModel Class")
        }
    }
}



