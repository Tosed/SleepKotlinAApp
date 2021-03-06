/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

        private var viewModelJob = Job()

        override fun onCleared() {
                super.onCleared()
                viewModelJob.cancel()
        }
        
        private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
        private var tonight = MutableLiveData<SleepNight?>()
        private val nights = database.getAllNights()
        val nightString = Transformations.map(nights) { nights ->
                formatNights(nights, application.resources)
        }
        private val _navigateToSleepQuality = MutableLiveData<SleepNight>()
        val navigateToSleepQuality : LiveData<SleepNight> = _navigateToSleepQuality

        private val _showSnackbarEvent = MutableLiveData<Boolean>()
        val showSnackbarEvent: LiveData<Boolean> = _showSnackbarEvent

        // O botao INICIAR deve ser visible quando o tonight for nulo
        val startButtonVisible = Transformations.map(tonight){
                null == it
        }
        // O botao FINALIZAR deve ser visible quando o tonight nao foi nulo
        val stopButtonVisible = Transformations.map(tonight){
                null != it
        }

        // Aparece quando Nights tem algum conteudo, se estiver vazio nao aparece
        val clearButtonVisible = Transformations.map(nights){
                it?.isNotEmpty()
        }
        
        init {
            initializeToNight()
        }

        fun doneNavigating(){
                _navigateToSleepQuality.value = null
        }

        fun doneShowingSnackbar(){
                _showSnackbarEvent.value = false
        }

        private fun initializeToNight() {
                uiScope.launch { 
                        tonight.value = getToNightFromDatabase()
                }
        }

        private suspend fun getToNightFromDatabase(): SleepNight? {
                return withContext(Dispatchers.IO){
                        var night = database.getTonight()
                        if (night?.endTimeMilli != night?.startTimeMilli){
                                night = null
                        }
                        night
                }
        }

        fun onStartTracking(){
                uiScope.launch {
                        val newNight = SleepNight()
                        insert(newNight)
                        tonight.value = getToNightFromDatabase()
                }
        }
        private suspend fun insert(night: SleepNight){
                withContext(Dispatchers.IO){
                        database.insert(night)
                }
        }

        fun onStopTracking(){
                uiScope.launch {
                        val oldNight = tonight.value ?: return@launch
                        oldNight.endTimeMilli = System.currentTimeMillis()
                        _navigateToSleepQuality.value = oldNight
                        update(oldNight)
                }
        }
        private suspend fun update(night: SleepNight){
                withContext(Dispatchers.IO){
                        database.update(night)
                }
        }
        fun onClear(){
                uiScope.launch {
                        clear()
                        tonight.value = null
                        _showSnackbarEvent.value = true
                }
        }
        private suspend fun clear(){
                withContext(Dispatchers.IO){
                        database.clear()
                }
        }

}

