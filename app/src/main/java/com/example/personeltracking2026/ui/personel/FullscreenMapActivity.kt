package com.example.personeltracking2026.ui.personel

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.map.MapTypeManager
import com.example.personeltracking2026.databinding.ActivityFullscreenMapBinding
import com.example.personeltracking2026.utils.drawableToBitmap
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

class FullscreenMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var binding: ActivityFullscreenMapBinding
    private var mapLibreMap: org.maplibre.android.maps.MapLibreMap? = null
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var currentMapType = MapTypeManager.MapType.STANDARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        binding = ActivityFullscreenMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)

        binding.btnZoomIn.setOnClickListener {
            mapLibreMap?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.zoomIn()
            )
        }

        binding.btnZoomOut.setOnClickListener {
            mapLibreMap?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.zoomOut()
            )
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnCenter.setOnClickListener {
            if (currentLat == 0.0 && currentLon == 0.0) return@setOnClickListener

            mapLibreMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(currentLat, currentLon),
                    18.0
                )
            )
        }

        currentLat = intent.getDoubleExtra("lat", 0.0)
        currentLon = intent.getDoubleExtra("lon", 0.0)

        val savedType = getSharedPreferences("map_settings", MODE_PRIVATE)
            .getString("map_type", MapTypeManager.MapType.STANDARD.name)

        currentMapType = MapTypeManager.MapType.valueOf(savedType!!)

        val styleUrl = MapTypeManager.getStyleUrl(currentMapType)

        binding.btnMapType.setOnClickListener {
            showMapTypeMenu()
        }

        mapView.getMapAsync { map ->
            mapLibreMap = map

            map.setStyle(
                Style.Builder().fromUri(styleUrl)
            ) {

                val point = LatLng(currentLat, currentLon)

                map.cameraPosition = CameraPosition.Builder()
                    .target(point)
                    .zoom(12.0)
                    .build()

                addMarker(currentLat, currentLon)
            }
        }
    }

    private fun addMarker(lat: Double, lon: Double) {
        val map = mapLibreMap ?: return

        val geoJsonSource = GeoJsonSource(
            "marker-source",
            Point.fromLngLat(lon, lat)
        )

        map.style?.apply {

            getLayer("marker-layer")?.let { removeLayer(it) }
            getSource("marker-source")?.let { removeSource(it) }

            addSource(geoJsonSource)

            val symbolLayer = SymbolLayer(
                "marker-layer",
                "marker-source"
            ).withProperties(
                iconImage("marker-icon"),
                iconAnchor("bottom"),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )

            addLayer(symbolLayer)

            if (getImage("marker-icon") == null) {
                val drawable = ContextCompat.getDrawable(this@FullscreenMapActivity, R.drawable.ic_location_pin)
                    ?: return

                drawable.setTint(Color.rgb(255, 82, 82))

                val bitmap = drawableToBitmap(drawable)
                addImage(
                    "marker-icon",
                    bitmap
                )
            }
        }

//        map.animateCamera(
//            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 18.0)
//        )
    }

    private fun showMapTypeMenu() {
        val wrapper = android.view.ContextThemeWrapper(this, R.style.DarkPopupMenu)
        val popup = android.widget.PopupMenu(wrapper, binding.btnMapType)

        MapTypeManager.MapType.values().forEach {
            popup.menu.add(it.label)
        }

        popup.setOnMenuItemClickListener { item ->
            MapTypeManager.MapType.values()
                .firstOrNull { it.label == item.title.toString() }
                ?.let {
                    currentMapType = it
                    applyMapType(it)

                    getSharedPreferences("map_settings", MODE_PRIVATE).edit {
                        putString("map_type", it.name)
                    }
                }
            true
        }

        popup.show()
    }

    private fun applyMapType(type: MapTypeManager.MapType) {
        val map = mapLibreMap ?: return

        // SIMPAN posisi kamera sekarang
        val currentCamera = map.cameraPosition

        val styleUrl = MapTypeManager.getStyleUrl(type)

        map.setStyle(
            Style.Builder().fromUri(styleUrl)
        ) {
            // RESTORE kamera
            map.cameraPosition = currentCamera

            // Tambahin marker lagi
            addMarker(currentLat, currentLon)
        }
    }

    // Lifecycle
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume()
        mapView.onResume()

        val savedType = getSharedPreferences("map_settings", MODE_PRIVATE)
            .getString("map_type", MapTypeManager.MapType.STANDARD.name)

        val newType = MapTypeManager.MapType.valueOf(savedType!!)

        if (newType != currentMapType) {
            currentMapType = newType
            applyMapType(newType)
        }
    }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}