package com.example.takemethere.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.takemethere.R
import com.example.takemethere.utils.Tools
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.MapboxMap.OnMapClickListener
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class MapFragment :
    Fragment(),
    PermissionsListener,
    LocationEngineListener,
    OnMapReadyCallback,
    OnMapClickListener {


    // INITIALIZE VARS
    private lateinit var fragmentView: View
    private lateinit var idMapView: MapView
    private lateinit var idBtnStartNavigation: Button
    private lateinit var mapbox: MapboxMap
    private lateinit var permissionManager: PermissionsManager
    private var locationEngine: LocationEngine? = null
    private var locationLayerPlugin: LocationLayerPlugin? = null
    private lateinit var originLocation: Location
    private lateinit var originPosition: Point
    private lateinit var destinationPosition: Point
    private var destinationMarker: Marker? = null
    private var navigationMapRoute: NavigationMapRoute? = null
    private var currentRoute: DirectionsRoute? = null

    val REQUEST_CHECK_SETTINGS = 1
    var settingsClient: SettingsClient? = null
    var locationComponent: LocationComponent? = null

    private val getLocationResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {}
    // END INITIALIZE VARS===========================================================


    // FRAGMENT OVERRIDERS
    @SuppressWarnings("MissingPermission")
    override fun onStart() {
        super.onStart()
        idMapView.onStart()
        if (PermissionsManager.areLocationPermissionsGranted(requireContext())) {
            locationEngine?.requestLocationUpdates()
            locationLayerPlugin?.onStart()
        }

    }

    override fun onResume() {
        super.onResume()
        idMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        idMapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        idMapView.onStop()
        locationEngine?.removeLocationUpdates()
        locationLayerPlugin?.onStop()
        //mapbox.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        idMapView.onDestroy()
        locationEngine?.deactivate()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        idMapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        idMapView.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(requireContext(), requireContext().getString(R.string.access_token))
        if (!Tools.isLocationEnabled(requireContext())) {
            Tools.popUpWindow(requireContext(), "Your location is not enabled") {
                it.setPositiveButton("enable location") { _, _ ->
                    getLocationResult.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                it.setNegativeButton("cancel") { _, _ ->
                    requireActivity().finish()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragmentView = inflater.inflate(R.layout.fragment_map, container, false)

        initializeVars()

        executionBody(savedInstanceState)

        return fragmentView
    }


    // PRIVATE CLASS FUNCTIONS
    private fun executionBody(savedInstanceState: Bundle?) {
        idMapView.onCreate(savedInstanceState)
        idMapView.getMapAsync(this)
        idBtnStartNavigation.setOnClickListener {
            val navigationLauncherOptions = NavigationLauncherOptions.builder()
                .directionsRoute(currentRoute)
                .shouldSimulateRoute(true)
                .build()

            NavigationLauncher.startNavigation(requireActivity(), navigationLauncherOptions)

        }
    }

    private fun initializeVars() {
        idMapView = fragmentView.findViewById(R.id.id_mapview)
        idBtnStartNavigation = fragmentView.findViewById(R.id.id_btn_start_navigation)
        settingsClient = LocationServices.getSettingsClient(requireActivity())
    }

    private fun enableLocation() {
        if (PermissionsManager.areLocationPermissionsGranted(requireContext())) {
            initializeLocationComponent()
            initializeLocationEngine()
        } else {
            permissionManager = PermissionsManager(this)
            permissionManager.requestLocationPermissions(requireActivity())
        }
    }

    @SuppressWarnings("MissingPermission")
    fun initializeLocationEngine() {
        locationEngine =
            LocationEngineProvider(requireContext()).obtainBestLocationEngineAvailable()
        locationEngine?.priority = LocationEnginePriority.HIGH_ACCURACY
        locationEngine?.activate()
        locationEngine?.addLocationEngineListener(this)

        val lastLocation = locationEngine?.lastLocation
        if (lastLocation != null) {
            originLocation = lastLocation
            setCameraPosition(lastLocation)
        } else {
            locationEngine?.addLocationEngineListener(this)
        }

    }

    @SuppressWarnings("MissingPermission")
    fun initializeLocationComponent() {
        locationComponent = mapbox.locationComponent
        locationComponent?.activateLocationComponent(requireContext())
        locationComponent?.isLocationComponentEnabled = true
        locationComponent?.cameraMode = com.mapbox.mapboxsdk.location.modes.CameraMode.TRACKING

    }

    private fun setCameraPosition(location: Location) {
        mapbox.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(
                    location.latitude,
                    location.longitude
                ), 15.0
            )
        )

    }

    private fun getRoute(origin: Point, destination: Point) {
        NavigationRoute.builder(requireContext()).accessToken(Mapbox.getAccessToken()!!)
            .origin(origin).destination(destination).build()
            .getRoute(object : Callback<DirectionsResponse> {
                override fun onResponse(
                    call: Call<DirectionsResponse>,
                    response: Response<DirectionsResponse>
                ) {
                    if (navigationMapRoute != null) {
                        navigationMapRoute?.removeRoute()
                    } else {
                        navigationMapRoute = NavigationMapRoute(null, idMapView, mapbox)
                    }
                    currentRoute = response.body()?.routes()?.first()
                    if (currentRoute != null){
                        navigationMapRoute!!.addRoute(currentRoute)
                    }
                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    TODO("Not yet implemented")
                }

            })
    }

    @SuppressLint("MissingPermission")
    private fun checkLocation() {
        if (!originLocation.hasBearing()) {
            mapbox.locationComponent.lastKnownLocation?.run {
                originLocation = this
            }
        }
    }
    // END PRIVATE CLASS FUNCTIONS =============================================================

    // MAP OVERRIDES
    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Tools.showToast(requireContext(), "permission is needed for full functionality")
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            enableLocation()
        } else {
            Tools.showToast(requireContext(), "Permission was not granted")
            requireActivity().finish()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnected() {
        locationEngine?.requestLocationUpdates()
    }

    override fun onLocationChanged(location: Location?) {
        location?.run {
            originLocation = this
            setCameraPosition(this)
        }

    }

    override fun onMapReady(mapboxMap: MapboxMap?) {
        mapbox = mapboxMap ?: return
        mapbox.addOnMapClickListener(this)

//        mapbox.cameraPosition =
//            CameraPosition.Builder().target(LatLng(LATITUDE, LONGITUDE)).zoom(13.0).build()
////            mapbox.addImage("", Tools.drawableToBitmap(requireContext().getDrawable(R.drawable.circle)))
//        mapbox.updateMarker(
//            Marker(
//                MarkerOptions().icon(
//                    IconFactory.getInstance(requireContext()).fromBitmap(
//                        Tools.drawableToBitmap(
//                            requireContext().getDrawable(R.drawable.stop_circle)!!
//                        )
//                    )
//                )
//            )
//        )
//        mapbox.addMarker(
//            MarkerOptions().position(mapbox.cameraPosition.target).setIcon(
//                IconFactory.getInstance(requireContext()).fromBitmap(
//                    Tools.drawableToBitmap(
//                        requireContext().getDrawable(R.drawable.stop_circle)!!
//                    )
//                )
//            )
//        )

        val lRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(1000)
            .setMaxUpdateDelayMillis(100)
            .build()
        val locationRequestBuilder = LocationSettingsRequest.Builder().addLocationRequest(lRequest)
        val locationRequest = locationRequestBuilder.build()

        settingsClient?.checkLocationSettings(locationRequest)?.run {
            addOnSuccessListener {
                enableLocation()
            }

            addOnFailureListener {
                val statusCode = (it as ApiException).statusCode

                if (statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                    val resolvableException = it as? ResolvableApiException
                    resolvableException?.startResolutionForResult(
                        requireActivity(),
                        REQUEST_CHECK_SETTINGS
                    )
                }
            }
        }
        //enableLocation()
    }

    override fun onMapClick(point: LatLng) {
        if (mapbox.markers.isNotEmpty()) mapbox.clear()
        mapbox.addMarker(MarkerOptions().position(point))
        checkLocation()
        originLocation.run {
            val startPoint = Point.fromLngLat(longitude, latitude)
            val endPoint = Point.fromLngLat(point.longitude, point.latitude)
            getRoute(startPoint, endPoint)
        }
        idBtnStartNavigation.visibility = View.VISIBLE
    }
    // END MAP OVERRIDES ====================================================================

    companion object {
        private const val LATITUDE = -7.981084
        private const val LONGITUDE = 31.629473
    }
}


// END FRAGMENT OVERRIDERS=============================================================================================
//
//    // PRIVATE CLASS FUNCTIONS
//    private fun executionBody(savedInstanceState: Bundle?) {
//        val icon = BitmapFactory.decodeResource(
//            requireContext().resources,
//            R.drawable.circle
//        )
//        if (savedInstanceState != null) {
//            idMapView.onSaveInstanceState(savedInstanceState)
//        }
//
//        idMapView.getMapAsync(this)
//        idMapView.getMapAsync {
////            it.addMarker(MarkerOptions().position(LatLng(-7.981084, 31.629473)))
//            //it.cameraPosition = CameraPosition.Builder().target(LatLng(-7.981084, 31.629473)).build()
//        }
//
//        idBtnStartNavigation.setOnClickListener {
//            // navigation ui
//            val options = NavigationLauncherOptions.builder()
//                .directionsRoute(currentRoute)
//                .shouldSimulateRoute(true)
//                .build()
//            NavigationLauncher.startNavigation(requireActivity(), options)
//        }
//    }
//
//    private fun initializeVars() {
//        idMapView = fragmentView.findViewById(R.id.id_mapview)
//        idBtnStartNavigation = fragmentView.findViewById(R.id.id_btn_start_navigation)
//    }
//
//    private fun enableLocation() {
//        if (PermissionsManager.areLocationPermissionsGranted(requireContext())) {
//            initializeLocationEngine()
//            initializeLocationLayer()
//        } else {
//            permissionsManager = PermissionsManager(this)
//            permissionsManager.requestLocationPermissions(requireActivity())
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun initializeLocationEngine() {
//        locationEngine =
//            LocationEngineProvider(requireContext()).obtainBestLocationEngineAvailable()
//        locationEngine?.priority = LocationEnginePriority.HIGH_ACCURACY
//        locationEngine?.activate()
//        locationEngine?.addLocationEngineListener(this)
//
//        val lastLocation = locationEngine?.lastLocation
//        if (lastLocation != null) {
//            originLocation = lastLocation
//            setCameraPosition(lastLocation)
//        } else {
//            locationEngine?.addLocationEngineListener(this)
//        }
//    }
//
//    private fun setCameraPosition(lastLocation: Location) {
//        mapbox.animateCamera(
//            CameraUpdateFactory.newLatLngZoom(
//                LatLng(
//                    lastLocation.latitude,
//                    lastLocation.longitude
//                ), 20.0
//            )
//        )
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun initializeLocationLayer() {
//        locationLayerPlugin = LocationLayerPlugin(idMapView, mapbox, locationEngine)
//        locationLayerPlugin!!.setLocationLayerEnabled(true)
//        locationLayerPlugin!!.cameraMode = CameraMode.TRACKING
//        locationLayerPlugin!!.renderMode = RenderMode.NORMAL
//    }
//
//    private fun drawableToBitmap(drawable: Drawable): Bitmap {
//        var bitmap: Bitmap? = null
//        if (drawable is BitmapDrawable) {
//            if (drawable.bitmap != null) {
//                return drawable.bitmap
//            }
//        }
//        bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
//            Bitmap.createBitmap(
//                1,
//                1,
//                Bitmap.Config.ARGB_8888
//            ) // Single color bitmap will be created of 1x1 pixel
//        } else {
//            Bitmap.createBitmap(
//                drawable.intrinsicWidth,
//                drawable.intrinsicHeight,
//                Bitmap.Config.ARGB_8888
//            )
//        }
//        val canvas = Canvas(bitmap)
//        drawable.setBounds(0, 0, canvas.width, canvas.height)
//        drawable.draw(canvas)
//        return bitmap
//    }
//
//    private fun getRoute(origin: Point, destination:Point){
//        Mapbox.getAccessToken()?.let {
//            NavigationRoute.builder(requireContext())
//                .accessToken(it)
//                .origin(origin)
//                .destination(destination)
//                .build()
//                .getRoute(object : Callback<DirectionsResponse> {
//                    override fun onResponse(
//                        call: Call<DirectionsResponse>,
//                        response: Response<DirectionsResponse>
//                    ) {
//                        if (response.body()?.routes()?.isEmpty() == true) return // no route
//                        currentRoute = response.body()?.routes()?.first()
//                        if (navigationMapRoute != null) {
//                            navigationMapRoute!!.removeRoute()
//                        }else{
//                            navigationMapRoute = NavigationMapRoute(null, idMapView, mapbox)
//                        }
//                        navigationMapRoute?.addRoute(currentRoute)
//                    }
//
//                    override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
//                        // failed to route
//                    }
//
//                })
//        }
//    }
//
//    // END  PRIVATE CLASS FUNCTIONS===============================================================================
//
//    // MAP OVERRIDES
//    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
//        Toast.makeText(
//            requireContext(),
//            "This app needs location permission to be able to show your location on the map",
//            Toast.LENGTH_LONG
//        ).show()
//    }
//
//    override fun onPermissionResult(granted: Boolean) {
//        if (granted) {
//            enableLocation()
//        } else {
//            Toast.makeText(requireContext(), "User location was not granted", Toast.LENGTH_LONG)
//                .show()
//            requireActivity().finish()
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    override fun onConnected() {
//        locationEngine?.requestLocationUpdates()
//    }
//
//    override fun onLocationChanged(location: Location?) {
//        location?.run {
//            originLocation = this
//            setCameraPosition(this)
//        }
//    }
//
//    override fun onMapReady(mapboxMap: MapboxMap?) {
//        mapbox = mapboxMap ?: return
//        mapbox.addOnMapClickListener(this)
//        enableLocation()
//    }
//
//    override fun onMapClick(point: LatLng) {
//        if (destinationMarker != null) {
//            mapbox.removeMarker(destinationMarker!!)
//        }
//        destinationMarker = mapbox.addMarker(MarkerOptions().position(point))
//        destinationPosition = Point.fromLngLat(point.longitude, point.latitude)
//        originPosition = Point.fromLngLat(originLocation.longitude, originLocation.latitude)
//        getRoute(originPosition, destinationPosition)
//        idBtnStartNavigation.isEnabled = true
//        idBtnStartNavigation.setBackgroundColor(Color.DKGRAY)
//    }
//    // END MAP OVERRIDES========================================================================
//}