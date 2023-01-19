package com.example.takemethere

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.takemethere.fragment.MapFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.id_fragment_container,
                MapFragment()
            )
            .commit()
    }
}