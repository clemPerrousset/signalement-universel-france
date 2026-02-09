package com.lecoindeclem.signalementuniverselfrance

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.OutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), PaymentManager.Listener {

    private lateinit var descEdit: TextInputEditText
    private lateinit var photoButton: MaterialButton
    private lateinit var photoPreview: ImageView
    private lateinit var addressEdit: TextInputEditText
    private lateinit var mairieInfo: MaterialTextView
    private lateinit var mapView: MapView
    private lateinit var sendButton: MaterialButton

    private val mairieViewModel: MairieViewModel by viewModels()

    private var photoUri: Uri? = null
    private var lastLocation: Location? = null
    private var currentAddress: String? = null
    private var currentMarker: Marker? = null

    private lateinit var paymentManager: PaymentManager
    private lateinit var prefs: SharedPreferences

    private var introAccepted = false
    private var cachedPrice: String? = null

    private val reqLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) getLocationAndDisplay()
        else Toast.makeText(this, "Permission localisation refusée", Toast.LENGTH_SHORT).show()
    }

    private val reqCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCameraPreview()
        else Toast.makeText(this, "Permission caméra refusée", Toast.LENGTH_SHORT).show()
    }

    private val reqReadImagesPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openGallery()
        else Toast.makeText(this, "Permission médias refusée", Toast.LENGTH_SHORT).show()
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res: ActivityResult ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.data
            if (uri != null) {
                photoUri = uri
                photoPreview.visibility = View.VISIBLE
                photoPreview.setImageURI(uri)
                photoPreview.setOnClickListener { askReplacePhoto() }
            }
        }
    }

    private val takePicturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            val uri = saveBitmapToMediaStore(bmp)
            if (uri != null) {
                photoUri = uri
                photoPreview.visibility = View.VISIBLE
                photoPreview.setImageURI(uri)
                photoPreview.setOnClickListener { askReplacePhoto() }
            } else {
                Toast.makeText(this, "Impossible d'enregistrer la photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        descEdit = findViewById(R.id.descEdit)
        photoButton = findViewById(R.id.photoButton)
        photoPreview = findViewById(R.id.photoPreview)
        addressEdit = findViewById(R.id.addressEdit)
        mairieInfo = findViewById(R.id.mairieInfo)
        mapView = findViewById(R.id.map)
        sendButton = findViewById(R.id.sendButton)

        paymentManager = PaymentManager(this)
        paymentManager.setListener(this)
        paymentManager.start()

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        centerFrance()
        enableMapTapSelection()

        photoButton.setOnClickListener { showPhotoChooser() }

        addressEdit.setOnClickListener {
            if (!introAccepted) {
                showIntroIfNeeded(force = true)
                return@setOnClickListener
            }
            ensureLocationPermissionThenFetch()
        }

        sendButton.setOnClickListener {
            if (!introAccepted) {
                showIntroIfNeeded(force = true)
                return@setOnClickListener
            }
            if (paymentManager.canSendToday()) {
                openContactChooser()
            } else {
                showPaywall()
            }
        }

        lifecycleScope.launch { mairieViewModel.mairieLabel.collect { updateMairieUI() } }
        lifecycleScope.launch { mairieViewModel.mairieEmail.collect { updateMairieUI() } }
        lifecycleScope.launch { mairieViewModel.mairieAdresse.collect { updateMairieUI() } }

        showIntroIfNeeded(force = false)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        paymentManager.restorePurchases()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        paymentManager.stop()
    }

    private fun centerFrance() {
        mapView.controller.setZoom(5.5)
        mapView.controller.setCenter(GeoPoint(46.6, 2.2))
    }

    private fun enableMapTapSelection() {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) onGeoPointSelected(p)
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) onGeoPointSelected(p)
                return true
            }
        }
        val overlay = MapEventsOverlay(receiver)
        mapView.overlays.add(0, overlay)
    }

    private fun onGeoPointSelected(geo: GeoPoint) {
        if (currentMarker == null) {
            currentMarker = Marker(mapView).apply { title = "Position du signalement" }
            mapView.overlays.add(currentMarker)
        }
        currentMarker!!.position = geo
        mapView.controller.setZoom(17.0)
        mapView.controller.setCenter(geo)
        mapView.invalidate()

        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addr = geocoder.getFromLocation(geo.latitude, geo.longitude, 1)?.firstOrNull()
            val line = addr?.getAddressLine(0).orEmpty()
            currentAddress = line.ifBlank { "%.6f, %.6f".format(Locale.US, geo.latitude, geo.longitude) }
            addressEdit.setText(currentAddress)
        } catch (_: Exception) {
            currentAddress = "%.6f, %.6f".format(Locale.US, geo.latitude, geo.longitude)
            addressEdit.setText(currentAddress)
        }

        mairieViewModel.fetchMairieFromLatLon(geo.latitude, geo.longitude)
    }

    private fun showPhotoChooser() {
        val items = arrayOf("Prendre une photo", "Choisir depuis la galerie")
        AlertDialog.Builder(this)
            .setTitle("Photo du signalement")
            .setItems(items) { _: DialogInterface, which: Int ->
                when (which) {
                    0 -> ensureCameraPermissionThenOpen()
                    1 -> ensureReadImagesPermissionThenOpen()
                }
            }
            .show()
    }

    private fun ensureCameraPermissionThenOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) openCameraPreview() else reqCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun ensureReadImagesPermissionThenOpen() {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            openGallery()
        else reqReadImagesPermission.launch(perm)
    }

    private fun openCameraPreview() {
        takePicturePreview.launch(null)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun askReplacePhoto() {
        AlertDialog.Builder(this)
            .setTitle("Modifier la photo ?")
            .setMessage("Souhaitez-vous remplacer la photo actuelle ?")
            .setPositiveButton("Oui") { _, _ -> showPhotoChooser() }
            .setNegativeButton("Non", null)
            .show()
    }

    private fun saveBitmapToMediaStore(bmp: Bitmap): Uri? {
        val resolver = contentResolver
        val filename = "signalement_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Signalements")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out: OutputStream ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun ensureLocationPermissionThenFetch() {
        val needFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        val needCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        if (needFine && needCoarse) {
            reqLocationPermissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else getLocationAndDisplay()
    }

    private fun getLocationAndDisplay() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val provider = LocationManager.NETWORK_PROVIDER
        try {
            lm.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onLocationReady(location)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }, null)
        } catch (_: SecurityException) {
        }
    }

    private fun onLocationReady(loc: Location) {
        lastLocation = loc
        val geo = GeoPoint(loc.latitude, loc.longitude)
        onGeoPointSelected(geo)
    }

    private fun updateMairieUI() {
        val label = mairieViewModel.mairieLabel.value
        val email = mairieViewModel.mairieEmail.value
        val addr = mairieViewModel.mairieAdresse.value
        mairieInfo.text = buildString {
            append("Mairie la plus proche : ")
            append(label ?: "inconnue")
            if (!email.isNullOrBlank()) append("\nEmail : $email")
            if (!addr.isNullOrBlank()) append("\nAdresse : $addr")
        }
    }

    private fun openContactChooser() {
        val items = arrayOf("Envoyer un email", "Appeler (saisir un numéro)")
        AlertDialog.Builder(this)
            .setTitle("Contacter")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> prepareEmailAndCount()
                    1 -> promptAndDialAndCount()
                }
            }
            .show()
    }

    private fun prepareEmailAndCount() {
        if (!prepareEmail()) return
        paymentManager.markSentToday()
    }

    private fun promptAndDialAndCount() {
        val input = TextInputEditText(this).apply {
            hint = "Numéro de téléphone (ex: 03 80 ...)"
        }
        AlertDialog.Builder(this)
            .setTitle("Composer un numéro")
            .setView(input)
            .setPositiveButton("Appeler") { _, _ ->
                val raw = input.text?.toString().orEmpty()
                val tel = raw.replace(Regex("[^+0-9]"), "")
                if (tel.isBlank()) {
                    Toast.makeText(this, "Numéro invalide", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))
                    try {
                        startActivity(intent)
                        paymentManager.markSentToday()
                    } catch (_: Exception) {
                        Toast.makeText(this, "Aucune application téléphone disponible", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun prepareEmail(): Boolean {
        val description = descEdit.text?.toString()?.trim().orEmpty()
        if (description.isBlank()) {
            descEdit.error = "Veuillez décrire le problème"
            descEdit.requestFocus()
            return false
        }

        val subject = "Signalement à ma mairie"
        val body = buildString {
            append("Bonjour,\n\nVoici un signalement :\n\n")
            append(description).append("\n\n")
            append("Adresse : ").append(currentAddress ?: "Non renseignée").append("\n")
            mairieViewModel.mairieLabel.value?.let { append("Destinataire suggéré : $it\n") }
            lastLocation?.let {
                append("Coordonnées : %.6f, %.6f\n".format(Locale.US, it.latitude, it.longitude))
            } ?: run {
                currentMarker?.position?.let {
                    append("Coordonnées : %.6f, %.6f\n".format(Locale.US, it.latitude, it.longitude))
                }
            }
            append("\nCordialement")
            append("\n\nGénéré par l'application Signalement Universel France")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (photoUri != null) "image/jpeg" else "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            mairieViewModel.mairieEmail.value?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }

            photoUri?.let {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_STREAM, it)
            }
        }

        return try {
            startActivity(Intent.createChooser(intent, "Envoyer via…"))
            true
        } catch (_: Exception) {
            Toast.makeText(this, "Aucune application email disponible", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun showPaywall() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_paywall, null, false)
        val priceTv = v.findViewById<MaterialTextView>(R.id.paywallPrice)
        val buyBtn = v.findViewById<MaterialButton>(R.id.paywallBuy)
        val laterBtn = v.findViewById<MaterialButton>(R.id.paywallLater)

        val priceText = cachedPrice ?: paymentManager.getFormattedPriceOrNull()
        priceTv.text = priceText?.let { "Déblocage à vie : $it" } ?: "Déblocage à vie : 9,99 €"

        val dialog = AlertDialog.Builder(this)
            .setView(v)
            .create()

        buyBtn.setOnClickListener {
            dialog.dismiss()
            paymentManager.launchLifetimePurchase(this)
        }
        laterBtn.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showIntroIfNeeded(force: Boolean) {
        val hide = prefs.getBoolean(KEY_HIDE_INTRO, false)
        if (!force && hide) {
            introAccepted = true
            return
        }
        if (!force && introAccepted) return

        val v = LayoutInflater.from(this).inflate(R.layout.dialog_intro, null, false)
        val cb = v.findViewById<CheckBox>(R.id.introDontShowAgain)
        val ok = v.findViewById<MaterialButton>(R.id.introOk)

        val dialog = AlertDialog.Builder(this)
            .setCancelable(false)
            .setView(v)
            .create()

        ok.setOnClickListener {
            introAccepted = true
            if (cb.isChecked) prefs.edit().putBoolean(KEY_HIDE_INTRO, true).apply()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onPremiumStateChanged(isPremium: Boolean) {
        if (isPremium) {
            Toast.makeText(this, "Signalements illimités activés 🎉", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPurchaseFlowError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onPriceLoaded(price: String?) {
        cachedPrice = price
    }

    companion object {
        private const val PREFS_NAME = "suf_ui_prefs"
        private const val KEY_HIDE_INTRO = "suf_hide_intro"
    }
}
