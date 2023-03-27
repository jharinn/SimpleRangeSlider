package com.example.rangeslider.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.rangeslider.databinding.ActivityMainBinding
import com.example.rangeslider.ui.customview.SimpleRangeSlider

class MainActivity : AppCompatActivity(), SimpleRangeSlider.OnValueChangeListener {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.rangeSlider.setOnRangeSeekBarChangeListener(this)
    }

    override fun onValueChanged(bar: SimpleRangeSlider, minValue: Float, maxValue: Float) {
        binding.tvValue.text = "${minValue.toInt()} - ${maxValue.toInt()}"
    }
}