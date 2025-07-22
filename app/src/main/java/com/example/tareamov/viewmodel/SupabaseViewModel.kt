package com.example.tareamov.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tareamov.data.repository.SupabaseRepository
import kotlinx.coroutines.launch
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData

class SupabaseViewModel : ViewModel() {
    private val repository = SupabaseRepository()

    private val _loginResult = MutableLiveData<String?>()
    val loginResult: LiveData<String?> = _loginResult

    fun loginConEmail(email: String, password: String) {
        viewModelScope.launch {
            val token = repository.loginConEmail(email, password)
            _loginResult.postValue(token)
        }
    }
}
