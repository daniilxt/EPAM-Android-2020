package com.university.epam_android_2020

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.university.epam_android_2020.firebaseDB.FirebaseDB
import com.university.epam_android_2020.services.ForegroundService
import com.university.epam_android_2020.user_data.CurrentGroup
import com.university.epam_android_2020.user_data.Group
import com.university.epam_android_2020.viewmodels.MainActivityViewModel
import kotlinx.android.synthetic.main.activity_main.*

const val EXTRA_USER_MAP = "EXTRA_USER_MAP"

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap

    //private lateinit var map: MapOfUsers
    private lateinit var mainActivityViewModel: MainActivityViewModel

    private var mFirebaseDB = FirebaseDB()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainActivityViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)

        mainActivityViewModel.init()

        checkPermissions()

        /*if (isFirstStart) {
            isFirstStart = false
            val intent = Intent(this, GroupActivity::class.java)
            startActivity(intent)
        } else {*/
        val mapNew = intent.getParcelableExtra(EXTRA_USER_MAP) as Group

        mainActivityViewModel.setGroup(mapNew)

        supportActionBar?.title = mapNew.name

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        menuButton.setOnClickListener {
            val intent = Intent(this, GroupActivity::class.java)
            startActivity(intent)
        }
        //}

/*        Thread {
*//*            while (true) {
                Thread.sleep(2000)
                println("thread!!")
                mainActivityViewModel.updateGroup()

            }*//*
        }.start()*/
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), 100
            )
        } else {
            println("starting service!!!")
            startService()
            //mFirebaseDB.listenChange { mainActivityViewModel.listenChange(it)
            mFirebaseDB.getListUsersFromGroup(CurrentGroup.instance.groupName) {
                mFirebaseDB.listenChange(it) { mainActivityViewModel.listenChange(it)
            }
            }
            // mainActivityViewModel.listenChange()

        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        println("request code = ${requestCode}, ${grantResults[0]}, ${permissions[0]},${permissions[1]}")
        if (requestCode == 100 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkPermissions()
        } else {
            val toast: Toast = Toast.makeText(
                applicationContext,
                "No no no no permissions! ${grantResults[0]}",
                Toast.LENGTH_LONG
            )
            toast.show()
            val intent = Intent(this, FailedPermissionsActivity::class.java)
            startActivity(intent)

        }
    }

    fun startService() {
        val serviceIntent = Intent(this, ForegroundService::class.java)
        serviceIntent.putExtra("inputExtra", "GPS usage in foreground")
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mainActivityViewModel.getGroup().observe(this, Observer {
            println("observing")
            updateMap()
        }) //TODO move it elsewhere

        updateMap()

        //mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 1000, 1000, 0))
    }

    private fun updateMap() {
        mMap.clear()
        println("updating map")
        val boundsBuilder = LatLngBounds.Builder()
        val map: Group? = mainActivityViewModel.getGroup().value
        val mFirebaseDB = FirebaseDB()
        if (map != null) {
            for (place in map.members) {
                if (place != null) {
                    val latLng = LatLng(place.gps.latitude, place.gps.longitude)
                    boundsBuilder.include(latLng)
                    getMarkerIcon(this, place.photo!!) { task1 ->
                        val options = MarkerOptions()
                            .position(latLng)
                            .title(place.name)
                            .icon(task1)
                        mMap.addMarker(options)
                    }
                }
            }
        }
    }

    private fun getMarkerIcon(context: Context, url: String, listener: (BitmapDescriptor) -> Unit) {
        val marker: LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val markerView: View = marker.inflate(R.layout.map_marker, null)
        val markerPhoto: ImageView = markerView.findViewById(R.id.map_photo)
        Glide.with(context)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {

                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    markerPhoto.setImageBitmap(resource)
                    listener.invoke(BitmapDescriptorFactory.fromBitmap(getBitmapFromView(markerView)))
                }

                override fun onLoadCleared(placeholder: Drawable?) {

                }
            })
    }

    private fun getBitmapFromView(view: View): Bitmap {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.draw(canvas)
        return bitmap
    }
}
