package com.lecoindeclem.signalementuniverselfrance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.journeyapps.barcodescanner.CompoundBarcodeView
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SignalementProduitActivity : AppCompatActivity() {

    private lateinit var barcodeScanner: CompoundBarcodeView
    private lateinit var formLayout: ScrollView
    private lateinit var productImage: ImageView
    private lateinit var productName: TextView
    private lateinit var productBarcode: TextView
    private lateinit var inputCategory: EditText
    private lateinit var inputDescription: EditText
    private lateinit var btnSubmit: Button
    private lateinit var loadingIndicator: ProgressBar

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                barcodeScanner.resume()
            } else {
                Toast.makeText(this, "Permission caméra requise pour scanner", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_signalement_produit)

        barcodeScanner = findViewById(R.id.barcode_scanner)
        formLayout = findViewById(R.id.form_layout)

        val initialPaddingLeft = formLayout.paddingLeft
        val initialPaddingTop = formLayout.paddingTop
        val initialPaddingRight = formLayout.paddingRight
        val initialPaddingBottom = formLayout.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Appliquer le padding sur le layout de base (main)
            // Sauf pour le barcode scanner pour qu'il prenne tout l'écran, on modifie donc formLayout
            formLayout.setPadding(
                initialPaddingLeft + systemBars.left,
                initialPaddingTop + systemBars.top,
                initialPaddingRight + systemBars.right,
                initialPaddingBottom + systemBars.bottom
            )

            // Pour le barcode scanner, sa configuration interne de CompoundBarcodeView
            // fait en sorte que le preview prend tout l'écran, ce qui est parfait.
            // S'il a une vue overlay (par ex ViewFinder), on pourrait la decaler
            val viewFinder = barcodeScanner.findViewById<View>(com.google.zxing.client.android.R.id.zxing_viewfinder_view)
            viewFinder?.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)

            insets
        }
        productImage = findViewById(R.id.product_image)
        productName = findViewById(R.id.product_name)
        productBarcode = findViewById(R.id.product_barcode)
        inputCategory = findViewById(R.id.input_category)
        inputDescription = findViewById(R.id.input_description)
        btnSubmit = findViewById(R.id.btn_submit)
        loadingIndicator = findViewById(R.id.loading_indicator)

        barcodeScanner.decodeContinuous { result ->
            if (result.text != null) {
                barcodeScanner.pause()
                fetchProductInfo(result.text)
            }
        }

        btnSubmit.setOnClickListener {
            // Simulate sending report
            Toast.makeText(this, "Signalement envoyé avec succès!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            barcodeScanner.resume()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        super.onPause()
        barcodeScanner.pause()
    }

    private fun fetchProductInfo(barcode: String) {
        runOnUiThread {
            loadingIndicator.visibility = View.VISIBLE
            barcodeScanner.visibility = View.GONE
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://world.openfoodfacts.org/api/v0/product/$barcode.json"
                val response = httpClient.get(url)
                val responseBody = response.bodyAsText()

                val json = Json { ignoreUnknownKeys = true }
                val productResponse = json.decodeFromString<OpenFoodFactsResponse>(responseBody)

                withContext(Dispatchers.Main) {
                    loadingIndicator.visibility = View.GONE
                    formLayout.visibility = View.VISIBLE
                    displayProductInfo(productResponse.product)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingIndicator.visibility = View.GONE
                    Toast.makeText(this@SignalementProduitActivity, "Erreur lors de la récupération du produit", Toast.LENGTH_SHORT).show()
                    barcodeScanner.visibility = View.VISIBLE
                    barcodeScanner.resume()
                }
            }
        }
    }

    private fun displayProductInfo(product: Product?) {
        if (product != null) {
            productName.text = product.product_name ?: "Produit inconnu"
            productBarcode.text = "Code barre: ${product.code ?: "N/A"}"

            product.image_url?.let {
                 productImage.load(it) {
                    crossfade(true)
                 }
            }
        } else {
            productName.text = "Produit non trouvé"
            productBarcode.text = ""
        }
    }
}

@Serializable
data class OpenFoodFactsResponse(
    val code: String? = null,
    val product: Product? = null,
    val status: Int? = null,
    val status_verbose: String? = null
)

@Serializable
data class Product(
    val code: String? = null,
    val product_name: String? = null,
    val image_url: String? = null,
    val brands: String? = null
)
